package com.github.nfzsh.intellijrancherplugin.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Rancher工具窗口工厂类
 */
class MyToolWindowFactory : ToolWindowFactory {

    @Suppress("removal")
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ToolWindowPanel(project)
        val content = ContentFactory.SERVICE.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}
