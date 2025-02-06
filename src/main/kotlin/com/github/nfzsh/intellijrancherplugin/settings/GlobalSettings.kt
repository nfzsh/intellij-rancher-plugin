package com.github.nfzsh.intellijrancherplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 *
 * @author 祝世豪
 * @since 2024/12/24 00:33
 */
// 定义存储结构
@State(
    name = "GlobalSettings",
    storages = [Storage("global_settings.xml")]
)
@Service // 声明为服务
class GlobalSettings : PersistentStateComponent<GlobalSettings.State> {

    var rancherHost: String = ""
    var rancherApiKey: String = ""

    data class State(
        var field1: String = "",
        var field2: String = "",
        var isFeatureEnabled: Boolean = false
    )

    private var state = State()

    override fun getState(): State {
        state.field1 = rancherHost
        state.field2 = rancherApiKey
        return state
    }

    override fun loadState(state: State) {
        this.rancherHost = state.field1
        this.rancherApiKey = state.field2
    }

    companion object {
        val instance: GlobalSettings
            get() = ApplicationManager.getApplication().getService(GlobalSettings::class.java)
    }
}

