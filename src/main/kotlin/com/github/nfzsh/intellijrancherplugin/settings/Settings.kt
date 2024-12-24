package com.github.nfzsh.intellijrancherplugin.settings

import com.intellij.openapi.project.Project

/**
 *
 * @author 祝世豪
 * @since 2024/12/24 16:44
 */
class Settings(project: Project) {

    var rancherHost: String = ""
    var rancherApiKey: String = ""
    var isSslCheckEnabled: Boolean = false

    init {
        val globalSettings = GlobalSettings.instance
        val projectSettings = ProjectSettings.getInstance(project)
        rancherHost = globalSettings.rancherHost
        rancherApiKey = globalSettings.rancherApiKey
        isSslCheckEnabled = globalSettings.isSslCheckEnabled
        if(projectSettings.rancherHost.isNotEmpty()) {
            rancherHost = projectSettings.rancherHost
        }
        if(projectSettings.rancherApiKey.isNotEmpty()) {
            rancherApiKey = projectSettings.rancherApiKey
        }
        if(projectSettings.isSslCheckEnabled != null) {
            isSslCheckEnabled = projectSettings.isSslCheckEnabled!!
        }
        if(rancherHost.startsWith("http://")) {
            throw RuntimeException("Rancher host must be https://")
        }
        if(!rancherHost.endsWith("/")) {
            rancherHost += "/"
        }
        rancherHost.replace("https://", "")
    }
}