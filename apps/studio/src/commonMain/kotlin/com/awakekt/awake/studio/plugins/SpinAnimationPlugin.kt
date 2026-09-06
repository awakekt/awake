/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins

import com.awakekt.awake.editor.EditorPlugin
import com.awakekt.awake.editor.EditorPluginApi
import com.awakekt.awake.editor.EditorPluginId
import com.awakekt.awake.editor.EditorPluginMetadata
import com.awakekt.awake.editor.EditorProvider
import com.awakekt.awake.editor.EditorStore
import com.awakekt.awake.editor.scene.plugin.SceneSystemPlugin
import com.awakekt.awake.scene.authoring.SceneSystemsDsl
import com.awakekt.awake.scene.core.transform.SpinSystem
import com.awakekt.awake.studio.systems.PlayModeSystem
import com.awakekt.awake.studio.systems.SpinClockSystem

/**
 * Modular plugin contributing rotating cube spin behavior to Studio during Play mode.
 */
class SpinAnimationPlugin(private val store: EditorStore) :
    EditorPlugin,
    SceneSystemPlugin {
    override val metadata = EditorPluginMetadata(
        id = EditorPluginId("awake.animation.spin"),
        displayName = "Spin Animation",
        version = "0.1.0",
        requiredApiVersion = EditorPluginApi.currentVersion,
    )

    override fun createProviders(): List<EditorProvider> = emptyList()

    override fun registerSystems(dsl: SceneSystemsDsl) {
        dsl.frameSystem("spin-clock") { PlayModeSystem(SpinClockSystem(), store) }
        dsl.frameSystem("spin") { PlayModeSystem(SpinSystem(), store) }
    }
}
