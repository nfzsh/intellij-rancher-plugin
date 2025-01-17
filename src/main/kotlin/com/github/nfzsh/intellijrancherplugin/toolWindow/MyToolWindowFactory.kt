package com.github.nfzsh.intellijrancherplugin.toolWindow

import com.github.nfzsh.intellijrancherplugin.listeners.ConfigChangeListener
import com.github.nfzsh.intellijrancherplugin.listeners.ConfigChangeNotifier
import com.github.nfzsh.intellijrancherplugin.services.RancherInfoService
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
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
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingWorker
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }
    private var basicInfo: Triple<String, String, String>? = null
    private var deploymentName = ""
    private var podName = ""
    private val redeployButton = JButton("Redeploy")
    private val remoteLogButton = JButton("Remote Log")
    private val remoteShellButton = JButton("Remote Shell")
    private val refreshCancelButton = JButton("Refresh")
    private var rancherInfoService: RancherInfoService? = null
    private var remoteToolWindow: ToolWindow? = null
    // 创建占位符内容区域，用于显示加载提示或实际内容
    private val contentPanel = JPanel(BorderLayout()).apply {
        add(JLabel("Loading...", SwingConstants.CENTER), BorderLayout.CENTER) // 初始显示加载提示
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 创建主面板，使用 BorderLayout
        val mainPanel = JPanel(BorderLayout())

        // 创建刷新/取消按钮
        refreshCancelButton.apply {
            icon = AllIcons.Actions.Refresh
            toolTipText = "Reload content"
            isEnabled = true // 初始状态为禁用
        }

        // 创建其他按钮（初始状态为隐藏）
        redeployButton.apply {
            icon = AllIcons.Actions.Refresh
            toolTipText = "Redeploy the selected service"
            isVisible = false // 初始状态为隐藏
            isEnabled = false
            addActionListener { handleRedeploy() }
        }

        remoteLogButton.apply {
            icon = AllIcons.Actions.ShowAsTree
            toolTipText = "View remote logs"
            isVisible = false // 初始状态为隐藏
            isEnabled = false
            addActionListener { handleRemoteLog(project) }
        }

        remoteShellButton.apply {
            icon = AllIcons.Actions.Execute
            toolTipText = "Open remote shell"
            isVisible = false // 初始状态为隐藏
            isEnabled = false
            addActionListener { handleRemoteShell(project) }
        }
        // 订阅配置更改事件
        val connection = project.messageBus.connect()
        connection.subscribe(ConfigChangeNotifier.topic, object : ConfigChangeListener {
            override fun onConfigChanged() {
                reloadContent(project)
            }
        })
        // 创建按钮区域
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 10)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            background = UIUtil.getPanelBackground()
            add(refreshCancelButton) // 添加刷新/取消按钮
            add(redeployButton)      // 添加 Redeploy 按钮（初始隐藏）
            add(remoteLogButton)     // 添加 Remote Log 按钮（初始隐藏）
            add(remoteShellButton)   // 添加 Remote Shell 按钮（初始隐藏）
        }

        // 将内容区域和按钮区域添加到主面板
        mainPanel.add(contentPanel, BorderLayout.CENTER) // 内容区域放在中间
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)   // 按钮区域放在底部

        // 将主面板添加到 ToolWindow 内容
        val content = ContentFactory.getInstance().createContent(mainPanel, null, false)
        toolWindow.contentManager.addContent(content)
        rancherInfoService = RancherInfoService(project)
        remoteToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Remote")
        // 初始加载内容
        reloadContent(project)
    }

    /**
     * 异步加载内容并更新 UI
     */
    private fun reloadContent(project: Project) {
        // 显示加载提示
        contentPanel.removeAll()
        contentPanel.add(JLabel("Loading...", SwingConstants.CENTER), BorderLayout.CENTER)
        contentPanel.revalidate()
        contentPanel.repaint()
        if(rancherInfoService?.checkReady() == false) {
            Messages.showErrorDialog(project, "Please check your settings", "Error")
            refreshCancelButton.text = "Refresh"
            refreshCancelButton.isEnabled = true
            return
        }
        // 更新刷新/取消按钮状态
        refreshCancelButton.text = "Cancel"
        refreshCancelButton.icon = AllIcons.Actions.Cancel
        refreshCancelButton.toolTipText = "Cancel loading"
        refreshCancelButton.isEnabled = true

        // 隐藏其他按钮
        redeployButton.isVisible = false
        remoteLogButton.isVisible = false
        remoteShellButton.isVisible = false

        // 使用 SwingWorker 异步加载内容
        val worker = object : SwingWorker<JPanel, Void>() {
            override fun doInBackground(): JPanel {
                // 在后台加载耗时内容
                val mainPanel = JPanel(BorderLayout())
                // 创建树状结构
                val tree = createTree()
                val scrollPane = JBScrollPane(tree).apply {
                    border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                }
                mainPanel.add(scrollPane, BorderLayout.CENTER)

                return mainPanel
            }

            override fun done() {
                try {
                    if (isCancelled) {
                        // 如果任务被取消，显示取消状态
                        contentPanel.removeAll()
                        contentPanel.add(JLabel("Loading cancelled.", SwingConstants.CENTER), BorderLayout.CENTER)
                    } else {
                        // 加载完成后，替换占位符面板为实际内容
                        val mainPanel = get() // 获取 doInBackground 的返回值
                        contentPanel.removeAll()
                        contentPanel.add(mainPanel, BorderLayout.CENTER)

                        // 显示其他按钮
                        redeployButton.isVisible = true
                        remoteLogButton.isVisible = true
                        remoteShellButton.isVisible = true
                    }
                } catch (e: Exception) {
                    // 处理异常
                    contentPanel.removeAll()
                    contentPanel.add(JLabel("Failed to load content.", SwingConstants.CENTER), BorderLayout.CENTER)
                } finally {
                    // 无论成功、取消还是失败，都恢复刷新按钮状态
                    refreshCancelButton.text = "Refresh"
                    refreshCancelButton.icon = AllIcons.Actions.Refresh
                    refreshCancelButton.toolTipText = "Reload content"
                    refreshCancelButton.isEnabled = true
                }
                contentPanel.revalidate()
                contentPanel.repaint()
            }
        }

        // 设置刷新/取消按钮的点击事件
        refreshCancelButton.addActionListener {
            if (refreshCancelButton.text == "Cancel") {
                // 如果当前是取消按钮，则取消任务
                worker.cancel(true)
            } else {
                // 如果当前是刷新按钮，则重新加载内容
                reloadContent(project)
            }
        }

        // 启动异步任务
        worker.execute()
    }

    private fun createTree(): Tree {
        // cluster, project, namespace 列表
        val basicInfos = rancherInfoService?.basicInfo
        // 创建树的根节点
        val rootNode = DefaultMutableTreeNode("Projects")
        basicInfos?.forEach {
            val namespaceNode = DefaultMutableTreeNode(it.third) // 直接使用 Namespace 作为子节点
            rootNode.add(namespaceNode)
            val deployments = rancherInfoService?.getDeployments(it.second)
            deployments?.forEach { deployment ->
                val deploymentNode = DefaultMutableTreeNode(deployment)
                namespaceNode.add(deploymentNode)
                val pods = rancherInfoService?.getPodNames(it, deployment)
                pods?.forEach { pod ->
                    val podNode = DefaultMutableTreeNode(pod)
                    deploymentNode.add(podNode)
                }
            }
        }
        // 创建树并返回
        val tree = Tree(rootNode)
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        // 监听树形选择事件
        tree.addTreeSelectionListener { event ->
            // 调整路径层级：1 Root -> 2 Namespace -> 3 Deployment -> 4 Pod
            val pathCount = event.path.pathCount
            when (pathCount) {
                3 -> {
                    // deployment
                    deploymentName = event.path.lastPathComponent.toString()
                    val namespace = event.path.parentPath.lastPathComponent.toString()
                    val projectId = basicInfos?.find { it.third == namespace }?.second // 根据 Namespace 查找 Project
                    val cluster = projectId?.split(":")?.get(0) ?: ""
                    basicInfo = Triple(cluster, projectId ?: "", namespace)
                    redeployButton.isEnabled = true
                    remoteLogButton.isEnabled = false
                    remoteShellButton.isEnabled = false
                }

                4 -> {
                    // pod
                    podName = event.path.lastPathComponent.toString()
                    deploymentName = event.path.parentPath.lastPathComponent.toString()
                    val namespace = event.path.parentPath.parentPath.lastPathComponent.toString()
                    val projectId = basicInfos?.find { it.third == namespace }?.second // 根据 Namespace 查找 Project
                    val cluster = projectId?.split(":")?.get(0) ?: ""
                    basicInfo = Triple(cluster, projectId ?: "", namespace)
                    redeployButton.isEnabled = false
                    remoteLogButton.isEnabled = true
                    remoteShellButton.isEnabled = true
                }

                else -> {
                    // 其他情况
                    redeployButton.isEnabled = false
                    remoteLogButton.isEnabled = false
                    remoteShellButton.isEnabled = false
                }
            }
        }
        return tree
    }

    private fun handleRedeploy() {
        val success = basicInfo?.let { rancherInfoService?.redeploy(deploymentName, it) }
        if(success!!) {
            Messages.showInfoMessage("Redeploy success.", "Success")
        } else {
            Messages.showErrorDialog("Redeploy failed.", "Error")
        }
    }

    private fun handleRemoteLog(project: Project) {

        // 创建 Content 并设置为可关闭
        val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        val logContent = ContentFactory.getInstance().createContent(consoleView.component, "console_${podName}", false).apply {
            isCloseable = true
        }
        // 添加 Content 到 ToolWindow
        remoteToolWindow?.contentManager?.addContent(logContent)
        val webSocket = basicInfo?.let { rancherInfoService?.getLogs(it, deploymentName, podName, consoleView) }
        remoteToolWindow?.show()
        remoteToolWindow?.contentManager?.setSelectedContent(logContent)
        // 监听 Content 关闭事件
        remoteToolWindow?.contentManager?.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                if (event.content == logContent) {
                    webSocket?.close(1000, "")
                }
            }
        })


    }

    private fun handleRemoteShell(project: Project) {
        // 创建 JetBrains Terminal
        val terminalSettingsProvider = JBTerminalSystemSettingsProviderBase()
        // 创建一个 Disposable 对象
        val parentDisposable = Disposer.newDisposable("WebSocketShellTerminal")
        val terminalWidget = JBTerminalWidget(project, terminalSettingsProvider, parentDisposable)
        val shellContent = ContentFactory.getInstance().createContent(
            terminalWidget.component,
            "shell_${podName}", false
        )
        shellContent.isCloseable = true
        // 初始化 WebSocket 并连接
        val connector = remoteToolWindow?.let {
            basicInfo?.let { it1 ->
                RancherInfoService(project).createWebSocketTtyConnector(
                    terminalWidget, it.contentManager, shellContent,
                    it1, deploymentName, podName
                )
            }
        }
        terminalWidget.createTerminalSession(connector)
        terminalWidget.start(connector)
        remoteToolWindow?.contentManager?.addContent(shellContent)
        remoteToolWindow?.show()
        remoteToolWindow?.contentManager?.setSelectedContent(shellContent)
    }

    override fun shouldBeAvailable(project: Project) = true
}
