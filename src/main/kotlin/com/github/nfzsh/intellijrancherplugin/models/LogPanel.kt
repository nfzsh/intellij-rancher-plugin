package com.github.nfzsh.intellijrancherplugin.models

import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

/**
 * LogPanel class with performance and UI optimizations.
 */
class LogPanel {
    private val allLogs = mutableListOf<String>() // Store all logs
    private val textPane = JTextPane().also {
        it.isEditable = false
        it.text = "" // Initialize text to empty to avoid unnecessary updates
    }

    private val clearButton = JButton("Clear").apply {
        addActionListener { textPane.text = "" }
    }

    private val filterField = JTextField().also {
        it.toolTipText = "Filter logs..."
        it.preferredSize = Dimension(200, 30)
    }

    private val popupMenu = JPopupMenu().also {
        it.add(JMenuItem("Copy").apply {
            addActionListener {
                val selectedText = textPane.selectedText
                if (!selectedText.isNullOrEmpty()) {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        StringSelection(selectedText), null
                    )
                }
            }
        })
        it.add(JMenuItem("Clear").apply {
            addActionListener { textPane.text = "" }
        })
    }

    val panel = JPanel(BorderLayout()).also {
        it.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        it.add(createTopPanel(), BorderLayout.NORTH)
        it.add(JScrollPane(textPane), BorderLayout.CENTER)
        it.add(clearButton, BorderLayout.SOUTH)
    }

    private fun createTopPanel() = JPanel(BorderLayout()).also {
        it.add(filterField, BorderLayout.CENTER)
    }

    // Append log
    fun appendLog(log: String) {
        allLogs.add(log)
        SwingUtilities.invokeLater {
            val level = when {
                log.startsWith("ERROR") -> "ERROR"
                log.startsWith("WARN") -> "WARN"
                log.startsWith("INFO") -> "INFO"
                else -> "DEBUG"
            }
            appendColoredLog(log, level)
            scrollToBottom()
        }
    }

    // Append logs
    fun appendLog(logs: List<String>) {
        SwingUtilities.invokeLater {
            logs.forEach { appendLog(it) }
        }
    }

    @Synchronized
    private fun appendColoredLog(log: String, level: String) {
        val document: StyledDocument = textPane.styledDocument
        val style = textPane.addStyle(level, null)
        when (level) {
            "ERROR" -> StyleConstants.setForeground(style, JBColor.RED)
            "WARN" -> StyleConstants.setForeground(style, JBColor.ORANGE)
            "INFO" -> StyleConstants.setForeground(style, JBColor.BLUE)
            else -> StyleConstants.setForeground(style, JBColor.BLACK)
        }
        StyleConstants.setBold(style, level == "ERROR")
        document.insertString(document.length, "$log\n", style)
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            textPane.caretPosition = textPane.document.length
        }
    }

    init {
        textPane.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) popupMenu.show(e.component, e.x, e.y)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) popupMenu.show(e.component, e.x, e.y)
            }
        })
        filterField.document.addDocumentListener (object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                filterLogs()
            }

            override fun removeUpdate(e: DocumentEvent) {
                filterLogs()
            }

            override fun changedUpdate(e: DocumentEvent) {
                // Plain text components do not fire these events
            }
        })
    }

    private fun filterLogs() {
        val filter = filterField.text
        val filteredLogs = if (filter.isEmpty()) allLogs else allLogs.filter { it.contains(filter, ignoreCase = true) }
        updateLogDisplay(filteredLogs)
    }

    private fun updateLogDisplay(logs: List<String>) {
        SwingUtilities.invokeLater {
            val document = textPane.styledDocument
            document.remove(0, document.length)
            logs.forEach { log ->
                val level = when {
                    log.startsWith("ERROR") -> "ERROR"
                    log.startsWith("WARN") -> "WARN"
                    log.startsWith("INFO") -> "INFO"
                    else -> "DEBUG"
                }
                appendColoredLog(log, level)
            }
            scrollToBottom()
        }
    }
}