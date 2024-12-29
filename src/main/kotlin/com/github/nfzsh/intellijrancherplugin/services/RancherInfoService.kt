package com.github.nfzsh.intellijrancherplugin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.nfzsh.intellijrancherplugin.settings.Settings
import com.intellij.openapi.project.Project
import okhttp3.*
import okio.ByteString
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 *
 * @author 祝世豪
 * @since 2024/12/24 17:16
 */
class RancherInfoService(private val project: Project) {

    /**
     * 集群信息
     * @return cluster, project, namespace
     */
    val basicInfo: MutableList<Triple<String, String, String>>

    init {
        basicInfo = getInfo()
    }

    fun getInfo(): MutableList<Triple<String, String, String>> {
        val list: Any? = getData("/v3/projects")
        val info: MutableList<Triple<String, String, String>> = mutableListOf()
        if (list is List<*>) {
            list.forEach { item ->
                if (item is Map<*, *>) {
                    val projectId = item["id"]
                    if (projectId is String) {
                        val cluster = projectId.split(":")[0]
                        val namespaces = getNameSpace(cluster)
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
            .header("Authorization", "Bearer token-pd976:mfhnbrz4pzcqkbdc22rkwvvgwpcqr7x5g75db4cfwdzrg4rw4t77bs")
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

    private fun getNameSpace(cluster: String): MutableList<String> {
        val list: Any? = getData("/v3/clusters/$cluster/namespaces")
        val namespaces = mutableListOf<String>()
        if (list is List<*>) {
            list.forEach { item ->
                if (item is Map<*, *>) {
                    val namespace = item["id"]
                    if (namespace is String && !namespaces.contains(namespace)) {
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
        if(list is List<*>) {
            list.forEach { item ->
                if (item is Map<*, *>) {
                    val workloadId = item["workloadId"]
                    if(workloadId == "deployment:${basicInfo.third}:${project.name}" && item["type"] == "pod" && item["state"] == "running") {
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