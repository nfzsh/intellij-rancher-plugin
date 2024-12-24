package com.github.nfzsh.intellijrancherplugin.toolWindow

import com.github.nfzsh.intellijrancherplugin.models.LogPanel
import com.github.nfzsh.intellijrancherplugin.services.LogService
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 *
 * @author 祝世豪
 * @since 2024/12/23 19:04
 */
class LogWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager

        // 创建日志窗口面板
        val logPanel = LogPanel()
        LogService.setLogPanel(logPanel)
        val content = contentManager.factory.createContent(logPanel.panel, "Logs", false)

        // 添加到工具窗口
        contentManager.addContent(content)
    }
}