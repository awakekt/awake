/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.particles.ParticleContentSystem
import com.awakekt.awake.scene.rendering.particles.ParticleEmitter
import com.awakekt.awake.scene.rendering.particles.ParticleEmitterBinding
import com.awakekt.awake.scene.rendering.particles.ParticleEmitterSource
import com.awakekt.awake.scene.rendering.particles.ParticleFacing
import com.awakekt.awake.scene.rendering.particles.SceneParticleEmitter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SceneParticleEmitterTest {
    init {
        SceneComponentRegistry.registerGlobal(ParticleEmitterBinding)
    }

    private val sprite = TextureAsset(ByteArray(4) { -1 }, width = 1, height = 1)
    private val dust = SceneParticleEmitter(texture = "dust.png", endScale = 0.5f, radialSpeed = 3f, additive = true)

    @Test
    fun anEmitterSpawnsAtAndFollowsItsNodeOnceItsSpriteIsLoaded() {
        val world = World()
        val node = world.node(dust, at = Vec3f(1f, 2f, 3f))

        ParticleContentSystem(CountingRenderer(), mapOf("dust.png" to sprite)).update(world, 0f)

        val emitter = requireNotNull(world.get<ParticleEmitter>(node))
        assertEquals(Vec3f(1f, 2f, 3f), emitter.origin)
        assertEquals(node, emitter.dynamics.followEntity)
        assertEquals(0.5f, emitter.visual.endScale)
        assertEquals(true, emitter.visual.additive)
        assertEquals(3f, emitter.motion.radialSpeed)
    }

    /** `facing` survives the scene document both ways and reaches the live emitter; left out, it is `Camera`. */
    @Test
    fun facingRoundTripsThroughTheDocumentAndReachesTheLiveEmitter() {
        fun decode(component: String) = SceneLoader.decode("""{"version": 1, "nodes": [{"components": [$component]}]}""")
            .nodes.single().components.single() as SceneParticleEmitter
        val glow = decode("""{"component": "particle_emitter", "texture": "glow.png", "facing": "Flat"}""")
        val reencoded = SceneLoader.decode(SceneLoader.encode(SceneDocument(nodes = listOf(SceneNode("glow", components = listOf(glow))))))
        val world = World()
        val node = world.node(glow)

        ParticleContentSystem(CountingRenderer(), mapOf("glow.png" to sprite)).update(world, 0f)

        assertEquals(ParticleFacing.Flat, glow.facing)
        assertEquals(glow, reencoded.nodes.single().components.single())
        assertEquals(ParticleFacing.Flat, world.get<ParticleEmitter>(node)!!.visual.facing)
        assertEquals(glow, ParticleEmitterBinding.export(world, node, world.get<ParticleEmitterSource>(node)!!))
        assertEquals(ParticleFacing.Camera, decode("""{"component": "particle_emitter", "texture": "dust.png"}""").facing)
    }

    @Test
    fun emittersShareOneQuadAndOneMaterialPerSpriteUntilReleased() {
        val world = World()
        val renderer = CountingRenderer()
        val first = world.node(dust)
        val second = world.node(dust)
        world.node(dust.copy(texture = "spark.png"))
        val content = ParticleContentSystem(renderer, mapOf("dust.png" to sprite, "spark.png" to sprite))

        content.update(world, 0f)
        content.update(world, 0f)

        assertEquals(1, renderer.meshes, "one quad for every emitter")
        assertEquals(2, renderer.materials, "one material per sprite")
        assertSame(world.get<ParticleEmitter>(first)!!.material, world.get<ParticleEmitter>(second)!!.material)
        content.release()
        assertEquals(3, renderer.destroyed)
    }

    @Test
    fun anEmitterWhoseSpriteDidNotLoadDrawsNothing() {
        val world = World()
        val node = world.node(dust)

        ParticleContentSystem(CountingRenderer(), emptyMap()).update(world, 0f)

        assertNull(world.get<ParticleEmitter>(node))
    }

    @Test
    fun theAuthoredComponentExportsAndValidates() {
        val world = World()
        val node = world.node(dust)

        assertEquals(dust, ParticleEmitterBinding.export(world, node, world.get<ParticleEmitterSource>(node)!!))
        assertEquals(
            listOf("particle_emitter.lifetime must be greater than 0", "particle_emitter.scale must be greater than 0"),
            dust.copy(lifetime = 0f, scale = 0f).validate("fx").map { it.message },
        )
    }

    private fun World.node(authored: SceneParticleEmitter, at: Vec3f = Vec3f(0f, 0f, 0f)): Entity = create().also {
        add(it, Transform(position = at).apply { worldMatrix.m03 = at.x; worldMatrix.m13 = at.y; worldMatrix.m23 = at.z })
        add(it, ParticleEmitterSource(authored))
    }

    private class CountingRenderer : NoopRenderer() {
        var meshes = 0
        var materials = 0
        var destroyed = 0

        override fun createMesh(geometry: MeshGeometry): Mesh {
            meshes++
            val mesh = super.createMesh(geometry)
            return object : Mesh by mesh {
                override fun destroy() {
                    destroyed++
                }
            }
        }

        override fun createMaterial(
            texture: TextureAsset?,
            renderTarget: RenderTarget?,
            uniformFloatCount: Int,
            pbrTextures: PbrTextureSet?,
        ): Material {
            materials++
            val material = super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
            return object : Material by material {
                override fun destroy() {
                    destroyed++
                }
            }
        }
    }
}
