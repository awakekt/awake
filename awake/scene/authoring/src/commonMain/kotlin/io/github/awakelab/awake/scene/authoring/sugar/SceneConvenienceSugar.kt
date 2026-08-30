/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.authoring.sugar

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.renderer.CullMode
import io.github.awakelab.awake.scene.authoring.SceneAppDsl
import io.github.awakelab.awake.scene.authoring.dsl.AwakeSceneDsl
import io.github.awakelab.awake.scene.authoring.dsl.EntityScope
import io.github.awakelab.awake.scene.authoring.dsl.SceneBuilder
import io.github.awakelab.awake.scene.authoring.dsl.camera
import io.github.awakelab.awake.scene.authoring.dsl.meshRenderer
import io.github.awakelab.awake.scene.controls.components.CameraMode
import io.github.awakelab.awake.scene.rendering.components.Light

/**
 * Spawns a default orbit (third-person) camera entity.
 *
 * @param target The optional target entity to orbit around.
 * @param name The optional descriptive name for the camera entity.
 * @param block Optional additional configuration lambda.
 * @return The spawned camera [Entity].
 */
fun SceneBuilder.defaultOrbitCamera(
    target: Entity? = null,
    name: String = "camera",
    block: EntityScope.() -> Unit = {},
): Entity = entity(name) {
    camera(mode = CameraMode.ThirdPerson, target = target)
    block()
}

/**
 * Spawns a default orbit (third-person) camera entity on a [SceneAppDsl].
 *
 * @param target The optional target entity to orbit around.
 * @param name The optional descriptive name for the camera entity.
 * @param block Optional additional configuration lambda.
 */
fun SceneAppDsl.defaultOrbitCamera(
    target: Entity? = null,
    name: String = "camera",
    block: EntityScope.() -> Unit = {},
) {
    entity(name) {
        camera(mode = CameraMode.ThirdPerson, target = target)
        block()
    }
}

/**
 * Scope providing convenience helper functions for lighting.
 */
@AwakeSceneDsl
class LightingScope internal constructor(
    private val spawnEntity: (name: String, block: EntityScope.() -> Unit) -> Entity,
) {
    /**
     * Spawns a directional light entity.
     *
     * @param direction World-space direction vector the light shines from.
     * @param color Light RGB color vector.
     * @param intensity Light luminance/radiance multiplier.
     * @return The spawned light [Entity].
     */
    fun singleDirectionalLight(
        direction: Vec3f = Vec3f(0.4f, 0.8f, 0.4f),
        color: Vec3f = Vec3f(1f, 1f, 1f),
        intensity: Float = 1f,
    ): Entity = spawnEntity("light") {
        with(
            Light(
                color = color,
                intensity = intensity,
                type = Light.Type.Directional,
                direction = direction,
            ),
        )
    }
}

/**
 * Lighting convenience sugar for [SceneBuilder].
 */
val SceneBuilder.lighting: LightingScope
    get() = LightingScope { name, block -> entity(name, block) }

/**
 * Lighting convenience sugar for [SceneAppDsl].
 */
val SceneAppDsl.lighting: LightingScope
    get() = LightingScope { name, block ->
        // Spawn entity via SceneAppDsl
        entity(name, block)
        // Return entity placeholder for DSL chaining
        Entity(0)
    }

/**
 * Alias for [meshRenderer] providing concise mesh and material attachment.
 *
 * @param mesh The mesh geometry asset.
 * @param material The material asset.
 * @param cullMode The triangle face cull mode.
 */
fun EntityScope.mesh(
    mesh: Mesh,
    material: Material,
    cullMode: CullMode = CullMode.None,
) = meshRenderer(mesh, material, cullMode)
