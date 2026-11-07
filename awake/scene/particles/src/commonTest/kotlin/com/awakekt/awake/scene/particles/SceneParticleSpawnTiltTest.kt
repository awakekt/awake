/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.particles

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneVec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A scene file's `orientSpawnRing` on a `particle_emitter`: read, written and carried to the live emitter. */
class SceneParticleSpawnTiltTest {
    init {
        SceneComponentRegistry.registerGlobal(ParticleEmitterBinding)
    }

    private val sprite = TextureAsset(ByteArray(4) { -1 }, width = 1, height = 1)
    private val circle = SceneParticleEmitter(texture = "circle.png", spawnRadius = 2f, orientSpawnRing = true)

    private fun decode(component: String) = SceneLoader.decode("""{"version": 1, "nodes": [{"components": [$component]}]}""")
        .nodes.single().components.single() as SceneParticleEmitter

    @Test
    fun orientSpawnRingRoundTripsThroughTheDocument() {
        val decoded = decode("""{"component": "particle_emitter", "texture": "circle.png", "spawnRadius": 2, "orientSpawnRing": true}""")
        val encoded = SceneLoader.encode(SceneDocument(nodes = listOf(SceneNode("fx", components = listOf(decoded)))))

        assertEquals(circle, decoded)
        assertEquals(circle, SceneLoader.decode(encoded).nodes.single().components.single())
    }

    @Test
    fun leftOutItIsOff() {
        assertFalse(decode("""{"component": "particle_emitter", "texture": "dust.png"}""").orientSpawnRing)
    }

    @Test
    fun anAuthoredTiltedRingLiesInItsTurnedNodesPlane() {
        val world = World()
        val node = world.create()
        // A quarter turn about Z: the node's XZ ring plane is the world's YZ plane.
        world.add(
            node,
            Transform(rotation = Vec3f(0f, 0f, (PI / 2).toFloat())).apply { computeLocalMatrix() },
        )
        val settings = circle.copy(maxParticles = 16, spawnRate = 16_000f, lifetime = 100f, velocity = SceneVec3(0f, 0f, 0f))
        world.add(node, ParticleEmitterSource(settings))
        ParticleContentSystem(NoopRenderer(), mapOf("circle.png" to sprite)).update(world, 0f)
        val emitter = requireNotNull(world.get<ParticleEmitter>(node))
        assertTrue(emitter.motion.orientSpawnRing, "carried to the live emitter")

        ParticleSystem(TransformPlacement).update(world, 0.001f)

        val spawned = ArrayList<Vec3f>()
        emitter.forEachLiveParticle { position, _ -> spawned += position.copy() }
        assertTrue(spawned.size > 8, "the step spawned a ring")
        assertTrue(spawned.all { abs(it.x) < 1e-3f }, "every spawn point is in the world's YZ plane")
    }
}
