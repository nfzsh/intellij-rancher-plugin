package com.github.nfzsh.intellijrancherplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.*

/**
 *
 * @author 祝世豪
 * @since 2024/12/24 00:41
 */
class ProjectSettingsConfigurable(private val project: Project) : Configurable {
    private val textFieldRancherHost = JTextField()
    private val textFieldRancherApiKey = JTextField()
    private val checkSsl = JCheckBox("Check SSL")

    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String {
        return "Rancher Plugin Global Setting"
    }

    override fun createComponent(): JComponent {
        // 使用 GridLayout 来组织多个输入框
        mainPanel = JPanel()
        mainPanel?.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

        // 添加第一个输入框
        val panelRancherHost = JPanel()
        panelRancherHost.layout = BoxLayout(panelRancherHost, BoxLayout.X_AXIS)
        panelRancherHost.add(JLabel("rancherHost: "))
        panelRancherHost.add(textFieldRancherHost)

        // 添加第二个输入框
        val panelRancherAPIKey = JPanel()
        panelRancherAPIKey.layout = BoxLayout(panelRancherAPIKey, BoxLayout.X_AXIS)
        panelRancherAPIKey.add(JLabel("rancherAPIKey: "))
        panelRancherAPIKey.add(textFieldRancherApiKey)

        // 添加复选框
        val sslCheck = JPanel()
        sslCheck.layout = BoxLayout(sslCheck, BoxLayout.X_AXIS)
        sslCheck.add(checkSsl)

        // 将子面板加入主面板
        mainPanel?.add(panelRancherHost)
        mainPanel?.add(panelRancherAPIKey)
        mainPanel?.add(sslCheck)

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        // 检查配置是否被修改
        return textFieldRancherHost.text != ProjectSettings.getInstance(project).rancherHost ||
                textFieldRancherApiKey.text != ProjectSettings.getInstance(project).rancherApiKey ||
                checkSsl.isSelected != ProjectSettings.getInstance(project).isSslCheckEnabled
    }

    override fun apply() {
        // 保存用户输入的配置
        val settings = ProjectSettings.getInstance(project)
        settings.rancherHost = textFieldRancherHost.text
        settings.rancherApiKey = textFieldRancherApiKey.text
        settings.isSslCheckEnabled = checkSsl.isSelected
    }

    override fun reset() {
        // 加载当前配置到输入框
        val settings = ProjectSettings.getInstance(project)
        textFieldRancherHost.text = settings.rancherHost
        textFieldRancherApiKey.text = settings.rancherApiKey
        if(settings.isSslCheckEnabled == null) {
            checkSsl.isSelected = false
        } else {
            checkSsl.isSelected = settings.isSslCheckEnabled!!
        }
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}