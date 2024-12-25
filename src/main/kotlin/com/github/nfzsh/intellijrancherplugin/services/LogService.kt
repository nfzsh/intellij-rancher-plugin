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
//        val filterProvider : MyConsoleFilterProvider
//        val grepService: GrepConsoleData = project.getService(GrepConsoleService::class.java)
//        if (grepService != null) {
//            // 使用服务获取高亮规则或其他配置
//            val rules: List<HighlightRule> = grepService.getHighlightRules()
//        }
        if(message.isEmpty()) return
        logPanel?.appendLog(message.split("\n").filter { it.isNotEmpty() })
    }
}