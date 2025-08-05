package com.github.nfzsh.intellijrancherplugin.toolWindow

/**
 *
 * @author 祝世豪
 * @since 2025/5/12 19:40
 */
import com.github.nfzsh.intellijrancherplugin.MyBundle
import com.github.nfzsh.intellijrancherplugin.actions.*
import com.github.nfzsh.intellijrancherplugin.listeners.ConfigChangeListener
import com.github.nfzsh.intellijrancherplugin.listeners.ConfigChangeNotifier
import com.github.nfzsh.intellijrancherplugin.listeners.RancherDataLoadedListener
import com.github.nfzsh.intellijrancherplugin.listeners.RancherDataLoadedNotifier
import com.github.nfzsh.intellijrancherplugin.services.RancherInfoService
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBEmptyBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class ToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private var basicInfo: Triple<String, String, String>? = null
    private var deploymentName = ""
    private var podName = ""

    private var currentWorker: SwingWorker<JPanel, Void>? = null
    private val rancherInfoService = project.getService(RancherInfoService::class.java)
    private val remoteToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Remote")
    private val actionManager = RancherActionManager.getInstance(project)
    val statusLabel = JLabel(MyBundle.message("ready")).apply {
        horizontalAlignment = SwingConstants.LEFT
        border = JBEmptyBorder(5, 10, 5, 10)
        foreground = JBColor.GREEN
    }
    private val contentPanel = JPanel(BorderLayout())

    // Action实例
    private val refreshAction = RefreshAction { reloadContent() }
    private val redeployAction = RedeployAction { handleRedeploy() }
    private val remoteLogAction = RemoteLogAction { handleRemoteLog() }
    private val remoteShellAction = RemoteShellAction { handleRemoteShell() }
    private val editDeploymentAction = EditDeploymentAction { handleEditDeployment() }
    override fun dispose() {
    }
    init {
        // 初始化时禁用所有操作按钮
        redeployAction.setEnabled(false)
        remoteLogAction.setEnabled(false)
        remoteShellAction.setEnabled(false)
        editDeploymentAction.setEnabled(false)
        rancherInfoService.ensureLoaded()

        // 初始化 UI
        setupLayout()
        subscribeToConfigChanges()
        // 监听 RancherInfoService 初始化完成事件
        project.messageBus.connect(this)
            .subscribe(RancherDataLoadedNotifier.TOPIC, object : RancherDataLoadedListener {
                override fun onDataLoaded() {
                    reloadContent()
                }
            })
    }

    // 保存ActionToolbar引用以便更新UI
    private lateinit var toolbar: ActionToolbar

    private fun setupLayout() {
        // 创建顶部工具栏
        val actionGroup = DefaultActionGroup().apply {
            add(refreshAction)
            addSeparator()
            add(redeployAction)
            add(editDeploymentAction)
            add(remoteLogAction)
            add(remoteShellAction)
            // 未来可以在这里轻松添加更多按钮
        }

        toolbar = ActionManager.getInstance().createActionToolbar(
            ActionPlaces.TOOLBAR, actionGroup, true
        ).apply {
            targetComponent = this@ToolWindowPanel
            setReservePlaceAutoPopupIcon(false)
        }

        contentPanel.add(JLabel(MyBundle.message("loading"), SwingConstants.CENTER), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)
        add(toolbar.component, BorderLayout.NORTH)
    }

    private fun subscribeToConfigChanges() {
        project.messageBus.connect().subscribe(ConfigChangeNotifier.topic, object : ConfigChangeListener {
            override fun onConfigChanged() {
                reloadContent()
            }
        })
    }

    private fun reloadContent() {
        // 如果当前有任务在执行，则取消任务
        if (currentWorker != null && !currentWorker!!.isDone) {
            currentWorker!!.cancel(true)
            currentWorker = null
            // 恢复刷新按钮状态
            refreshAction.setCancelMode(false)
            refreshAction.setEnabled(true)
            toolbar.updateActionsImmediately()
            statusLabel.text = MyBundle.message("ready")
            return
        }

        // 1. 设置状态为加载中
        statusLabel.text = MyBundle.message("loading_status")

        if (!rancherInfoService.checkReady()) {
            statusLabel.text = MyBundle.message("error")
            Messages.showErrorDialog(project, MyBundle.message("check_settings"), MyBundle.message("error_title"))
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
                    if (!isCancelled) {
                        val newContent = get()
                        contentPanel.removeAll()
                        contentPanel.add(newContent, BorderLayout.CENTER)
                    }

                    // 更新Action状态
                    refreshAction.setCancelMode(false)
                    refreshAction.setEnabled(true)
                    toolbar.updateActionsImmediately()

                    statusLabel.text = MyBundle.message("ready")
                } catch (e: Exception) {
                    statusLabel.text = MyBundle.message("failed")
                    Messages.showErrorDialog(
                        project,
                        MyBundle.message("load_failed", e.message ?: ""),
                        MyBundle.message("error_title")
                    )
                } finally {
                    currentWorker = null
                    contentPanel.revalidate()
                    contentPanel.repaint()
                }
            }
        }

        // 更新刷新按钮为取消按钮
        refreshAction.setCancelMode(true)
        refreshAction.setEnabled(true)
        toolbar.updateActionsImmediately()

        currentWorker = worker
        worker.execute()
    }

    private fun createTree(): Tree {
        val basicInfos = rancherInfoService.basicInfo
        val rootNode = DefaultMutableTreeNode(MyBundle.message("projects"))
        runBlocking {
            val namespaceNodes = basicInfos.map { info ->
                async(Dispatchers.IO) {
                    val (cluster, projectId, namespace) = info
                    val namespaceNode = DefaultMutableTreeNode(namespace)

                    val deployments = rancherInfoService.getDeployments(projectId)
                    val deploymentNodes = deployments.map { deployment ->
                        async(Dispatchers.IO) {
                            val deploymentNode = DefaultMutableTreeNode(deployment)
                            val podNames = rancherInfoService.getPodNames(info, deployment)
                            podNames.forEach { pod ->
                                deploymentNode.add(DefaultMutableTreeNode(pod))
                            }
                            deploymentNode
                        }
                    }.awaitAll()

                    deploymentNodes.forEach { namespaceNode.add(it) }
                    namespaceNode
                }
            }.awaitAll()

            namespaceNodes.forEach { rootNode.add(it) }
        }
        return Tree(rootNode).apply {
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

            // 添加树节点选择监听器
            addTreeSelectionListener { event ->
                when (event.path.pathCount) {
                    3 -> {
                        deploymentName = event.path.lastPathComponent.toString()
                        val namespace = event.path.parentPath.lastPathComponent.toString()
                        val projectId =
                            basicInfos.find { it.third == namespace }?.second ?: return@addTreeSelectionListener
                        val cluster = projectId.split(":").getOrNull(0) ?: ""
                        basicInfo = Triple(cluster, projectId, namespace)

                        // 更新Action状态
                        redeployAction.setEnabled(true)
                        editDeploymentAction.setEnabled(true)
                        remoteLogAction.setEnabled(false)
                        remoteShellAction.setEnabled(false)

                        // 刷新工具栏UI
                        toolbar.updateActionsImmediately()
                    }

                    4 -> {
                        podName = event.path.lastPathComponent.toString()
                        deploymentName = event.path.parentPath.lastPathComponent.toString()
                        val namespace = event.path.parentPath.parentPath.lastPathComponent.toString()
                        val projectId =
                            basicInfos.find { it.third == namespace }?.second ?: return@addTreeSelectionListener
                        val cluster = projectId.split(":").getOrNull(0) ?: ""
                        basicInfo = Triple(cluster, projectId, namespace)

                        // 更新Action状态
                        redeployAction.setEnabled(false)
                        editDeploymentAction.setEnabled(false)
                        remoteLogAction.setEnabled(true)
                        remoteShellAction.setEnabled(true)

                        // 刷新工具栏UI
                        toolbar.updateActionsImmediately()
                    }

                    else -> {
                        deploymentName = ""
                        podName = ""

                        // 刷新Action状态
                        redeployAction.setEnabled(false)
                        editDeploymentAction.setEnabled(false)
                        remoteLogAction.setEnabled(false)
                        remoteShellAction.setEnabled(false)

                        // 刷新工具栏UI
                        toolbar.updateActionsImmediately()
                    }
                }
            }

            // 添加右键菜单
            val popupActionGroup = TreeNodeActionGroup()
            PopupHandler.installPopupMenu(this, popupActionGroup, ActionPlaces.POPUP)
        }
    }

    /**
     * 处理重新部署
     */
    private fun handleRedeploy() {
        basicInfo?.let {
            // 直接调用服务方法，不需要传递TreePath
            if (rancherInfoService.redeploy(deploymentName, it)) {
                Messages.showInfoMessage(project, MyBundle.message("redeploy_success"), MyBundle.message("success"))
            } else {
                Messages.showErrorDialog(project, MyBundle.message("redeploy_failed"), MyBundle.message("error_title"))
            }
        }
    }

    /**
     * 处理远程日志
     */
    @Suppress("removal")
    private fun handleRemoteLog() {
        basicInfo?.let {
            // 创建控制台视图并显示日志
            val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
            val logContent =
                ContentFactory.SERVICE.getInstance().createContent(consoleView.component, "console_$podName", false)
                    .apply {
                        isCloseable = true
                    }
            remoteToolWindow?.apply {
                contentManager.addContent(logContent)
                contentManager.setSelectedContent(logContent)
                show()
                val webSocket = rancherInfoService.getLogs(it, deploymentName, podName, consoleView)
                contentManager.addContentManagerListener(object : ContentManagerListener {
                    override fun contentRemoved(event: ContentManagerEvent) {
                        if (event.content == logContent) {
                            webSocket.close(1000, "")
                        }
                    }
                })
            }
        }
    }

    /**
     * 处理远程Shell
     */
    @Suppress("removal")
    private fun handleRemoteShell() {
        basicInfo?.let {
            // 创建终端并连接到远程Shell
            val terminalSettingsProvider = JBTerminalSystemSettingsProviderBase()
            val parentDisposable = Disposer.newDisposable("ShellDisposable_$podName")
            val terminalWidget = JBTerminalWidget(project, terminalSettingsProvider, parentDisposable)
            val shellContent =
                ContentFactory.SERVICE.getInstance().createContent(terminalWidget.component, "shell_$podName", false)
                    .apply {
                        isCloseable = true
                    }
            val connector = rancherInfoService.createWebSocketTtyConnector(
                terminalWidget,
                remoteToolWindow?.contentManager!!,
                shellContent,
                it,
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

    /**
     * 处理编辑Deployment
     */
    private fun handleEditDeployment() {
        basicInfo?.let {
            try {
                // 直接调用服务方法获取Deployment详细信息并打开编辑器
                val deploymentDetail = rancherInfoService.getDeploymentDetail(it, deploymentName)
                // 创建编辑器工厂并打开编辑器
                val deploymentEditorFactory = DeploymentEditorFactory(project)
                deploymentEditorFactory.openEditor(deploymentDetail, it, deploymentName)
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    e.message ?: MyBundle.message("deployment_load_failed"),
                    MyBundle.message("deployment_detail_title")
                )
            }
        }
    }
}
