/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.environment

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.firstOrNull
import com.awakekt.awake.ecs.singleOrNull
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.rendering.Environment
import com.awakekt.awake.scene.rendering.Skybox

/**
 * Pushes skybox and background atmosphere state from ECS to the [Renderer] each frame.
 *
 * Evaluates the active [Skybox] entity in the world (with fallback to legacy [Environment]).
 */
@Suppress("DEPRECATION")
class SkyboxSystem(private val renderer: Renderer) : System {
    override fun update(world: World, delta: Float) {
        val skybox = world.singleOrNull<Skybox>() ?: world.firstOrNull<Skybox>()
        val env = world.singleOrNull<Environment>() ?: world.firstOrNull<Environment>()

        if (skybox != null) {
            renderer.showEnvironment = skybox.enabled
            renderer.horizonColor = skybox.horizonColor
            renderer.zenithColor = skybox.zenithColor
        } else if (env != null) {
            renderer.showEnvironment = env.showEnvironment
            renderer.horizonColor = env.horizonColor
            renderer.zenithColor = env.zenithColor
        }
    }
}
