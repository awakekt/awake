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
import com.awakekt.awake.scene.rendering.Fog

/**
 * Pushes atmospheric distance fog state from ECS to the [Renderer] each frame.
 *
 * Evaluates the active [Fog] entity in the world (with fallback to legacy [Environment]).
 */
@Suppress("DEPRECATION")
class FogSystem(private val renderer: Renderer) : System {
    override fun update(world: World, delta: Float) {
        val fog = world.singleOrNull<Fog>() ?: world.firstOrNull<Fog>()
        val env = world.singleOrNull<Environment>() ?: world.firstOrNull<Environment>()

        if (fog != null) {
            renderer.fogDensity = if (fog.enabled) fog.density else 0f
            renderer.fogColor = fog.color
        } else if (env != null) {
            renderer.fogDensity = env.fogDensity
            renderer.fogColor = env.fogColor
        }
    }
}
