package com.github.nfzsh.intellijrancherplugin.actions

import com.github.nfzsh.intellijrancherplugin.MyBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.treeStructure.Tree
import javax.swing.tree.TreePath

/**
 * 树节点右键菜单动作组
 */
class TreeNodeActionGroup : ActionGroup(), DumbAware {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        e ?: return emptyArray()

        val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree ?: return emptyArray()
        val path = tree.selectionPath ?: return emptyArray()

        return when (path.pathCount) {
            3 -> { // Deployment节点
                arrayOf(RedeployActionPopup(), EditDeploymentActionPopup())
            }

            4 -> { // Pod节点
                arrayOf(RemoteLogActionPopup(), RemoteShellActionPopup())
            }

            else -> emptyArray()
        }
    }

    override fun update(e: AnActionEvent) {
        val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree
        e.presentation.isEnabledAndVisible = tree != null && tree.selectionPath != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT // 简单的UI状态检查，使用EDT
    }
}

/**
 * 右键菜单中的重新部署动作
 */
class RedeployActionPopup :
    AnAction(MyBundle.message("redeploy_action"), MyBundle.message("redeploy_desc"), AllIcons.Actions.Restart),
    DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree ?: return
        val path = tree.selectionPath ?: return

        // 获取树节点数据并触发重新部署
        val deploymentName = path.lastPathComponent.toString()
        val actionData = ActionData(tree, path, deploymentName, null)
        e.project?.let { RancherActionManager.getInstance(it).handleRedeploy(actionData) }
    }

    override fun update(e: AnActionEvent) {
        val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree
        val path = tree?.selectionPath
        e.presentation.isEnabledAndVisible = tree != null && path != null && path.pathCount == 3
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT // 简单的UI状态检查，使用EDT
    }
}

/**
 * 右键菜单中的远程日志动作
 */
class RemoteLogActionPopup :
    AnAction(MyBundle.message("remote_log"), MyBundle.message("remote_log_desc"), AllIcons.Actions.ShowAsTree),
    DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree ?: return
        val path = tree.selectionPath ?: return

        // 获取树节点数据并触发查看日志
        val podName = path.lastPathComponent.toString()
        val deploymentName = path.parentPath.lastPathComponent.toString()
        val actionData = ActionData(tree, path, deploymentName, podName)
        e.project?.let { RancherActionManager.getInstance(it).handleRemoteLog(actionData) }
    }

    override fun update(e: AnActionEvent) {
        val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree
        val path = tree?.selectionPath
        e.presentation.isEnabledAndVisible = tree != null && path != null && path.pathCount == 4
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT // 简单的UI状态检查，使用EDT
    }
}

/**
 * 右键菜单中的远程Shell动作
 */
class RemoteShellActionPopup :
    AnAction(MyBundle.message("remote_shell"), MyBundle.message("remote_shell_desc"), AllIcons.Actions.Execute),
    DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree ?: return
        val path = tree.selectionPath ?: return

        // 获取树节点数据并触发打开Shell
        val podName = path.lastPathComponent.toString()
        val deploymentName = path.parentPath.lastPathComponent.toString()
        val actionData = ActionData(tree, path, deploymentName, podName)
        e.project?.let { RancherActionManager.getInstance(it).handleRemoteShell(actionData) }
    }

    override fun update(e: AnActionEvent) {
        val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree
        val path = tree?.selectionPath
        e.presentation.isEnabledAndVisible = tree != null && path != null && path.pathCount == 4
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT // 简单的UI状态检查，使用EDT
    }
}

/**
 * 右键菜单中的编辑Deployment动作
 */
class EditDeploymentActionPopup :
    AnAction(MyBundle.message("edit_deployment"), MyBundle.message("edit_deployment_desc"), AllIcons.Actions.Edit),
    DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree ?: return
        val path = tree.selectionPath ?: return

        // 获取树节点数据并触发编辑Deployment
        val deploymentName = path.lastPathComponent.toString()
        val actionData = ActionData(tree, path, deploymentName, null)
        e.project?.let { RancherActionManager.getInstance(it).handleEditDeployment(actionData) }
    }

    override fun update(e: AnActionEvent) {
        val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree
        val path = tree?.selectionPath
        e.presentation.isEnabledAndVisible = tree != null && path != null && path.pathCount == 3
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT // 简单的UI状态检查，使用EDT
    }
}

/**
 * 动作数据类，用于传递树节点信息
 */
data class ActionData(
    val tree: Tree,
    val path: TreePath,
    val deploymentName: String,
    val podName: String?
)
