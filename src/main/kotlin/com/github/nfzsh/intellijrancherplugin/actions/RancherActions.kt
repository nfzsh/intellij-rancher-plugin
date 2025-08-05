package com.github.nfzsh.intellijrancherplugin.actions

import com.github.nfzsh.intellijrancherplugin.MyBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * 刷新按钮动作
 */
class RefreshAction(private val refreshCallback: () -> Unit) : AnAction(
    MyBundle.message("refresh"),
    MyBundle.message("refresh_desc"),
    AllIcons.Actions.Refresh
), DumbAware {
    private var enabled = true
    private var isCancelMode = false

    override fun actionPerformed(e: AnActionEvent) {
        refreshCallback()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = enabled
        if (isCancelMode) {
            e.presentation.icon = AllIcons.Actions.Cancel
            e.presentation.text = MyBundle.message("cancel")
        } else {
            e.presentation.icon = AllIcons.Actions.Refresh
            e.presentation.text = MyBundle.message("refresh")
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setCancelMode(cancelMode: Boolean) {
        this.isCancelMode = cancelMode
    }
}

/**
 * 重新部署按钮动作
 */
class RedeployAction(private val redeployCallback: () -> Unit) : AnAction(
    MyBundle.message("redeploy_action"),
    MyBundle.message("redeploy_desc"),
    AllIcons.Actions.Restart
), DumbAware {
    private var enabled = false

    override fun actionPerformed(e: AnActionEvent) {
        redeployCallback()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = enabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}

/**
 * 远程日志按钮动作
 */
class RemoteLogAction(private val logCallback: () -> Unit) : AnAction(
    MyBundle.message("remote_log"),
    MyBundle.message("remote_log_desc"),
    AllIcons.Actions.ShowAsTree
), DumbAware {
    private var enabled = false

    override fun actionPerformed(e: AnActionEvent) {
        logCallback()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = enabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}

/**
 * 远程Shell按钮动作
 */
class RemoteShellAction(private val shellCallback: () -> Unit) : AnAction(
    MyBundle.message("remote_shell"),
    MyBundle.message("remote_shell_desc"),
    AllIcons.Actions.Execute
), DumbAware {
    private var enabled = false

    override fun actionPerformed(e: AnActionEvent) {
        shellCallback()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = enabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}

/**
 * 编辑Deployment按钮动作
 */
class EditDeploymentAction(private val editCallback: () -> Unit) : AnAction(
    MyBundle.message("edit_deployment"),
    MyBundle.message("edit_deployment_desc"),
    AllIcons.Actions.Edit
), DumbAware {
    private var enabled = false

    override fun actionPerformed(e: AnActionEvent) {
        editCallback()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = enabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
