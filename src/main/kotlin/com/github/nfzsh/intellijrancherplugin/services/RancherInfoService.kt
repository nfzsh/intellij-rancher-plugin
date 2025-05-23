package com.github.nfzsh.intellijrancherplugin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.nfzsh.intellijrancherplugin.listeners.ConfigChangeListener
import com.github.nfzsh.intellijrancherplugin.listeners.ConfigChangeNotifier
import com.github.nfzsh.intellijrancherplugin.settings.Settings
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.util.Alarm
import com.jediterm.terminal.*
import java.time.LocalDateTime
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import java.awt.Dimension
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZoneId
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 *
 * @author 祝世豪
 * @since 2024/12/24 17:16
 */
@Service(Service.Level.PROJECT)
class RancherInfoService(private val project: Project) : Disposable {

    /**
     * 集群信息
     * @return cluster, project, namespace
     */
    var basicInfo: MutableList<Triple<String, String, String>> = getInfo()
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    init {
        // 订阅配置更改事件
        val connection = project.messageBus.connect()
        connection.subscribe(ConfigChangeNotifier.topic, object : ConfigChangeListener {
            override fun onConfigChanged() {
                basicInfo = getInfo()
            }
        })
        startAutoRefresh()
    }

    private fun startAutoRefresh(intervalMillis: Long = 60_000L) {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({
            getInfo()
            startAutoRefresh(intervalMillis)
        }, intervalMillis)
    }

    fun getInfo(): MutableList<Triple<String, String, String>> {
        val list: Any? = getData("/v3/projects")
        val info: MutableList<Triple<String, String, String>> = mutableListOf()
        try {
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
        } catch (e: Exception) {
            thisLogger().warn("获取集群信息失败", e)
        }
        basicInfo = info
        return info
    }

    fun getDeployments(project: String): MutableList<String> {
        val list: Any? = getData("/v3/project/${project}/deployments")
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

    fun redeploy(deploymentName: String, basicInfo: Triple<String, String, String>): Boolean {
        val setting = getSetting()
        val client = createUnsafeOkHttpClient()
        val request = Request.Builder()
            .url("https://${setting.first}v3/project/${basicInfo.second}/workloads/deployment:${basicInfo.third}:${deploymentName}?action=redeploy")
            .header("Authorization", setting.second)
            .post("".toRequestBody(null))
            .build()
        val response = client.newCall(request).execute()
        return response.code == 200
    }

    fun getLogs(
        basicInfo: Triple<String, String, String>,
        deploymentName: String,
        podName: String,
        consoleView: ConsoleView
    ): WebSocket {
        val client = createUnsafeOkHttpClient()
        val setting = getSetting()
        val request = Request.Builder()
            .url("wss://${setting.first}k8s/clusters/${basicInfo.first}/api/v1/namespaces/${basicInfo.third}/pods/${podName}/log?previous=false&follow=true&timestamps=false&pretty=true&container=${deploymentName}&sinceSeconds=100&sockId=5")
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
//                project.getService(LogService::class.java).log(bytes.utf8())
                consoleView.print(bytes.utf8(), ConsoleViewContentType.NORMAL_OUTPUT)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
//                println("Closing WebSocket: $reason")
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                consoleView.print("Error: ${t.message}", ConsoleViewContentType.NORMAL_OUTPUT)

            }
        }
        val webSocket = client.newWebSocket(request, listener)
        return webSocket
    }

    fun createWebSocketTtyConnector(
        terminalWidget: JBTerminalWidget?,
        contentManager: ContentManager,
        content: Content?,
        basicInfo: Triple<String, String, String>,
        deploymentName: String,
        podName: String
    ): TtyConnector {
        var isConnected = false
        val setting = getSetting()
        val webSocketUrl =
            "wss://${setting.first}k8s/clusters/${basicInfo.first}/api/v1/namespaces/${basicInfo.third}/pods/${podName}/exec?container=${deploymentName}&stdout=1&stdin=1&stderr=1&tty=1&command=%2Fbin%2Fsh&command=-c&command=TERM%3Dxterm-256color%3B%20export%20TERM%3B%20%5B%20-x%20%2Fbin%2Fbash%20%5D%20%26%26%20(%5B%20-x%20%2Fusr%2Fbin%2Fscript%20%5D%20%26%26%20%2Fusr%2Fbin%2Fscript%20-q%20-c%20%22%2Fbin%2Fbash%22%20%2Fdev%2Fnull%20%7C%7C%20exec%20%2Fbin%2Fbash)%20%7C%7C%20exec%20%2Fbin%2Fsh"
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

                    when (channel) {
                        '1' -> outputPipe.write(decodedBytes) // stdout
                        '2' -> outputPipe.write(("Error: $decodedBytes\n").toByteArray()) // stderr
                        '3' -> outputPipe.write(("Error message: $decodedBytes\n").toByteArray()) // error messages
                        else -> outputPipe.write(("Unknown channel $channel: $decodedBytes\n").toByteArray())
                    }
                    outputPipe.flush()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.message?.toByteArray()?.let { outputPipe.write(it) }
                outputPipe.flush()
                thisLogger().warn("Error: ${t.message}")
                Thread.sleep(2000)
                isConnected = false
                ApplicationManager.getApplication().invokeLater {
                    terminalWidget?.close()
                    content?.let { contentManager.removeContent(it, true) } // true 表示销毁资源
                }
            }
        })

        return object : TtyConnector {
            val reader = InputStreamReader(inputPipe, Charsets.UTF_8)
            val bufferedReader = BufferedReader(reader)
            override fun read(buf: CharArray, offset: Int, length: Int): Int {
                return bufferedReader.read(buf, offset, length)
            }

//            override fun resize(terminalSize: TermSize) {
//                // 远程服务器需要调整终端大小，可以在这里发送相关命令
//                // {"Width":163,"Height":25}
//                val resizeMessage = "{\"Width\":${terminalSize.columns},\"Height\":${terminalSize.rows}}"
//                val base64Data = Base64.getEncoder().encodeToString(resizeMessage.toByteArray())
//                val message = "4$base64Data" // '4' 表示 resize 事件通道
//                webSocket.send(message)
//            }

            override fun resize(terminalSize: Dimension) {
                // 远程服务器需要调整终端大小，可以在这里发送相关命令
                // {"Width":163,"Height":25}
                val resizeMessage = "{\"Width\":${terminalSize.width},\"Height\":${terminalSize.height}}"
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

            @Suppress("removal")
            override fun init(p0: Questioner?): Boolean {
                return true
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

    fun getPodNames(basicInfo: Triple<String, String, String>, name: String): MutableList<String> {
        val list: Any? = getData("/v3/project/${basicInfo.second}/pods")
        val pods = mutableListOf<String>()
        if (list is List<*>) {
            list.forEach { item ->
                if (item is Map<*, *>) {
                    val workloadId = item["workloadId"]
                    if (workloadId == "deployment:${basicInfo.third}:${name}" && item["type"] == "pod" && item["state"] == "running") {
                        pods.add(item["name"].toString())
                    }
                }
            }
        }
        return pods
    }

    companion object {
        @JvmStatic
        fun getTokenExpiredTime(host: String, apiKey: String): LocalDateTime? {
            if (apiKey.isEmpty() || host.isEmpty()) return null
            val list: Any?
            try {
                list = getData("/v3/token", host, apiKey)
            } catch (e: Exception) {
                return null
            }
            val cattleAccessKey = apiKey.replace("Bearer ", "").split(":")[0]
            if (list is List<*>) {
                list.forEach {
                    if (it is Map<*, *>) {
                        if (it["id"] == cattleAccessKey) {
                            val expires = it["expiresAt"] as String
                            val instant = Instant.parse(expires)
                            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                        }
                    }
                }
            }
            return null
        }

        private fun getData(url: String, host: String, apiKey: String): Any? {
            if (host.isEmpty() || apiKey.isEmpty()) {
                return null
            }
            val client = createUnsafeOkHttpClient()
            val request = Request.Builder()
                .url("https://${host}$url")
                .header("Authorization", apiKey)
                .get()
                .build()
            val response = try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                println("Error: ${e.message}")
                throw IllegalArgumentException("Error: ${e.message}")
            }
            if (response.code != 200) {
                throw IllegalArgumentException("Error: ${response.code}")
            }
            val res = response.body?.string() ?: throw IllegalArgumentException("Data is null")
            val mapper = getMapper()
            val data = mapper.readValue(res, Map::class.java)
            return data["data"]
        }

        private fun createUnsafeOkHttpClient(): OkHttpClient {
            val trustAllCertificates = object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
                }

                @Throws(CertificateException::class)
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


    fun checkReady(): Boolean {
        try {
            getData("/v3/token")
        } catch (e: Exception) {
            return false
        }
        return true
    }

    private fun getSetting(): Pair<String, String> {
        val settings = Settings(project)
        val url: String
        if (settings.rancherHost.isNotEmpty()) {
            url = settings.rancherHost
        } else {
            return Pair("", "")
        }
        val key: String
        if (settings.rancherApiKey.isNotEmpty()) {
            key = settings.rancherApiKey
        } else {
            return Pair("", "")
        }
        return Pair(url, key)
    }


    private fun getData(url: String): Any? {
        val setting = getSetting()
        return getData(url, setting.first, setting.second)
    }

    override fun dispose() {
        refreshAlarm.cancelAllRequests()
    }
}