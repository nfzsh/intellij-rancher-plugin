package com.github.nfzsh.intellijrancherplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.TitledBorder

/**
 *
 * @author 祝世豪
 * @since 2024/12/24 00:41
 */
class SettingsConfigurable(private val project: Project) : Configurable {
    private val globalRancherHost = JTextField()
    private val globalRancherApiKey = JTextField()

    private val projectRancherHost = JTextField()
    private val projectRancherApiKey = JTextField()

    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String {
        return "Rancher Plugin Setting"
    }

    override fun createComponent(): JComponent {
        // 主面板
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

        // 全局配置面板
        val globalMainPanel = JPanel()
        globalMainPanel.layout = GridBagLayout()
        globalMainPanel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Global Settings",
            TitledBorder.LEFT,
            TitledBorder.TOP
        )

        // 添加全局配置输入框
        addLabelAndTextField(globalMainPanel, "Rancher Host:", globalRancherHost, 0)
        addLabelAndTextField(globalMainPanel, "Rancher API Key:", globalRancherApiKey, 1)

        // 项目级配置面板
        val projectMainPanel = JPanel()
        projectMainPanel.layout = GridBagLayout()
        projectMainPanel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Project Settings",
            TitledBorder.LEFT,
            TitledBorder.TOP
        )

        // 添加项目级配置输入框
        addLabelAndTextField(projectMainPanel, "Rancher Host:", projectRancherHost, 0)
        addLabelAndTextField(projectMainPanel, "Rancher API Key:", projectRancherApiKey, 1)

        // 将子面板加入主面板
        mainPanel.add(globalMainPanel)
        mainPanel.add(Box.createVerticalStrut(10)) // 增加间距
        mainPanel.add(projectMainPanel)

        return mainPanel
    }
    /**
     * 辅助方法：向面板中添加标签和输入框
     */
    private fun addLabelAndTextField(panel: JPanel, labelText: String, textField: JTextField, row: Int) {
        val gbc = GridBagConstraints()
        gbc.gridx = 0
        gbc.gridy = row
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(5) // 设置组件间距
        panel.add(JLabel(labelText), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0 // 允许输入框水平扩展
        panel.add(textField, gbc)
    }

    override fun isModified(): Boolean {
        // 检查配置是否被修改
        return projectRancherHost.text != ProjectSettings.getInstance(project).rancherHost ||
                projectRancherApiKey.text != ProjectSettings.getInstance(project).rancherApiKey ||
                globalRancherHost.text != GlobalSettings.instance.rancherHost ||
                globalRancherApiKey.text != GlobalSettings.instance.rancherApiKey
    }

    override fun apply() {
        // 保存用户输入的配置
        val globalSettings = GlobalSettings.instance
        globalSettings.rancherHost = globalRancherHost.text
        globalSettings.rancherApiKey = globalRancherApiKey.text
        val projectSettings = ProjectSettings.getInstance(project)
        projectSettings.rancherHost = projectRancherHost.text
        projectSettings.rancherApiKey = projectRancherApiKey.text
    }

    override fun reset() {
        // 加载当前配置到输入框
        val globalSettings = GlobalSettings.instance
        globalRancherHost.text = globalSettings.rancherHost
        globalRancherApiKey.text = globalSettings.rancherApiKey

        val projectSettings = ProjectSettings.getInstance(project)
        projectRancherHost.text = projectSettings.rancherHost
        projectRancherApiKey.text = projectSettings.rancherApiKey
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}