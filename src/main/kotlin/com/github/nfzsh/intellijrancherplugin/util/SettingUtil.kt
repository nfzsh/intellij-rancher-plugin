package com.github.nfzsh.intellijrancherplugin.util

/**
 *
 * @author 祝世豪
 * @since 2025/1/16 15:36
 */
object SettingUtil {
    fun getHost(globalHost:String, projectHost:String): String {
        var rancherHost = projectHost.ifEmpty {
            globalHost
        }
        if(rancherHost.isEmpty()) {
            return ""
        }
        if(!rancherHost.endsWith("/")) {
            rancherHost += "/"
        }
        rancherHost.replace("https://", "")
        rancherHost.replace("http://", "")
        return rancherHost
    }
    fun getApiKey(globalApiKey:String, projectApiKey:String): String {
        val rancherApiKey = projectApiKey.ifEmpty {
            globalApiKey
        }
        if(rancherApiKey.isEmpty()) {
            return ""
        }
        return "Bearer $rancherApiKey"
    }
}