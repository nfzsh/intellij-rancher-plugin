package com.github.nfzsh.intellijrancherplugin.actions

import com.github.nfzsh.intellijrancherplugin.MyBundle
import com.github.nfzsh.intellijrancherplugin.services.RancherInfoService
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

/**
 * Rancher动作管理器，处理所有Action的回调
 */
@Service(Service.Level.PROJECT)
class RancherActionManager(private val project: Project) {
    
    private val rancherInfoService = project.service<RancherInfoService>()
    private val remoteToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Remote")
    private val deploymentEditorFactory = DeploymentEditorFactory(project)
    
    companion object {
        fun getInstance(project: Project): RancherActionManager = project.service()
    }
    
    /**
     * 获取基本信息
     */
    fun getBasicInfo(namespace: String): Triple<String, String, String>? {
        val basicInfos = rancherInfoService.basicInfo
        val projectId = basicInfos.find { it.third == namespace }?.second ?: return null
        val cluster = projectId.split(":").getOrNull(0) ?: ""
        return Triple(cluster, projectId, namespace)
    }
    
    /**
     * 处理重新部署
     */
    fun handleRedeploy(actionData: ActionData) {
        val namespace = actionData.path.parentPath.lastPathComponent.toString()
        val basicInfo = getBasicInfo(namespace) ?: return
        
        if (rancherInfoService.redeploy(actionData.deploymentName, basicInfo)) {
            Messages.showInfoMessage(project, MyBundle.message("redeploy_success"), MyBundle.message("success"))
        } else {
            Messages.showErrorDialog(project, MyBundle.message("redeploy_failed"), MyBundle.message("error"))
        }
    }
    
    /**
     * 处理远程日志
     */
    @Suppress("removal")
    fun handleRemoteLog(actionData: ActionData) {
        val podName = actionData.podName ?: return
        val deploymentName = actionData.deploymentName
        val namespace = actionData.path.parentPath.parentPath.lastPathComponent.toString()
        val basicInfo = getBasicInfo(namespace) ?: return
        
        val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        val logContent =
            ContentFactory.SERVICE.getInstance().createContent(consoleView.component, "console_$podName", false).apply {
                isCloseable = true
            }
        remoteToolWindow?.apply {
            contentManager.addContent(logContent)
            contentManager.setSelectedContent(logContent)
            show()
            val webSocket = rancherInfoService.getLogs(basicInfo, deploymentName, podName, consoleView)
            contentManager.addContentManagerListener(object : ContentManagerListener {
                override fun contentRemoved(event: ContentManagerEvent) {
                    if (event.content == logContent) {
                        webSocket.close(1000, "")
                    }
                }
            })
        }
    }
    
    /**
     * 处理远程Shell
     */
    @Suppress("removal")
    fun handleRemoteShell(actionData: ActionData) {
        val podName = actionData.podName ?: return
        val deploymentName = actionData.deploymentName
        val namespace = actionData.path.parentPath.parentPath.lastPathComponent.toString()
        val basicInfo = getBasicInfo(namespace) ?: return
        
        val terminalSettingsProvider = JBTerminalSystemSettingsProviderBase()
        val parentDisposable = Disposer.newDisposable("ShellDisposable_$podName")
        val terminalWidget = JBTerminalWidget(project, terminalSettingsProvider, parentDisposable)
        val shellContent =
            ContentFactory.SERVICE.getInstance().createContent(terminalWidget.component, "shell_$podName", false)
                .apply {
                    isCloseable = true
                }
        val connector = rancherInfoService.createWebSocketTtyConnector(
            terminalWidget,
            remoteToolWindow?.contentManager!!,
            shellContent,
            basicInfo,
            deploymentName,
            podName
        )
        terminalWidget.createTerminalSession(connector)
        terminalWidget.start(connector)
        remoteToolWindow.apply {
            contentManager.addContent(shellContent)
            contentManager.setSelectedContent(shellContent)
            show()
        }
    }
    
    /**
     * 处理编辑Deployment
     */
    fun handleEditDeployment(actionData: ActionData) {
        val namespace = actionData.path.parentPath.lastPathComponent.toString()
        val basicInfo = getBasicInfo(namespace) ?: return
        
        // 获取Deployment详细信息
        try {
            val deploymentDetail = rancherInfoService.getDeploymentDetail(basicInfo, actionData.deploymentName)
            // 打开编辑器
            deploymentEditorFactory.openEditor(deploymentDetail, basicInfo, actionData.deploymentName)
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                e.message ?: MyBundle.message("deployment_load_failed"),
                MyBundle.message("deployment_detail_title")
            )
        }
    }
}