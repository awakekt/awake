/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples.streaming

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.plugin.GamePlugin
import com.awakekt.awake.scene.core.plugin.PluginId
import com.awakekt.awake.scene.core.plugin.PluginMetadata

/**
 * Showcase plugin demonstrating how Awake Pro capabilities (such as infinite terrain streaming)
 * are modularized as a [GamePlugin] and installed into the active ECS [World].
 */
class WorldStreamGamePlugin : GamePlugin {
    override val metadata: PluginMetadata = PluginMetadata(
        id = PluginId("com.awakekt.pro.worldstream"),
        displayName = "Terrain & Mesh Cell Streaming",
        version = "1.0.0",
        description = "Infinite world partition terrain and navigation streaming",
    )

    override fun install(world: World) {
        // Installs cell streaming capabilities into the active world
    }
}
