package com.github.nfzsh.intellijrancherplugin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.nfzsh.intellijrancherplugin.settings.Settings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import okhttp3.*
import okio.ByteString
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 *
 * @author 祝世豪
 * @since 2024/12/24 17:16
 */
@Service(Service.Level.PROJECT)
class RancherInfoService(private val project: Project) {

    /**
     * 集群信息
     * @return cluster, project, namespace
     */
    private val basicInfo: MutableList<Triple<String, String, String>>

    init {
        basicInfo = getInfo()
    }

    private fun getInfo(): MutableList<Triple<String, String, String>> {
        val list: Any? = getData("/v3/projects")
        val info: MutableList<Triple<String, String, String>> = mutableListOf()
        if (list is List<*>) {
            list.forEach { item ->
                if (item is Map<*, *>) {
                    val projectId = item["id"]
                    if (projectId is String) {
                        val cluster = projectId.split(":")[0]
                        val namespaces = getNameSpace(cluster, projectId)
                        namespaces.forEach { info.add(Triple(cluster, projectId, it)) }
                    }
                }
            }
        }
        return info
    }

    fun getDeployments(): MutableList<String> {
        if (basicInfo.isEmpty()) {
            return mutableListOf()
        }
        val info = basicInfo[0]
        val list: Any? = getData("/v3/project/${info.second}/deployments")
        val nameList = mutableListOf<String>()
        if (list is List<*>) {
            list.forEach { item ->
                if (item is Map<*, *>) {
                    val varList = item["containers"]
                    if (varList is List<*>) {
                        varList.forEach { varItem ->
                            if (varItem is Map<*, *>) {
                                nameList.add(varItem["name"].toString())
                            }
                        }
                    }
                }
            }
        }
        return nameList
    }

    fun redeploy(deploymentName: String): Boolean {
        val setting = getSetting()
        val basicInfo = basicInfo.first()
        val client = createUnsafeOkHttpClient()
        val request = Request.Builder()
            .url("https://${setting.first}v3/project/${basicInfo.second}/workloads/deployment:${basicInfo.third}:${deploymentName}?action=redeploy")
            .header("Authorization", setting.second)
            .post(RequestBody.create(null, ""))
            .build()
        val response = client.newCall(request).execute()
        return response.code == 200
    }

    fun getLogs(): WebSocket {
        val client = createUnsafeOkHttpClient()
        val setting = getSetting()
        val basicInfo = basicInfo.first()
        val podNames = getPodNames()
        if (podNames.isEmpty()) {
            throw IllegalArgumentException("No pods found")
        }
        val request = Request.Builder()
            .url("wss://${setting.first}/k8s/clusters/${basicInfo.first}/api/v1/namespaces/${basicInfo.third}/pods/${podNames[0]}/log?previous=false&follow=true&timestamps=false&pretty=true&container=${project.name}&sinceSeconds=100&sockId=5")
            .header("Authorization", setting.second)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
//                println("WebSocket opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
//                println("Received message: $text")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                project.getService(LogService::class.java).log(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
//                println("Closing WebSocket: $reason")
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("Error: ${t.message}")

            }
        }
        val webSocket = client.newWebSocket(request, listener)
        return webSocket
    }

    fun createWebSocketTtyConnector(
        terminalWidget: JBTerminalWidget,
        contentManager: ContentManager,
        content: Content
    ): TtyConnector {
        var isConnected = false
        val setting = getSetting()
        val basicInfo = basicInfo.first()
        val podNames = getPodNames()
        val webSocketUrl =
            "wss://${setting.first}/k8s/clusters/${basicInfo.first}/api/v1/namespaces/${basicInfo.third}/pods/${podNames[0]}/exec?container=sds-common-center&stdout=1&stdin=1&stderr=1&tty=1&command=%2Fbin%2Fsh&command=-c&command=TERM%3Dxterm-256color%3B%20export%20TERM%3B%20%5B%20-x%20%2Fbin%2Fbash%20%5D%20%26%26%20(%5B%20-x%20%2Fusr%2Fbin%2Fscript%20%5D%20%26%26%20%2Fusr%2Fbin%2Fscript%20-q%20-c%20%22%2Fbin%2Fbash%22%20%2Fdev%2Fnull%20%7C%7C%20exec%20%2Fbin%2Fbash)%20%7C%7C%20exec%20%2Fbin%2Fsh"
        val client = createUnsafeOkHttpClient()
        val request = Request.Builder()
            .url(webSocketUrl)
            .header("Authorization", setting.second)
            .header("sec-websocket-protocol", "base64.channel.k8s.io")
            .build()
        // 使用管道流模拟终端输入输出
        val inputPipe = PipedInputStream()
        val outputPipe = PipedOutputStream(inputPipe)

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.isNotEmpty()) {
                    val channel = text[0]
                    val base64Data = text.substring(1)
                    val decodedBytes = Base64.getDecoder().decode(base64Data)
                    val decodedString = String(decodedBytes, Charsets.UTF_8)

                    when (channel) {
                        '1' -> outputPipe.write(decodedString.toByteArray()) // stdout
                        '2' -> outputPipe.write(("Error: $decodedString\n").toByteArray()) // stderr
                        '3' -> outputPipe.write(("Error message: $decodedString\n").toByteArray()) // error messages
                        else -> outputPipe.write(("Unknown channel $channel: $decodedString\n").toByteArray())
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                ApplicationManager.getApplication().invokeLater {
                    terminalWidget.close()
                    contentManager.removeContent(content, true) // true 表示销毁资源
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                outputPipe.write(("Error: ${t.message}\n").toByteArray())
                isConnected = false
                terminalWidget.close()
            }
        })

        return object : TtyConnector {
            override fun read(buf: CharArray, offset: Int, length: Int): Int {
                val byteBuffer = ByteArray(length)
                val bytesRead = inputPipe.read(byteBuffer, 0, length)
                if (bytesRead > 0) {
                    val chars = String(byteBuffer, 0, bytesRead).toCharArray()
                    System.arraycopy(chars, 0, buf, offset, chars.size)
                }
                return bytesRead
            }

            override fun resize(terminalSize: TermSize) {
                // 远程服务器需要调整终端大小，可以在这里发送相关命令
                // {"Width":163,"Height":25}
                val resizeMessage = "{\"Width\":${terminalSize.columns},\"Height\":${terminalSize.rows}}"
                val base64Data = Base64.getEncoder().encodeToString(resizeMessage.toByteArray())
                val message = "4$base64Data" // '4' 表示 resize 事件通道
                webSocket.send(message)
            }

            override fun write(bytes: ByteArray?) {
                val base64Data = Base64.getEncoder().encodeToString(bytes)
                val message = "0$base64Data" // '0' 表示 stdin 通道
                webSocket.send(message)
            }

            override fun write(string: String) {
                val base64Data = Base64.getEncoder().encodeToString(string.toByteArray())
                val message = "0$base64Data" // '0' 表示 stdin 通道
                webSocket.send(message)
            }

            override fun close() {
                webSocket.close(1000, "Client closed")
            }

            override fun isConnected(): Boolean = isConnected

            override fun waitFor(): Int {
                // 如果不需要等待，直接返回 0 表示正常退出
                return 0
            }

            override fun ready(): Boolean {
                return isConnected && inputPipe.available() > 0 // 判断输入流中是否有数据
            }

            override fun getName(): String {
                // 返回连接的名称，例如 "WebSocket Shell"
                return "WebSocket Shell"
            }
        }
    }

    private fun getNameSpace(cluster: String, projectId: String): MutableList<String> {
        val list: Any? = getData("/v3/clusters/$cluster/namespaces")
        val namespaces = mutableListOf<String>()
        if (list is List<*>) {
            list.forEach { item ->
                if (item is Map<*, *>) {
                    val namespace = item["id"]
                    if (namespace is String && !namespaces.contains(namespace) && projectId == item["projectId"]) {
                        namespaces.add(namespace)
                    }
                }
            }
        }
        return namespaces
    }

    private fun getPodNames(): MutableList<String> {
        val basicInfo = basicInfo.first()
        val list: Any? = getData("/v3/project/${basicInfo.second}/pods")
        val pods = mutableListOf<String>()
        if (list is List<*>) {
            list.forEach { item ->
                if (item is Map<*, *>) {
                    val workloadId = item["workloadId"]
                    if (workloadId == "deployment:${basicInfo.third}:${project.name}" && item["type"] == "pod" && item["state"] == "running") {
                        pods.add(item["name"].toString())
                    }
                }
            }
        }
        return pods
    }

    private fun getSetting(): Pair<String, String> {
        val settings = Settings(project)
        val url: String
        if (settings.rancherHost.isNotEmpty()) {
            url = settings.rancherHost
        } else {
            throw IllegalArgumentException("Rancher host is not set")
        }
        val key: String
        if (settings.rancherApiKey.isNotEmpty()) {
            key = "Bearer " + settings.rancherApiKey
        } else {
            throw IllegalArgumentException("Rancher api key is not set")
        }
        return Pair(url, key)
    }

    private fun getData(url: String): Any? {
        val setting = getSetting()
        val client = createUnsafeOkHttpClient()
        val request = Request.Builder()
            .url("https://${setting.first}$url")
            .header("Authorization", setting.second)
            .get()
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            println("Error: ${e.message}")
            throw IllegalArgumentException("Error: ${e.message}")
        }
        val res = response.body?.string()
        if (res == null) {
            throw IllegalArgumentException("Data is null")
        }
        val mapper = getMapper()
        val data = mapper.readValue(res, Map::class.java)
        return data["data"]
    }

    private fun createUnsafeOkHttpClient(): OkHttpClient {
        val trustAllCertificates = object : X509TrustManager {
            @Throws(java.security.cert.CertificateException::class)
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
            }

            @Throws(java.security.cert.CertificateException::class)
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext: SSLContext = try {
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustAllCertificates), java.security.SecureRandom())
            }
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Unable to create SSLContext", e)
        } catch (e: KeyManagementException) {
            throw RuntimeException("Unable to initialize SSLContext", e)
        }

        val sslSocketFactory = sslContext.socketFactory

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCertificates)
            .hostnameVerifier { _, _ -> true }  // 信任所有主机
            .build()

    }

    private fun getMapper(): ObjectMapper {
        return ObjectMapper()
    }
}