/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples.ragdoll

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.plugin.GamePlugin
import com.awakekt.awake.scene.core.plugin.PluginId
import com.awakekt.awake.scene.core.plugin.PluginMetadata

/**
 * Showcase plugin demonstrating how Awake Pro capabilities (such as humanoid ragdoll physics)
 * are modularized as a [GamePlugin] and installed into the active ECS [World].
 */
class RagdollGamePlugin : GamePlugin {
    override val metadata: PluginMetadata = PluginMetadata(
        id = PluginId("com.awakekt.pro.physics-ragdoll"),
        displayName = "Humanoid Ragdoll Physics",
        version = "1.0.0",
        description = "Humanoid multi-body joint limit solver and ragdoll simulation",
    )

    override fun install(world: World) {
        // Installs ragdoll capabilities into the active world
    }
}
