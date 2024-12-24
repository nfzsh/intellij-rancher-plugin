package com.github.nfzsh.intellijrancherplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.nfzsh.intellijrancherplugin.MyBundle

@Service(Service.Level.PROJECT)
class MyProjectService(project: Project) {

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    fun getRandomNumber() = (1..100).random()

    fun getDeployment(project: Project): String {
        val service = RancherInfoService(project)
        val deployments = service.getDeployments()
        val ws = service.getLogs()
        Thread.sleep(1000)
        ws.close(1000, null)
        deployments.forEach {
            if (it == project.name) {
                return it
            }
        }
        return ""
    }
}
