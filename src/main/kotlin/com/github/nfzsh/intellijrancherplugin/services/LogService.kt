package com.github.nfzsh.intellijrancherplugin.services

import com.github.nfzsh.intellijrancherplugin.models.LogPanel

/**
 *
 * @author 祝世豪
 * @since 2024/12/23 19:07
 */
object LogService {
    private var logPanel: LogPanel? = null

    fun setLogPanel(panel: LogPanel) {
        logPanel = panel
    }

    fun log(message: String) {
        logPanel?.appendLog(message)
    }
}