package com.github.nfzsh.intellijrancherplugin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.nfzsh.intellijrancherplugin.listeners.ConfigChangeListener
import com.github.nfzsh.intellijrancherplugin.listeners.ConfigChangeNotifier
import com.github.nfzsh.intellijrancherplugin.listeners.RancherDataLoadedNotifier
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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.yaml.snakeyaml.Yaml
import java.awt.Dimension
import java.io.*
import java.security.cert.X509Certificate
import java.time.*
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

@Service(Service.Level.PROJECT)
class RancherInfoService(private val project: Project) : Disposable {

    // 集群信息缓存
    @Volatile
    var basicInfo: MutableList<Triple<String, String, String>> = mutableListOf()

    private val refreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    init {
        // 仅注册配置变更监听器，不立即加载
        val connection = project.messageBus.connect(this)
        connection.subscribe(ConfigChangeNotifier.topic, object : ConfigChangeListener {
            override fun onConfigChanged() {
                refreshAsync()
            }
        })
    }

    /** 懒加载入口：首次调用时触发异步加载 */
    fun ensureLoaded() {
        if (basicInfo.isEmpty()) refreshAsync()

    }

    /** 对外暴露刷新接口 */
    fun refreshAsync() {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val info = fetchInfoSync()
                    ApplicationManager.getApplication().invokeLater {
                        basicInfo = info
                        // ✅ 发出事件
                        project.messageBus.syncPublisher(RancherDataLoadedNotifier.TOPIC).onDataLoaded()
                    }
                } catch (e: Exception) {
                    thisLogger().warn("Failed to refresh Rancher info", e)
                }
            }
        }, 0)
    }

    /** 启动定时刷新（60s），仅在首次成功加载后调用 */
    fun startAutoRefresh(intervalMillis: Long = 60_000L) {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({
            refreshAsync()
            startAutoRefresh(intervalMillis)
        }, intervalMillis)
    }

    /** 同步获取数据，仅后台线程调用 */
    private fun fetchInfoSync(): MutableList<Triple<String, String, String>> {
        val list: Any? = getDataSync("/v3/projects")
        val result = mutableListOf<Triple<String, String, String>>()
        if (list is List<*>) {
            list.forEach { item ->
                if (item is Map<*, *>) {
                    val projectId = item["id"] as? String ?: return@forEach
                    val cluster = projectId.split(":")[0]
                    val namespaces = fetchNamespacesSync(cluster, projectId)
                    namespaces.forEach { result.add(Triple(cluster, projectId, it)) }
                }
            }
        }
        return result
    }

    private fun fetchNamespacesSync(cluster: String, projectId: String): List<String> {
        val list: Any? = getDataSync("/v3/clusters/$cluster/namespaces")
        val ns = mutableListOf<String>()
        if (list is List<*>) {
            list.forEach { item ->
                if (item is Map<*, *>) {
                    val nsId = item["id"] as? String
                    if (nsId != null && projectId == item["projectId"]) ns.add(nsId)
                }
            }
        }
        return ns
    }

    /* ------------------ 以下为原逻辑，仅改为同步版本 ------------------ */

    fun getDeployments(project: String): MutableList<String> {
        val list: Any? = getDataSync("/v3/project/$project/deployments")
        val names = mutableListOf<String>()
        if (list is List<*>) list.forEach { item ->
            if (item is Map<*, *>) (item["containers"] as? List<*>)?.forEach { c ->
                if (c is Map<*, *>) c["name"]?.let { names.add(it.toString()) }
            }
        }
        return names
    }

    fun getDeploymentDetail(
        basicInfo: Triple<String, String, String>,
        deploymentName: String
    ): String = getYamlDataSync(
        "/k8s/clusters/${basicInfo.first}/apis/apps/v1/namespaces/${basicInfo.third}/deployments/$deploymentName"
    ).orEmpty()

    data class DeploymentUpdateResult(
        val success: Boolean,
        val content: String,
        val statusCode: Int
    )

    fun updateDeployment(
        basicInfo: Triple<String, String, String>,
        deploymentName: String,
        deploymentData: String
    ): DeploymentUpdateResult {
        val (host, key) = getSetting()
        val client = createUnsafeOkHttpClient()
        val url = "https://${host.trimEnd('/')}/k8s/clusters/${basicInfo.first.trimEnd('/')}/apis/apps/v1/namespaces/${basicInfo.third}/deployments/$deploymentName"
        val body = deploymentData.toRequestBody("application/yaml; charset=utf-8".toMediaTypeOrNull())
        val req = Request.Builder().url(url).header("Authorization", key)
            .header("Content-Type", "application/yaml")
            .header("Accept", "application/yaml")
            .put(body).build()

        client.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            return if (resp.code == 200) DeploymentUpdateResult(true, bodyStr, 200)
            else DeploymentUpdateResult(false, extractYamlErrorMessage(bodyStr), resp.code)
        }
    }

    fun redeploy(deploymentName: String, basicInfo: Triple<String, String, String>): Boolean {
        val (host, key) = getSetting()
        val client = createUnsafeOkHttpClient()
        val url = "https://${host.trimEnd('/')}/v3/project/${basicInfo.second}/workloads/deployment:${basicInfo.third}:$deploymentName?action=redeploy"
        val req = Request.Builder().url(url).header("Authorization", key).post("".toRequestBody(null)).build()
        return client.newCall(req).execute().code == 200
    }

    fun getPodNames(basicInfo: Triple<String, String, String>, name: String): MutableList<String> {
        val list: Any? = getDataSync("/v3/project/${basicInfo.second}/pods")
        val pods = mutableListOf<String>()
        if (list is List<*>) list.forEach { item ->
            if (item is Map<*, *>) {
                val workload = item["workloadId"]
                if (workload == "deployment:${basicInfo.third}:$name" &&
                    item["type"] == "pod" && item["state"] == "running"
                ) {
                    pods.add(item["name"].toString())
                }
            }
        }
        return pods
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
    fun checkReady(): Boolean {
        try {
            val (host, key) = getSetting()
            Companion.getData("/v3/token", host, key)
        } catch (e: Exception) {
            return false
        }
        return true
    }
    /* ------------------ 工具方法 ------------------ */

    private fun getSetting(): Pair<String, String> {
        val settings = Settings(project)
        val host = settings.rancherHost.takeIf { it.isNotBlank() } ?: ""
        val key = settings.rancherApiKey.takeIf { it.isNotBlank() } ?: ""
        return host to key
    }

    private fun getDataSync(url: String): Any? {
        val (host, key) = getSetting()
        return Companion.getData(url, host, key)
    }

    private fun getYamlDataSync(url: String): String? {
        val (host, key) = getSetting()
        return Companion.getYamlData(url, host, key)
    }

    private fun extractYamlErrorMessage(yamlText: String): String {
        val yaml = Yaml()
        val data = yaml.load<Map<String, Any>>(StringReader(yamlText))
        val code = data["code"]?.toString() ?: "Unknown"
        val message = data["message"]?.toString()?.trimIndent() ?: "Unknown error"
        return "Error $code: $message"
    }

    override fun dispose() {
        refreshAlarm.cancelAllRequests()
    }

    /* ------------------ 静态工具 ------------------ */

    companion object {
        @JvmStatic
        fun getTokenExpiredTime(host: String, apiKey: String): LocalDateTime? {
            if (host.isBlank() || apiKey.isBlank()) return null
            val list: Any? = try {
                getData("/v3/token", host, apiKey)
            } catch (e: Exception) {
                return null
            }
            val key = apiKey.replace("Bearer ", "").split(":")[0]
            if (list is List<*>) {
                list.forEach {
                    if (it is Map<*, *> && it["id"] == key) {
                        val exp = it["expiresAt"] as? String ?: return null
                        return LocalDateTime.ofInstant(Instant.parse(exp), ZoneId.systemDefault())
                    }
                }
            }
            return null
        }

        private fun getData(url: String, host: String, apiKey: String): Any? {
            if (host.isBlank() || apiKey.isBlank()) return null
            val client = createUnsafeOkHttpClient()
            val req = Request.Builder()
                .url("https://$host$url")
                .header("Authorization", apiKey)
                .get()
                .build()
            return client.newCall(req).execute().use { resp ->
                if (resp.code != 200) throw IllegalArgumentException("HTTP ${resp.code}")
                val json = resp.body?.string() ?: throw IllegalArgumentException("Empty body")
                ObjectMapper().readValue(json, Map::class.java)["data"]
            }
        }

        private fun getYamlData(url: String, host: String, apiKey: String): String? {
            if (host.isBlank() || apiKey.isBlank()) return null
            val client = createUnsafeOkHttpClient()
            val req = Request.Builder()
                .url("https://$host$url")
                .header("Authorization", apiKey)
                .header("Accept", "application/yaml")
                .get()
                .build()
            return client.newCall(req).execute().use { resp ->
                if (resp.code != 200) throw IllegalArgumentException("HTTP ${resp.code}")
                resp.body?.string()
            }
        }

        private fun createUnsafeOkHttpClient(): OkHttpClient {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(c: Array<X509Certificate>?, t: String?) {}
                override fun checkServerTrusted(c: Array<X509Certificate>?, t: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ssl = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustAll), java.security.SecureRandom())
            }
            return OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, trustAll)
                .hostnameVerifier { _, _ -> true }
                .build()
        }
    }
}