package com.github.nfzsh.intellijrancherplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 *
 * @author 祝世豪
 * @since 2024/12/24 00:38
 */
@State(
    name = "ProjectSettings",
    storages = [Storage("project_settings.xml")]
)
@Service(Service.Level.PROJECT)
class ProjectSettings : PersistentStateComponent<ProjectSettings.State> {


    var rancherHost: String = ""
    var rancherApiKey: String = ""

    data class State(
        var field1: String = "",
        var field2: String = "",
        var isFeatureEnabled: Boolean? = null
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
        fun getInstance(project: Project): ProjectSettings =
            project.getService(ProjectSettings::class.java)
    }
}