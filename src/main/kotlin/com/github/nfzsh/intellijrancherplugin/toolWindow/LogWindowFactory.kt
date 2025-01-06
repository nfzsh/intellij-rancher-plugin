package com.github.nfzsh.intellijrancherplugin.toolWindow

import com.github.nfzsh.intellijrancherplugin.services.LogService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 *
 * @author 祝世豪
 * @since 2024/12/23 19:04
 */
class LogWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val logService = project.getService(LogService::class.java)
        val consoleView = logService.getOrCreateConsoleView()
        val contentFactory = ContentFactory.getInstance()
        val logContent = contentFactory.createContent(consoleView.component, "Remote Logs", false)
        toolWindow.contentManager.addContent(logContent)
//        // 创建 JetBrains Terminal
//        val terminalSettingsProvider = JBTerminalSystemSettingsProviderBase()
//        // 创建一个 Disposable 对象
//        val parentDisposable = Disposer.newDisposable("WebSocketShellTerminal")
//        val terminalWidget = JBTerminalWidget(project, terminalSettingsProvider, parentDisposable)
//
//        // 初始化 WebSocket 并连接
//        val connector = RancherInfoService(project).createWebSocketTtyConnector()
//        terminalWidget.createTerminalSession(connector)
//        terminalWidget.start(connector)
//        val shellContent = contentFactory.createContent(terminalWidget.component, "Remote Shell", false)
//        toolWindow.contentManager.addContent(shellContent)
    }
}