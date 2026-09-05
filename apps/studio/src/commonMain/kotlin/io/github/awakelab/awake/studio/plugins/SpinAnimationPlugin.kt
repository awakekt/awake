/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.plugins

import io.github.awakelab.awake.editor.EditorPlugin
import io.github.awakelab.awake.editor.EditorPluginApi
import io.github.awakelab.awake.editor.EditorPluginId
import io.github.awakelab.awake.editor.EditorPluginMetadata
import io.github.awakelab.awake.editor.EditorProvider
import io.github.awakelab.awake.editor.EditorStore
import io.github.awakelab.awake.editor.scene.plugin.SceneSystemPlugin
import io.github.awakelab.awake.scene.authoring.SceneSystemsDsl
import io.github.awakelab.awake.scene.core.transform.SpinSystem
import io.github.awakelab.awake.studio.systems.PlayModeSystem
import io.github.awakelab.awake.studio.systems.SpinClockSystem

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
