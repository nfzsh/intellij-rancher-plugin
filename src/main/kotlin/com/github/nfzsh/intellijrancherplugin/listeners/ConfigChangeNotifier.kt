package com.github.nfzsh.intellijrancherplugin.listeners

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

/**
 *
 * @author 祝世豪
 * @since 2025/1/17 10:04
 */
object ConfigChangeNotifier {
    val topic = Topic.create("MyPluginConfigChange", ConfigChangeListener::class.java)

    fun notifyConfigChanged(project: Project) {
        project
            .messageBus
            .syncPublisher(topic)
            .onConfigChanged()
    }
}
