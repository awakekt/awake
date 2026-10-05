/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.particles

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Gives each [ParticleEmitterSource] entity its [ParticleEmitter], following the entity, once its
 * sprite is in [sprites] (see [loadParticleSprites]). Emitters share one quad and one material per
 * sprite, created through [renderer] and destroyed by [release]. Run it alongside [ParticleSystem].
 */
class ParticleContentSystem(
    private val renderer: Renderer,
    private val sprites: Map<String, TextureAsset>,
) : System {
    private var quad: Mesh? = null
    private val materials = HashMap<String, Material>()
    private val waiting = ArrayList<Entity>()

    override fun update(world: World, delta: Float) {
        waiting.clear()
        world.queryEach(ParticleEmitterSource::class) { entity, _ ->
            if (!world.has(entity, ParticleEmitter::class)) waiting += entity
        }
        for (entity in waiting) {
            val settings = world.get<ParticleEmitterSource>(entity)?.settings ?: continue
            materialFor(settings.texture)?.let { world.add(entity, emitter(world, entity, settings, it)) }
        }
    }

    /** Destroys the quad and materials this system created. */
    fun release() {
        quad?.destroy()
        quad = null
        materials.values.forEach(Material::destroy)
        materials.clear()
    }

    private fun materialFor(path: String): Material? = materials[path]
        ?: sprites[path]?.let { sprite -> renderer.createMaterial(ParticleUniformLayout, texture = sprite).also { materials[path] = it } }

    private fun emitter(world: World, entity: Entity, settings: SceneParticleEmitter, material: Material): ParticleEmitter {
        val placed = world.get<Transform>(entity)?.worldMatrix
        val mesh = quad ?: renderer.createMesh(QUAD).also { quad = it }
        return ParticleEmitter(
            mesh = mesh,
            material = material,
            origin = Vec3f(placed?.m03 ?: 0f, placed?.m13 ?: 0f, placed?.m23 ?: 0f),
            maxParticles = settings.maxParticles,
            spawnRate = settings.spawnRate,
            lifetime = settings.lifetime,
            startAlpha = settings.startAlpha,
            scale = settings.scale,
            motion = settings.toMotion(),
            visual = settings.toVisual(),
            ground = settings.toGround(),
            lifecycle = settings.toLifecycle(),
            dynamics = ParticleDynamics(followEntity = entity),
        )
    }
}

/** A unit quad in the XY plane, position and UV; the particle shader lays it along each draw's quad axes. */
private val QUAD = MeshGeometry(
    floatArrayOf(
        -0.5f, -0.5f, 0f, 0f, 1f,
        0.5f, -0.5f, 0f, 1f, 1f,
        0.5f, 0.5f, 0f, 1f, 0f,
        -0.5f, 0.5f, 0f, 0f, 0f,
    ),
    intArrayOf(0, 1, 2, 2, 3, 0),
    format = VertexFormat.PositionUv,
)
