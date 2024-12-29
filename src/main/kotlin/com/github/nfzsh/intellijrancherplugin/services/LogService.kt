package com.github.nfzsh.intellijrancherplugin.services

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 *
 * @author 祝世豪
 * @since 2024/12/23 19:07
 */
@Service(Service.Level.PROJECT)
class LogService(private val project: Project) {
    private var consoleView: ConsoleView? = null

    fun getOrCreateConsoleView(): ConsoleView {
        if (consoleView == null) {
            consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        }
        return consoleView!!
    }
    fun log(message: String) {
        if (message.isEmpty()) return
        val consoleView = getOrCreateConsoleView()
        consoleView.print(message, ConsoleViewContentType.NORMAL_OUTPUT)

    }
}