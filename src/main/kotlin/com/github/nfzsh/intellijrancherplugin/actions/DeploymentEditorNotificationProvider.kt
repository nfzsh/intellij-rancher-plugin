package com.github.nfzsh.intellijrancherplugin.actions

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

        panel.text = "Deployment 编辑器: ${file.getUserData(DEPLOYMENT_NAME_KEY)}"

        panel.createActionLabel("获取最新版本") {
            factory.updateDevelopmentYaml(file)
            refresh(project, file)
        }

        panel.createActionLabel("保存") {
            factory.saveCurrentDeployment(file)
            refresh(project, file)
        }

        panel.createActionLabel("切换只读模式") {
            factory.toggleReadOnlyMode(file)
            refresh(project, file)
        }

        panel.createActionLabel("检查修改状态") {
            val originalContent = file.getUserData(ORIGINAL_CONTENT_KEY) ?: ""
            val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)
            val currentContent = document?.text ?: ""
            val msg = if (originalContent != currentContent) "当前Deployment有未保存的修改"
            else "无修改"
            com.intellij.openapi.ui.Messages.showInfoMessage(project, msg, "修改状态")
        }

        panel.createActionLabel("帮助") {
            val helpText = """
                <html>
                <h3>Deployment编辑器使用帮助</h3>
                <ul>
                    <li><b>保存Deployment:</b> 将当前编辑的YAML内容保存到Rancher服务器</li>
                    <li><b>切换只读模式:</b> 在编辑模式和只读模式之间切换</li>
                    <li><b>检查修改状态:</b> 查看当前文件是否有未保存的修改</li>
                </ul>
                <p><b>注意:</b> 只有打开Deployment文件时，按钮才会生效。</p>
                </html>
            """.trimIndent()
            com.intellij.openapi.ui.Messages.showInfoMessage(project, helpText, "使用帮助")
        }

        return panel
    }

    private fun refresh(project: Project, file: VirtualFile) {
        EditorNotifications.getInstance(project).updateNotifications(file)
    }
}