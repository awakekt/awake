/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRingBuffer
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [ParticleSystem] built without a placement turns following and orientation off without a word, so
 * it says so once. It must stay quiet when the omission is a choice or changes nothing.
 */
@Suppress("DEPRECATION")
class ParticleSystemPlacementWarningTest {
    private fun emitter(
        follows: Entity? = null,
        inherits: Boolean = false,
        children: List<ParticleEmitter> = emptyList(),
    ) = ParticleEmitter(
        mesh = fakeMesh(),
        material = fakeMaterial(),
        origin = Vec3f(0f, 0f, 0f),
        maxParticles = 1,
        spawnRate = 0f,
        lifetime = 1f,
        startAlpha = 1f,
        scale = 1f,
        motion = ParticleMotion(inheritOrientation = inherits),
        dynamics = ParticleDynamics(followEntity = follows),
        children = children,
    )

    /** The warnings [ParticleSystem] logged while [block] ran, however many frames it ran. */
    private fun warningsFrom(block: () -> Unit): List<String> {
        val sink = LogRingBuffer(capacity = 16)
        Log.install(sink)
        try {
            block()
        } finally {
            Log.remove(sink)
        }
        return sink.snapshot().filter { it.level == LogLevel.Warn && it.tag == "particles" }.map { it.message }
    }

    /** Runs [system] for [frames] frames over a world holding [emitters], each built with a followed entity from it when asked. */
    private fun runFrames(system: ParticleSystem, frames: Int = 3, build: (followed: Entity) -> List<ParticleEmitter>) {
        val world = World()
        val followed = world.create()
        build(followed).forEach { world.add(world.create(), it) }
        repeat(frames) { system.update(world, 0.016f) }
    }

    @Test
    fun aFollowingEmitterUnderASystemWithNoPlacementWarnsOnceHoweverManyFramesOrEmitters() {
        val warnings = warningsFrom {
            runFrames(ParticleSystem(), frames = 5) { target -> listOf(emitter(follows = target), emitter(follows = target)) }
        }

        assertEquals(1, warnings.size, "one warning per system, not one per frame or per emitter: $warnings")
        assertTrue("EmitterPlacement" in warnings.single(), "it names what to pass: ${warnings.single()}")
    }

    @Test
    fun anEmitterThatInheritsOrientationWarnsToo() {
        val warnings = warningsFrom { runFrames(ParticleSystem()) { listOf(emitter(inherits = true)) } }

        assertEquals(1, warnings.size)
    }

    @Test
    fun aChildThatNeedsAPlacementWarnsForItsParent() {
        val warnings = warningsFrom { runFrames(ParticleSystem()) { listOf(emitter(children = listOf(emitter(inherits = true)))) } }

        assertEquals(1, warnings.size, "children are turned and followed with their parent")
    }

    @Test
    fun eachSystemReportsItsOwnOmission() {
        val warnings = warningsFrom {
            runFrames(ParticleSystem()) { target -> listOf(emitter(follows = target)) }
            runFrames(ParticleSystem()) { target -> listOf(emitter(follows = target)) }
        }

        assertEquals(2, warnings.size)
    }

    @Test
    fun aSystemWithARealPlacementStaysQuiet() {
        val warnings = warningsFrom { runFrames(ParticleSystem(PlacedEntities)) { target -> listOf(emitter(follows = target, inherits = true)) } }

        assertTrue(warnings.isEmpty(), "$warnings")
    }

    @Test
    fun choosingNoPlacementOnPurposeStaysQuiet() {
        val warnings = warningsFrom { runFrames(ParticleSystem(EmitterPlacement.None)) { target -> listOf(emitter(follows = target, inherits = true)) } }

        assertTrue(warnings.isEmpty(), "an explicit None is a decision, not an omission: $warnings")
    }

    @Test
    fun aSystemWithNoPlacementStaysQuietWhenNoEmitterNeedsOne() {
        val warnings = warningsFrom { runFrames(ParticleSystem()) { listOf(emitter()) } }

        assertTrue(warnings.isEmpty(), "nothing is lost, so nothing to say: $warnings")
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionUv
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
