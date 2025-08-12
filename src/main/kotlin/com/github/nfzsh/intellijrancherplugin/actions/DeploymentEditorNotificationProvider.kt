package com.github.nfzsh.intellijrancherplugin.actions

import com.github.nfzsh.intellijrancherplugin.MyBundle
import com.github.nfzsh.intellijrancherplugin.actions.DeploymentEditorKeys.DEPLOYMENT_NAME_KEY
import com.github.nfzsh.intellijrancherplugin.actions.DeploymentEditorKeys.ORIGINAL_CONTENT_KEY
import com.github.nfzsh.intellijrancherplugin.actions.DeploymentEditorKeys.KEY
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications

/**
 * @author 祝世豪
 * @since 2025/8/5 15:39
 */
class DeploymentEditorNotificationProvider(
    private val project: Project
) : EditorNotifications.Provider<EditorNotificationPanel>() {

    override fun

            getKey(): Key<EditorNotificationPanel> = KEY

    override fun

            createNotificationPanel(
        file: VirtualFile,
        fileEditor: FileEditor,
        project: Project
    ): EditorNotificationPanel? {
        // 只处理带有 Deployment 标记的文件
        if (file.getUserData(DEPLOYMENT_NAME_KEY) == null) return null

        val factory = DeploymentEditorFactory(project)
        val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Info)

        panel.text = MyBundle.message("deployment_editor_title") + ": ${file.getUserData(DEPLOYMENT_NAME_KEY)}"

        panel.createActionLabel(MyBundle.message("get_latest_version")) {
            factory.updateDevelopmentYaml(file)
            refresh(project, file)
        }

        panel.createActionLabel(MyBundle.message("save_deployment")) {
            factory.saveCurrentDeployment(file)
            refresh(project, file)
        }

        panel.createActionLabel(MyBundle.message("toggle_readonly")) {
            factory.toggleReadOnlyMode(file)
            refresh(project, file)
        }

        panel.createActionLabel(MyBundle.message("check_modified")) {
            val originalContent = file.getUserData(ORIGINAL_CONTENT_KEY) ?: ""
            val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)
            val currentContent = document?.text ?: ""
            val msg = if (originalContent != currentContent) MyBundle.message("deployment_has_changes")
            else MyBundle.message("deployment_no_changes")
            com.intellij.openapi.ui.Messages.showInfoMessage(project, msg, MyBundle.message("modified_status"))
        }

        panel.createActionLabel(MyBundle.message("help")) {
            val helpText = """
                <html>
                <h3>${MyBundle.message("deployment_editor_help")}</h3>
                <ul>
                <li><b>${MyBundle.message("save_deployment")}:</b> 将当前编辑的YAML内容保存到Rancher服务器</li>
                <li><b>${MyBundle.message("toggle_readonly")}:</b> 在编辑模式和只读模式之间切换</li>
                <li><b>${MyBundle.message("check_modified")}:</b> 查看当前文件是否有未保存的修改</li>
                </ul>
                <p><b>${MyBundle.message("tip")}:</b> 只有打开Deployment文件时，按钮才会生效。</p>
                </html>
            """.trimIndent()
            com.intellij.openapi.ui.Messages.showInfoMessage(project, helpText, MyBundle.message("usage_help"))
        }

        return panel
    }

    private fun refresh(project: Project, file: VirtualFile) {
        EditorNotifications.getInstance(project).updateNotifications(file)
    }
}