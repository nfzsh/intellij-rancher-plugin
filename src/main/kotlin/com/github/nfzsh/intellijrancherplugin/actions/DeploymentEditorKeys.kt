package com.github.nfzsh.intellijrancherplugin.actions

import com.github.nfzsh.intellijrancherplugin.services.RancherInfoService
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorNotificationPanel

/**
 *
 * @author 祝世豪
 * @since 2025/8/5 16:06
 */
object DeploymentEditorKeys {
    val DEPLOYMENT_INFO_KEY = Key.create<Triple<String, String, String>>("DEPLOYMENT_INFO")
    val DEPLOYMENT_NAME_KEY = Key.create<String>("DEPLOYMENT_NAME")
    val RANCHER_SERVICE_KEY = Key.create<RancherInfoService>("RANCHER_SERVICE")
    val IS_READ_ONLY_KEY = Key.create<Boolean>("IS_READ_ONLY")
    val ORIGINAL_CONTENT_KEY = Key.create<String>("ORIGINAL_CONTENT")
    val KEY = Key.create<EditorNotificationPanel>("deployment.editor.panel")
}