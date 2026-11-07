/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.particles

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.particles.ParticleSpace
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneVec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A scene file's `space` on a `particle_emitter`: read, written, checked and carried to the live emitter. */
class SceneParticleSpaceTest {
    init {
        SceneComponentRegistry.registerGlobal(ParticleEmitterBinding)
    }

    private val sprite = TextureAsset(ByteArray(4) { -1 }, width = 1, height = 1)
    private val glow = SceneParticleEmitter(texture = "glow.png", space = ParticleSpace.Local)

    private fun decode(component: String) = SceneLoader.decode("""{"version": 1, "nodes": [{"components": [$component]}]}""")
        .nodes.single().components.single() as SceneParticleEmitter

    private fun encode(emitter: SceneParticleEmitter) =
        SceneLoader.encode(SceneDocument(nodes = listOf(SceneNode("fx", components = listOf(emitter)))))

    @Test
    fun spaceRoundTripsThroughTheDocument() {
        val decoded = decode("""{"component": "particle_emitter", "texture": "glow.png", "space": "Local"}""")

        assertEquals(glow, decoded)
        assertEquals(glow, SceneLoader.decode(encode(decoded)).nodes.single().components.single())
    }

    @Test
    fun leftOutSpaceIsWorldAndIsWrittenLikeTheOtherDefaults() {
        val plain = decode("""{"component": "particle_emitter", "texture": "dust.png"}""")

        assertEquals(ParticleSpace.World, plain.space)
        assertTrue(Regex(""""space"\s*:\s*"World"""").containsMatchIn(encode(plain)), "written as the module's other defaults are")
    }

    /** `space` is written in scene files as the runtime enum's constant names, so those names are the file format. */
    @Test
    fun theNamesOfTheSpacesAreTheFileFormat() {
        assertEquals(listOf("World", "Local"), ParticleSpace.entries.map { it.name })
    }

    @Test
    fun aLocalEmitterWithAGroundIsRefused() {
        val grounded = glow.copy(ground = SceneParticleGround(groundY = 0f))

        assertEquals(
            listOf("particle_emitter.ground does not apply to a Local space emitter"),
            grounded.validate("fx").map { it.message },
        )
        assertEquals(emptyList(), glow.validate("fx"))
        assertEquals(emptyList(), grounded.copy(space = ParticleSpace.World).validate("fx"))
    }

    @Test
    fun anAuthoredLocalEmitterCarriesItsParticlesWithItsNode() {
        val world = World()
        val node = world.node(glow.copy(maxParticles = 1, spawnRate = 1000f, lifetime = 100f, velocity = SceneVec3(0f, 0f, 0f)))
        ParticleContentSystem(NoopRenderer(), mapOf("glow.png" to sprite)).update(world, 0f)
        val particles = ParticleSystem(TransformPlacement)
        particles.update(world, 0.001f)
        val emitter = requireNotNull(world.get<ParticleEmitter>(node))
        assertEquals(ParticleSpace.Local, emitter.motion.space)

        world.get<Transform>(node)!!.position.set(4f, 0f, 0f)
        particles.update(world, 0f)

        var at = Vec3f(0f, 0f, 0f)
        emitter.forEachLiveParticle { position, _ -> at = position.copy() }
        assertEquals(4f, at.x, 1e-4f, "moved with its node")
    }

    private fun World.node(settings: SceneParticleEmitter): Entity = create().also {
        add(it, Transform())
        add(it, ParticleEmitterSource(settings))
    }
}
