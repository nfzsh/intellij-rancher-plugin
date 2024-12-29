package com.github.nfzsh.intellijrancherplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.nfzsh.intellijrancherplugin.MyBundle
import okhttp3.WebSocket


@Service(Service.Level.PROJECT)
class MyProjectService(project: Project) {

    var ws: WebSocket? = null
    var isRunning = false

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
    }

    fun getDeployment(project: Project): String {
        val service = RancherInfoService(project)
//        val deployments = service.getDeployments()
        if(ws == null) {
            ws = service.getLogs()
            isRunning = true

        } else {
            ws!!.close(1000, null)
            isRunning = false
            ws = null
        }

//        deployments.forEach {
//            if (it == project.name) {
//                return it
//            }
//        }
        return ""
    }
}
