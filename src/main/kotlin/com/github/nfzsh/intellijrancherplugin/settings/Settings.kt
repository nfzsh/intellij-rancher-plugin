package com.github.nfzsh.intellijrancherplugin.settings

import com.github.nfzsh.intellijrancherplugin.util.SettingUtil
import com.intellij.openapi.project.Project

/**
 *
 * @author 祝世豪
 * @since 2024/12/24 16:44
 */
class Settings(project: Project) {

    var rancherHost: String = ""
    var rancherApiKey: String = ""

    init {
        val globalSettings = GlobalSettings.instance
        val projectSettings = ProjectSettings.getInstance(project)
        rancherHost = SettingUtil.getHost(globalSettings.rancherHost, projectSettings.rancherHost)
        rancherApiKey = SettingUtil.getApiKey(globalSettings.rancherApiKey, projectSettings.rancherApiKey)
    }
}