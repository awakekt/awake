/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.environment

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.rendering.Light

/**
 * Pushes scene-level environment and lighting state from ECS to the renderer each frame.
 *
 * Two entities drive the renderer:
 * - The first `Environment` entity → sky, fog, ambient (matches Godot's `WorldEnvironment`).
 * - The first `Light(Directional)` entity → shadow toggle (matches Godot's `DirectionalLight3D`
 *   `shadow_enabled`).
 *
 * Must run **before** [com.awakekt.awake.scene.rendering.RenderSystem] so flags are current
 * when `renderer.draw()` is called. Registered via
 * [com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime.defaultInfrastructureSystems].
 *
 * When no `Environment` entity exists in the world the renderer keeps whatever values it already
 * had — an empty scene or a UI-only sample that never loaded an environment entity is unaffected.
 */
@Suppress("DEPRECATION")
class EnvironmentSystem(
    private val renderer: Renderer,
    private val skyboxSystem: SkyboxSystem = SkyboxSystem(renderer),
    private val fogSystem: FogSystem = FogSystem(renderer),
) : System {
    override fun update(world: World, delta: Float) {
        skyboxSystem.update(world, delta)
        fogSystem.update(world, delta)

        // Shadows — owned by the first Directional Light entity (Godot: DirectionalLight3D).
        var directional: Light? = null
        world.family<Light>().forEachComponent { light ->
            if (directional == null && light.type == Light.Type.Directional) {
                directional = light
            }
        }
        if (directional != null) {
            renderer.shadowsEnabled = directional.shadowsEnabled
        }
    }
}
