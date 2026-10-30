/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class Particle3DAllocationTest {
    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun particleSystemStepAndBurstEmitAllocateZeroBytesPerFrame() {
        val world = World()
        val emitter = ParticleEmitter(
            mesh = stubMesh(),
            material = stubMaterial(),
            origin = Vec3f(0f, 1f, 0f),
            maxParticles = 256,
            spawnRate = 180f,
            lifetime = 0.6f,
            startAlpha = 1f,
            scale = 0.4f,
            motion = ParticleMotion(
                baseVelocity = Vec3f(0f, 3f, 0f),
                coneHalfAngleDegrees = 30f,
                spawnRadius = 1f,
                turbulence = 1.5f,
                acceleration = Vec3f(0f, -9.8f, 0f),
            ),
            visual = ParticleVisual(
                startColor = Vec3f(1f, 0.7f, 0.2f),
                endColor = Vec3f(1f, 0.1f, 0f),
                frameCount = 4,
                endScale = 0.9f,
                alphaCurve = ParticleAlphaCurve(fadeInEnd = 0.1f, fadeOutStart = 0.7f),
                spin = ParticleSpin(-90f, 90f, randomStartAngle = true),
            ),
            ground = ParticleGround(groundY = 0f, restitution = 0.3f, friction = 0.8f),
        )
        emitter.simulation.random = Random(123)
        world.add(world.create(), emitter)
        val system = ParticleSystem(EmitterPlacement.None)

        repeat(WARMUP_FRAMES) {
            emitter.emit(8)
            system.update(world, FRAME_DELTA)
        }

        val before = threadBean.currentThreadAllocatedBytes
        repeat(MEASURED_FRAMES) {
            emitter.emit(8)
            system.update(world, FRAME_DELTA)
        }
        val after = threadBean.currentThreadAllocatedBytes

        val bytesPerFrame = (after - before) / MEASURED_FRAMES
        assertEquals(
            0L,
            bytesPerFrame,
            "Steady-state ParticleEmitter.emit() + ParticleSystem.update() must allocate 0 bytes per frame",
        )
    }

    private fun stubMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionUv
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private fun stubMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val WARMUP_FRAMES = 2_000
        const val MEASURED_FRAMES = 600
        const val FRAME_DELTA = 1f / 60f
    }
}
