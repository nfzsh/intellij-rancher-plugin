package com.github.nfzsh.intellijrancherplugin.toolWindow

import com.github.nfzsh.intellijrancherplugin.MyBundle
import com.github.nfzsh.intellijrancherplugin.services.MyProjectService
import com.github.nfzsh.intellijrancherplugin.services.RancherInfoService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import javax.swing.JButton


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(project), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<MyProjectService>()

        fun getContent(project: Project) = JBPanel<JBPanel<*>>().apply {
            val label = JBLabel(MyBundle.message("randomLabel", project.name))
            add(label)
            val button = JButton(MyBundle.message("start"))
            val contentFactory = ContentFactory.getInstance()
            add(button.apply {
                addActionListener {
                    button.setEnabled(false)
                    label.text = MyBundle.message("randomLabel", service.getDeployment(project))
                    button.setEnabled(true)
                    button.text = if (project.getService(MyProjectService::class.java).isRunning)
                        MyBundle.message("stop") else MyBundle.message("start")
                }
            })
            val connect = JButton(MyBundle.message("connect"))
            add(connect.apply {
                addActionListener {
                    // 创建 JetBrains Terminal
                    val terminalSettingsProvider = JBTerminalSystemSettingsProviderBase()
                    // 创建一个 Disposable 对象
                    val parentDisposable = Disposer.newDisposable("WebSocketShellTerminal")
                    val terminalWidget = JBTerminalWidget(project, terminalSettingsProvider, parentDisposable)
                    val shellContent = contentFactory.createContent(terminalWidget.component, "Remote Shell", false)
                    shellContent.isCloseable = true
                    // 初始化 WebSocket 并连接
                    val remoteToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Remote")
                    val connector = remoteToolWindow?.let {
                        RancherInfoService(project).createWebSocketTtyConnector(terminalWidget, it.contentManager, shellContent)
                    }
                    terminalWidget.createTerminalSession(connector)
                    terminalWidget.start(connector)
                    remoteToolWindow?.contentManager?.addContent(shellContent)
                    remoteToolWindow?.contentManager?.setSelectedContent(shellContent)
                }
            })

            val deploy = JButton(MyBundle.message("redeploy"))
            add(deploy.apply {
                addActionListener {
                    val rancherInfoService = project.getService(RancherInfoService::class.java)
                    val success = rancherInfoService.redeploy(project.name)
                }
            })
        }
    }
}
