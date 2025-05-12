package com.github.nfzsh.intellijrancherplugin.toolWindow

/**
 *
 * @author 祝世豪
 * @since 2025/5/12 19:40
 */
import com.github.nfzsh.intellijrancherplugin.listeners.ConfigChangeListener
import com.github.nfzsh.intellijrancherplugin.listeners.ConfigChangeNotifier
import com.github.nfzsh.intellijrancherplugin.services.RancherInfoService
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class ToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private var basicInfo: Triple<String, String, String>? = null
    private var deploymentName = ""
    private var podName = ""

    private val redeployButton = JButton("Redeploy")
    private val remoteLogButton = JButton("Remote Log")
    private val remoteShellButton = JButton("Remote Shell")
    private val refreshCancelButton = JButton("Refresh")

    private val rancherInfoService = RancherInfoService(project)
    private val remoteToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Remote")
    private val contentPanel = JPanel(BorderLayout())

    init {
        thisLogger().warn("Initialized ToolWindowPanel for project: ${project.name}")

        // 初始化 UI
        setupButtons()
        setupLayout()
        subscribeToConfigChanges()
        reloadContent()
    }

    private fun setupButtons() {
        redeployButton.apply {
            icon = AllIcons.Actions.Refresh
            toolTipText = "Redeploy the selected service"
            isVisible = false
            isEnabled = false
            addActionListener { handleRedeploy() }
        }

        remoteLogButton.apply {
            icon = AllIcons.Actions.ShowAsTree
            toolTipText = "View remote logs"
            isVisible = false
            isEnabled = false
            addActionListener { handleRemoteLog() }
        }

        remoteShellButton.apply {
            icon = AllIcons.Actions.Execute
            toolTipText = "Open remote shell"
            isVisible = false
            isEnabled = false
            addActionListener { handleRemoteShell() }
        }

        refreshCancelButton.apply {
            icon = AllIcons.Actions.Refresh
            toolTipText = "Reload content"
            isEnabled = true
        }
    }

    private fun setupLayout() {
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 10)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            background = UIUtil.getPanelBackground()
            add(refreshCancelButton)
            add(redeployButton)
            add(remoteLogButton)
            add(remoteShellButton)
        }

        contentPanel.add(JLabel("Loading...", SwingConstants.CENTER), BorderLayout.CENTER)

        add(contentPanel, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun subscribeToConfigChanges() {
        project.messageBus.connect().subscribe(ConfigChangeNotifier.topic, object : ConfigChangeListener {
            override fun onConfigChanged() {
                reloadContent()
            }
        })
    }

    private fun reloadContent() {
        contentPanel.removeAll()
        contentPanel.add(JLabel("Loading...", SwingConstants.CENTER), BorderLayout.CENTER)
        contentPanel.revalidate()
        contentPanel.repaint()

        if (!rancherInfoService.checkReady()) {
            Messages.showErrorDialog(project, "Please check your settings", "Error")
            return
        }

        val worker = object : SwingWorker<JPanel, Void>() {
            override fun doInBackground(): JPanel {
                val panel = JPanel(BorderLayout())
                val tree = createTree()
                panel.add(JBScrollPane(tree).apply {
                    border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                }, BorderLayout.CENTER)
                return panel
            }

            override fun done() {
                try {
                    contentPanel.removeAll()
                    contentPanel.add(get(), BorderLayout.CENTER)
                    redeployButton.isVisible = true
                    remoteLogButton.isVisible = true
                    remoteShellButton.isVisible = true
                } catch (e: Exception) {
                    contentPanel.removeAll()
                    contentPanel.add(JLabel("Failed to load content.", SwingConstants.CENTER), BorderLayout.CENTER)
                } finally {
                    refreshCancelButton.text = "Refresh"
                    refreshCancelButton.icon = AllIcons.Actions.Refresh
                    refreshCancelButton.isEnabled = true
                }
                contentPanel.revalidate()
                contentPanel.repaint()
            }
        }

        refreshCancelButton.addActionListener {
            if (!worker.isDone) worker.cancel(true) else reloadContent()
        }

        refreshCancelButton.text = "Cancel"
        refreshCancelButton.icon = AllIcons.Actions.Cancel
        worker.execute()
    }

    private fun createTree(): Tree {
        val basicInfos = rancherInfoService.basicInfo
        val rootNode = DefaultMutableTreeNode("Projects")
        basicInfos.forEach {
            val namespaceNode = DefaultMutableTreeNode(it.third)
            rootNode.add(namespaceNode)
            rancherInfoService.getDeployments(it.second).forEach { deployment ->
                val deploymentNode = DefaultMutableTreeNode(deployment)
                namespaceNode.add(deploymentNode)
                rancherInfoService.getPodNames(it, deployment).forEach { pod ->
                    deploymentNode.add(DefaultMutableTreeNode(pod))
                }
            }
        }
        return Tree(rootNode).apply {
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            addTreeSelectionListener { event ->
                when (event.path.pathCount) {
                    3 -> {
                        deploymentName = event.path.lastPathComponent.toString()
                        val namespace = event.path.parentPath.lastPathComponent.toString()
                        val projectId = basicInfos.find { it.third == namespace }?.second ?: return@addTreeSelectionListener
                        val cluster = projectId.split(":").getOrNull(0) ?: ""
                        basicInfo = Triple(cluster, projectId, namespace)
                        redeployButton.isEnabled = true
                        remoteLogButton.isEnabled = false
                        remoteShellButton.isEnabled = false
                    }
                    4 -> {
                        podName = event.path.lastPathComponent.toString()
                        deploymentName = event.path.parentPath.lastPathComponent.toString()
                        val namespace = event.path.parentPath.parentPath.lastPathComponent.toString()
                        val projectId = basicInfos.find { it.third == namespace }?.second ?: return@addTreeSelectionListener
                        val cluster = projectId.split(":").getOrNull(0) ?: ""
                        basicInfo = Triple(cluster, projectId, namespace)
                        redeployButton.isEnabled = false
                        remoteLogButton.isEnabled = true
                        remoteShellButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun handleRedeploy() {
        if (rancherInfoService.redeploy(deploymentName, basicInfo!!)) {
            Messages.showInfoMessage("Redeploy success.", "Success")
        } else {
            Messages.showErrorDialog("Redeploy failed.", "Error")
        }
    }

    private fun handleRemoteLog() {
        val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        val logContent = ContentFactory.getInstance().createContent(consoleView.component, "console_$podName", false).apply {
            isCloseable = true
        }
        remoteToolWindow?.apply {
            contentManager.addContent(logContent)
            contentManager.setSelectedContent(logContent)
            show()
            val webSocket = rancherInfoService.getLogs(basicInfo!!, deploymentName, podName, consoleView)
            contentManager.addContentManagerListener(object : ContentManagerListener {
                override fun contentRemoved(event: ContentManagerEvent) {
                    if (event.content == logContent) {
                        webSocket.close(1000, "")
                    }
                }
            })
        }
    }

    private fun handleRemoteShell() {
        val terminalSettingsProvider = JBTerminalSystemSettingsProviderBase()
        val parentDisposable = Disposer.newDisposable("ShellDisposable_$podName")
        val terminalWidget = JBTerminalWidget(project, terminalSettingsProvider, parentDisposable)
        val shellContent = ContentFactory.getInstance().createContent(terminalWidget.component, "shell_$podName", false).apply {
            isCloseable = true
        }
        val connector = rancherInfoService.createWebSocketTtyConnector(
            terminalWidget,
            remoteToolWindow?.contentManager!!,
            shellContent,
            basicInfo!!,
            deploymentName,
            podName
        )
        terminalWidget.createTerminalSession(connector)
        terminalWidget.start(connector)
        remoteToolWindow.apply {
            contentManager.addContent(shellContent)
            contentManager.setSelectedContent(shellContent)
            show()
        }
    }
}
