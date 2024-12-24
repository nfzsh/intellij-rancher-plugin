package com.github.nfzsh.intellijrancherplugin.models

import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 *
 * @author 祝世豪
 * @since 2024/12/23 19:05
 */
class LogPanel {
    val textArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true

    }
    private val clearButton = JButton("Clear").apply {
        addActionListener { textArea.text = "" }
    }
    val panel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        add(JBScrollPane(textArea), BorderLayout.CENTER)
        add(clearButton, BorderLayout.SOUTH)
    }
    // 添加日志
    fun appendLog(log: String) {
        textArea.append("$log\n")
        textArea.caretPosition = textArea.document.length // 滚动到最新日志
    }
}