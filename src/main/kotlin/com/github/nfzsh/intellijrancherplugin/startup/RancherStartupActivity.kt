package com.github.nfzsh.intellijrancherplugin.startup

import com.github.nfzsh.intellijrancherplugin.listeners.RancherDataLoadedNotifier
import com.github.nfzsh.intellijrancherplugin.services.RancherInfoService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class RancherStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        val service = project.getService(RancherInfoService::class.java)
        service.refreshAsync()
        service.startAutoRefresh()
        project.messageBus.syncPublisher(RancherDataLoadedNotifier.TOPIC).onDataLoaded()
    }
}