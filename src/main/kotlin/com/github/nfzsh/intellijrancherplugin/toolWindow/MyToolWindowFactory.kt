package com.github.nfzsh.intellijrancherplugin.toolWindow

import com.github.nfzsh.intellijrancherplugin.MyBundle
import com.github.nfzsh.intellijrancherplugin.services.MyProjectService
import com.github.nfzsh.intellijrancherplugin.services.RancherInfoService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
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
    private var rancherInfoService: RancherInfoService? = null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 创建一个占位符面板，用于快速显示窗口
        val placeholderPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("Loading...", SwingConstants.CENTER), BorderLayout.CENTER) // 显示加载提示
        }
        val loadingLabel = JLabel("Loading...", AnimatedIcon.Default(), SwingConstants.CENTER)
        placeholderPanel.add(loadingLabel, BorderLayout.CENTER)

        // 将占位符面板添加到 ToolWindow 内容
        val content = ContentFactory.getInstance().createContent(placeholderPanel, null, false)
        toolWindow.contentManager.addContent(content)
        // 使用 SwingWorker 异步加载内容
        val worker = object : SwingWorker<JPanel, Void>() {
            override fun doInBackground(): JPanel {
                rancherInfoService = RancherInfoService(project)
                val mainPanel = JPanel(BorderLayout())
                // 创建树状结构
                val tree = createTree()
                val scrollPane = JBScrollPane(tree).apply {
                    border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                }
                mainPanel.add(scrollPane, BorderLayout.CENTER)
                // 创建按钮区域
                val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                    border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                    background = UIUtil.getPanelBackground()
                }

                // 创建按钮
                redeployButton.apply {
                    icon = AllIcons.Actions.Refresh
                    isEnabled = false
                    toolTipText = "Redeploy the selected service"
                    addActionListener { handleRedeploy() }
                }

                remoteLogButton.apply {
                    icon = AllIcons.Actions.ShowAsTree
                    toolTipText = "View remote logs"
                    isEnabled = false
                    addActionListener { handleRemoteLog(project) }
                }

                remoteShellButton.apply {
                    icon = AllIcons.Debugger.Console
                    toolTipText = "Open remote shell"
                    isEnabled = false
                    addActionListener { handleRemoteShell(project) }
                }

                // 添加按钮到按钮区域
                buttonPanel.add(redeployButton)
                buttonPanel.add(remoteLogButton)
                buttonPanel.add(remoteShellButton)

                // 将按钮区域放在底部
                mainPanel.add(buttonPanel, BorderLayout.SOUTH)
                return mainPanel
            }
            override fun done() {
                try {
                    // 加载完成后，替换占位符面板为实际内容
                    val mainPanel = get() // 获取 doInBackground 的返回值
                    placeholderPanel.removeAll()
                    placeholderPanel.add(mainPanel, BorderLayout.CENTER)
                    placeholderPanel.revalidate()
                    placeholderPanel.repaint()
                } catch (e: Exception) {
                    // 处理异常
                    placeholderPanel.removeAll()
                    placeholderPanel.add(JLabel("Failed to load content.", SwingConstants.CENTER), BorderLayout.CENTER)
                    placeholderPanel.revalidate()
                    placeholderPanel.repaint()
                }
            }
        }
        // 启动异步任务
        worker.execute()
        val cancelButton = JButton("Cancel").apply {
            addActionListener { worker.cancel(true) }
        }
        placeholderPanel.add(cancelButton, BorderLayout.SOUTH)
    }

    private fun createTree(): Tree {
        // cluster, project, namespace 列表
        val basicInfos = rancherInfoService?.basicInfo
        // 创建树的根节点
        val rootNode = DefaultMutableTreeNode("Projects")
        basicInfos?.forEach {
            val projectNode = DefaultMutableTreeNode(it.second)
            val namespaceNode = DefaultMutableTreeNode(it.third)
            rootNode.add(projectNode)
            projectNode.add(namespaceNode)
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
            // 2 project 3 namespace 4 deployment 5 pod
            val pathCount = event.path.pathCount
            when (pathCount) {
                4 -> {
                    // deployment
                    deploymentName = event.path.lastPathComponent.toString()
                    val namespace = event.path.parentPath.lastPathComponent.toString()
                    val projectId = event.path.parentPath.parentPath.lastPathComponent.toString()
                    val cluster = projectId.split(":")[0]
                    basicInfo = Triple(cluster, projectId, namespace)
                    redeployButton.isEnabled = true
                    remoteLogButton.isEnabled = false
                    remoteShellButton.isEnabled = false
                }

                5 -> {
                    // pod
                    podName = event.path.lastPathComponent.toString()
                    deploymentName = event.path.parentPath.lastPathComponent.toString()
                    val namespace = event.path.parentPath.parentPath.lastPathComponent.toString()
                    val projectId = event.path.parentPath.parentPath.parentPath.lastPathComponent.toString()
                    val cluster = projectId.split(":")[0]
                    basicInfo = Triple(cluster, projectId, namespace)
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
    }

    private fun handleRemoteLog(project: Project) {
        remoteLogButton.setEnabled(true)
        val service = project.service<MyProjectService>()
        basicInfo?.let { service.getDeployment(project, it, deploymentName, podName) }
        remoteLogButton.text = if (project.getService(MyProjectService::class.java).isRunning)
            MyBundle.message("stop") else MyBundle.message("start")
    }

    private fun handleRemoteShell(project: Project) {
        // 创建 JetBrains Terminal
        val terminalSettingsProvider = JBTerminalSystemSettingsProviderBase()
        // 创建一个 Disposable 对象
        val parentDisposable = Disposer.newDisposable("WebSocketShellTerminal")
        val terminalWidget = JBTerminalWidget(project, terminalSettingsProvider, parentDisposable)
        val shellContent = ContentFactory.getInstance().createContent(
            terminalWidget.component,
            "RemoteShell-${podName}", false
        )
        shellContent.isCloseable = true
        // 初始化 WebSocket 并连接
        val remoteToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Remote")
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
        remoteToolWindow?.contentManager?.setSelectedContent(shellContent)
    }

    override fun shouldBeAvailable(project: Project) = true
}
