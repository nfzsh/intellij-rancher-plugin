package com.github.nfzsh.intellijrancherplugin.settings

import com.github.nfzsh.intellijrancherplugin.listeners.ConfigChangeNotifier
import com.github.nfzsh.intellijrancherplugin.services.RancherInfoService
import com.github.nfzsh.intellijrancherplugin.util.SettingUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.time.LocalDateTime
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

        // 校验按钮
        val validateButton = JButton("Validate Configuration")
        validateButton.addActionListener {
            validateConfiguration()
        }

        // 将子面板和按钮加入主面板
        mainPanel.add(globalMainPanel)
        mainPanel.add(Box.createVerticalStrut(10)) // 增加间距
        mainPanel.add(projectMainPanel)
        mainPanel.add(Box.createVerticalStrut(10)) // 增加间距
        mainPanel.add(validateButton)

        return mainPanel
    }

    /**
     * 校验配置是否正确
     */
    private fun validateConfiguration(): Boolean {
        val globalHost = globalRancherHost.text
        val globalApiKey = globalRancherApiKey.text
        val projectHost = projectRancherHost.text
        val projectApiKey = projectRancherApiKey.text
        val host = SettingUtil.getHost(globalHost, projectHost)
        val apiKey = SettingUtil.getApiKey(globalApiKey, projectApiKey)
        val date = RancherInfoService.getTokenExpiredTime(host, apiKey)
        var isValid = true // 假设校验通过
        if (date == null) {
            isValid = false
        }
        if (isValid) {
            if (date?.isBefore(LocalDateTime.now()) == true) {
                Messages.showErrorDialog("Token expired", "Rancher Setting")
                return false
            }
            Messages.showInfoMessage("Check success", "Rancher Setting")
            return true
        } else {
            Messages.showErrorDialog("This is an error message", "Rancher Setting")
            return false
        }
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
//        val success = validateConfiguration()
//        if(!success) {
//            Messages.showErrorDialog("This is an error message", "Rancher Setting")
//            return
//        }
        // 保存用户输入的配置
        val globalSettings = GlobalSettings.instance
        globalSettings.rancherHost = globalRancherHost.text
        globalSettings.rancherApiKey = globalRancherApiKey.text
        val projectSettings = ProjectSettings.getInstance(project)
        projectSettings.rancherHost = projectRancherHost.text
        projectSettings.rancherApiKey = projectRancherApiKey.text
        ConfigChangeNotifier.notifyConfigChanged(project)
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