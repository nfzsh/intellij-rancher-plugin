package com.github.nfzsh.intellijrancherplugin.listeners

import com.intellij.util.messages.Topic

/**
 *
 * @author 祝世豪
 * @since 2025/8/5 20:13
 */
interface RancherDataLoadedListener {
    fun onDataLoaded()
}

object RancherDataLoadedNotifier {
    val TOPIC = Topic.create("RancherDataLoaded", RancherDataLoadedListener::class.java)
}