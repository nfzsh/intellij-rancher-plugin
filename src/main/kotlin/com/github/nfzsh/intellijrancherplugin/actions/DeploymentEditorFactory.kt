package com.github.nfzsh.intellijrancherplugin.actions

import com.github.nfzsh.intellijrancherplugin.MyBundle
import com.github.nfzsh.intellijrancherplugin.services.RancherInfoService
import com.github.nfzsh.intellijrancherplugin.actions.DeploymentEditorKeys.DEPLOYMENT_INFO_KEY
import com.github.nfzsh.intellijrancherplugin.actions.DeploymentEditorKeys.DEPLOYMENT_NAME_KEY
import com.github.nfzsh.intellijrancherplugin.actions.DeploymentEditorKeys.RANCHER_SERVICE_KEY
import com.github.nfzsh.intellijrancherplugin.actions.DeploymentEditorKeys.IS_READ_ONLY_KEY
import com.github.nfzsh.intellijrancherplugin.actions.DeploymentEditorKeys.ORIGINAL_CONTENT_KEY
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Deployment编辑器工厂，用于创建和打开Deployment编辑器
 * 使用IntelliJ原生编辑器提供完整的编辑体验，包括：
 * - 全局统一的搜索逻辑和快捷键
 * - 默认只读模式（UI与编辑模式一致）
 * - 实时YAML格式检测和错误提示
 * - 统一的UI配置
 */
class DeploymentEditorFactory(private val project: Project) {

    private val rancherInfoService = project.service<RancherInfoService>()

    fun updateDevelopmentYaml(virtualFile: VirtualFile) {
        val deploymentName = virtualFile.getUserData(DEPLOYMENT_NAME_KEY) ?: return
        val basicInfo = virtualFile.getUserData(DEPLOYMENT_INFO_KEY) ?: return
        val expectedYaml = virtualFile.getUserData(ORIGINAL_CONTENT_KEY) ?: return
        val latestYaml = rancherInfoService.getDeploymentDetail(basicInfo, deploymentName)
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
        if (document != null) {
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    document.setText(latestYaml)
                    virtualFile.putUserData(ORIGINAL_CONTENT_KEY, latestYaml)
                }
            }
        }
    }
    /**
     * 手动保存当前编辑器中的Deployment YAML
     */
    fun saveCurrentDeployment(virtualFile: VirtualFile) {
        val deploymentName = virtualFile.getUserData(DEPLOYMENT_NAME_KEY) ?: return
        val basicInfo = virtualFile.getUserData(DEPLOYMENT_INFO_KEY) ?: return
        val rancherService = virtualFile.getUserData(RANCHER_SERVICE_KEY) ?: return

        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
        if (document != null) {
            val yamlContent = document.text

            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在保存Deployment...", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "正在验证YAML格式..."
                    indicator.fraction = 0.3

                    try {
                        // 验证YAML格式
                        val yaml = Yaml()
                        yaml.load<Any>(yamlContent)

                        indicator.text = "正在保存到Rancher..."
                        indicator.fraction = 0.7

                        // 更新Deployment
                        val updateInfo = rancherService.updateDeployment(
                            basicInfo,
                            deploymentName,
                            yamlContent
                        )

                        indicator.fraction = 1.0

                        ApplicationManager.getApplication().invokeLater {
                            if (updateInfo.success) {
                                // 提示更新成功
                                Messages.showInfoMessage(
                                    project,
                                    MyBundle.message("deployment_update_success"),
                                    MyBundle.message("success")
                                )
                                // 替换编辑器中的内容
                                updateDevelopmentYaml(virtualFile)
                            } else {
                                Messages.showErrorDialog(
                                    project,
                                    updateInfo.content,
                                    MyBundle.message("error")
                                )
                            }
                        }
                    } catch (e: YAMLException) {
                        ApplicationManager.getApplication().invokeLater {
                            val result = Messages.showYesNoDialog(
                                project,
                                "YAML 格式有误：${e.message}\n\n是否仍要继续保存？",
                                "YAML 格式错误",
                                Messages.getWarningIcon()
                            )
                            if (result == Messages.YES) {
                                // 强制保存
                                ProgressManager.getInstance()
                                    .run(object : Backgroundable(project, "正在强制保存...", false) {
                                        override fun run(indicator: ProgressIndicator) {
                                            val updateInfo = rancherService.updateDeployment(
                                                basicInfo,
                                                deploymentName,
                                                yamlContent
                                            )
                                            ApplicationManager.getApplication().invokeLater {
                                                if (updateInfo.success) {
                                                    // 提示更新成功
                                                    Messages.showInfoMessage(
                                                        project,
                                                        MyBundle.message("deployment_update_success"),
                                                        MyBundle.message("success")
                                                    )
                                                    // 替换编辑器中的内容
                                                    updateDevelopmentYaml(virtualFile)
                                                } else {
                                                    Messages.showErrorDialog(
                                                        project,
                                                        updateInfo.content,
                                                        MyBundle.message("error")
                                                    )
                                                }
                                            }
                                        }
                                    })
                            }
                        }
                    } catch (e: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                e.message ?: MyBundle.message("deployment_update_failed"),
                                MyBundle.message("error")
                            )
                        }
                    }
                }
            })
        }
    }

    fun updateEditorContent(project: Project, virtualFile: VirtualFile, newContent: String) {
        val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return

        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                document.setText(newContent)
            }
        }
    }

    /**
     * 切换只读模式
     */
    fun toggleReadOnlyMode(virtualFile: VirtualFile) {
        val isCurrentlyReadOnly = virtualFile.getUserData(IS_READ_ONLY_KEY) ?: false
        val deploymentName = virtualFile.getUserData(DEPLOYMENT_NAME_KEY) ?: return
        val basicInfo = virtualFile.getUserData(DEPLOYMENT_INFO_KEY) ?: return

        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
        val currentContent = document?.text ?: return

        // 关闭当前文件
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.closeFile(virtualFile)

        // 以相反的只读模式重新打开
        if (isCurrentlyReadOnly) {
            openInEditor(currentContent, basicInfo, deploymentName, false)
        } else {
            openInEditor(currentContent, Triple("", "", ""), deploymentName, true)
        }
    }

    /**
     * 在IntelliJ编辑器中打开Deployment YAML文件进行编辑
     * 添加loading过程，因为这是一个耗时操作
     */
    fun openEditor(deploymentDetail: String, basicInfo: Triple<String, String, String>, deploymentName: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在打开编辑器...", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "正在准备编辑器..."
                indicator.fraction = 0.5

                ApplicationManager.getApplication().invokeLater {
                    openInEditor(deploymentDetail, basicInfo, deploymentName, false)
                    indicator.fraction = 1.0
                }
            }
        })
    }

    /**
     * 在IntelliJ编辑器中以只读模式打开Deployment YAML文件
     */
    fun openReadOnlyEditor(deploymentDetail: String, deploymentName: String) {
        openInEditor(deploymentDetail, Triple("", "", ""), deploymentName, true)
    }

    private fun openInEditor(
        deploymentDetail: String,
        basicInfo: Triple<String, String, String>,
        deploymentName: String,
        isReadOnly: Boolean
    ) {
        ApplicationManager.getApplication().invokeLater {
            // 创建自定义虚拟文件
            val fileName = if (isReadOnly) "$deploymentName-readonly.yaml" else "$deploymentName.yaml"
            val virtualFile = DeploymentVirtualFile(
                deploymentName = deploymentName,
                content = deploymentDetail,
                project = project,
                basicInfo = basicInfo,
                rancherInfoService = rancherInfoService,
                isReadOnly = isReadOnly
            )

            // 设置文件属性
            virtualFile.putUserData(DEPLOYMENT_INFO_KEY, basicInfo)
            virtualFile.putUserData(DEPLOYMENT_NAME_KEY, deploymentName)
            virtualFile.putUserData(RANCHER_SERVICE_KEY, rancherInfoService)
            virtualFile.putUserData(IS_READ_ONLY_KEY, isReadOnly)
            virtualFile.putUserData(ORIGINAL_CONTENT_KEY, deploymentDetail)

            // 在编辑器中打开文件
            openInEditor(virtualFile, project)

            // 如果不是只读模式，设置保存监听器和实时验证
            if (!isReadOnly) {
                ApplicationManager.getApplication().invokeLater {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val editors = fileEditorManager.getEditors(virtualFile)
                    if (editors.isNotEmpty()) {
                        val editor = fileEditorManager.getSelectedEditor(virtualFile)
                        if (editor != null) {
                            setupFileDocumentListener(virtualFile)
                        }
                    }
                }
            }
        }
    }

    /**
     * 在编辑器中打开虚拟文件
     */
    private fun openInEditor(virtualFile: VirtualFile, project: Project) {
        ApplicationManager.getApplication().invokeLater {
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.openFile(virtualFile, true)
        }
    }

    /**
     * 设置文件文档监听器，用于保存时的处理
     */
    private fun setupFileDocumentListener(virtualFile: VirtualFile) {
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
        if (document != null) {
            document.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    // 实时YAML验证在这里可以添加
                    // 由于IntelliJ已经提供了YAML语法高亮和错误检测，这里简化处理
                }
            })
        }
    }

    /**
     * 自定义虚拟文件，用于处理Deployment YAML的保存逻辑
     * 继承LightVirtualFile以获得完整的IntelliJ编辑器支持
     */
    private class DeploymentVirtualFile(
        private val deploymentName: String,
        private val content: String,
        private val project: Project,
        private val basicInfo: Triple<String, String, String>,
        private val rancherInfoService: RancherInfoService,
        private val isReadOnly: Boolean = false
    ) : LightVirtualFile(
        "$deploymentName.yaml",
        FileTypeManager.getInstance().getFileTypeByExtension("yaml"),
        content
    ) {

        init {
            // 设置文件属性
            isWritable = !isReadOnly
            // 设置文件编码
            charset = Charsets.UTF_8
        }

        // 移除自动保存逻辑，改为手动保存
        // 用户需要通过 Ctrl+S 或菜单手动保存
        override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
            if (isReadOnly) {
                throw IllegalStateException("Cannot write to read-only file")
            }

            // 返回一个简单的输出流，不执行自动保存
            return object : ByteArrayOutputStream() {
                override fun close() {
                    super.close()
                    // 不执行自动保存，用户需要手动保存
                }
            }
        }
    }
}
