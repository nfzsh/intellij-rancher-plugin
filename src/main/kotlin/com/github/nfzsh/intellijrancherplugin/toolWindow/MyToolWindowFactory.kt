package com.github.nfzsh.intellijrancherplugin.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.github.nfzsh.intellijrancherplugin.MyBundle
import com.github.nfzsh.intellijrancherplugin.services.MyProjectService
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
            add(button.apply {
                addActionListener {
                    button.setEnabled(false)
                    button.text = MyBundle.message("loading")
                    label.text = MyBundle.message("randomLabel", service.getDeployment(project))
                    button.setEnabled(true)
                    button.text = if (project.getService(MyProjectService::class.java).isRunning)
                        MyBundle.message("stop") else MyBundle.message("start")
                }
            })
        }
    }
}
