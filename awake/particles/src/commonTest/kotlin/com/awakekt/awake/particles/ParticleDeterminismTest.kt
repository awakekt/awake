/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that delegating [ParticleEmitter] and [ParticleSystem] to `ParticleSimulation` preserves
 * the pre-extraction 3D simulation state bit-for-bit (0 ULP) and consumes the exact same [Random]
 * call sequence across cone spread, ring spawn, orientation inheritance, radial push, convergence,
 * jitter, burst cycles, frame offsets, spin, turbulence, acceleration, and ground bounce.
 */
class ParticleDeterminismTest {

    @Test
    fun coneRingOrientationSpinTurbulenceAndBounceMatchReferenceBitForBit() {
        val rotation = Mat4().apply {
            val c = cos(0.4f)
            val s = sin(0.4f)
            m00 = c * 2f
            m10 = s * 2f
            m01 = -s * 2f
            m11 = c * 2f
            m22 = 2f
        }
        assertReferenceParity(
            seed = 42L,
            steps = 120,
            delta = 1f / 60f,
            placed = Placed(position = Vec3f(1f, 3f, -2f), worldMatrix = rotation),
            buildEmitter = {
                ParticleEmitter(
                    mesh = stubMesh(),
                    material = stubMaterial(),
                    origin = Vec3f(1f, 3f, -2f),
                    maxParticles = 48,
                    spawnRate = 45f,
                    lifetime = 0.8f,
                    startAlpha = 0.9f,
                    scale = 0.5f,
                    motion = ParticleMotion(
                        baseVelocity = Vec3f(0.5f, 4f, -0.3f),
                        coneHalfAngleDegrees = 35f,
                        spawnRadius = 1.5f,
                        turbulence = 2.5f,
                        turbulenceFrequency = 1.2f,
                        radialSpeed = 1.25f,
                        acceleration = Vec3f(0.2f, -9.8f, -0.1f),
                        inheritOrientation = true,
                    ),
                    visual = ParticleVisual(
                        frameCount = 6,
                        frameRate = 10f,
                        endScale = 1.2f,
                        alphaCurve = ParticleAlphaCurve(fadeInEnd = 0.15f, fadeOutStart = 0.7f),
                        spin = ParticleSpin(minDegreesPerSecond = -120f, maxDegreesPerSecond = 180f, randomStartAngle = true),
                    ),
                    ground = ParticleGround(groundY = 0f, restitution = 0.45f, friction = 0.8f),
                )
            },
        )
    }

    @Test
    fun convergeToOriginAndBurstCycleJitterMatchReferenceBitForBit() {
        assertReferenceParity(
            seed = 1337L,
            steps = 90,
            delta = 1f / 60f,
            placed = null,
            buildEmitter = {
                ParticleEmitter(
                    mesh = stubMesh(),
                    material = stubMaterial(),
                    origin = Vec3f(-2f, 1f, 4f),
                    maxParticles = 32,
                    spawnRate = 60f,
                    lifetime = 0.6f,
                    startAlpha = 1f,
                    scale = 0.35f,
                    motion = ParticleMotion(
                        baseVelocity = Vec3f(0f, 3.5f, 0f),
                        spawnRadius = 2.5f,
                        convergeToOrigin = true,
                    ),
                )
            },
        )
        assertReferenceParity(
            seed = 2026L,
            steps = 90,
            delta = 0.05f,
            placed = null,
            buildEmitter = {
                ParticleEmitter(
                    mesh = stubMesh(),
                    material = stubMaterial(),
                    origin = Vec3f(0f, 0f, 0f),
                    maxParticles = 24,
                    spawnRate = 0f,
                    lifetime = 0.75f,
                    startAlpha = 0.75f,
                    scale = 0.5f,
                    motion = ParticleMotion(
                        baseVelocity = Vec3f(1f, 2f, -0.5f),
                        velocityJitter = 0.8f,
                        radialSpeed = 0.6f,
                    ),
                    visual = ParticleVisual(frameCount = 4, spin = ParticleSpin(-45f, 45f, randomStartAngle = false)),
                    lifecycle = ParticleLifecycle(
                        burstCount = 18,
                        burstCycle = ParticleBurstCycle(
                            cycleSeconds = 1f,
                            activeSeconds = 0.5f,
                            burstInterval = 0.2f,
                            burstSize = 4,
                        ),
                    ),
                )
            },
        )
    }

    @Test
    fun emitOnParticleEmitterSpawnsBurstAndTracksDroppedOverflow() {
        val emitter = ParticleEmitter(
            mesh = stubMesh(),
            material = stubMaterial(),
            origin = Vec3f(2f, 4f, -1f),
            maxParticles = 5,
            spawnRate = 0f,
            lifetime = 2f,
            startAlpha = 0.8f,
            scale = 0.5f,
            lifecycle = ParticleLifecycle(burstCount = 3),
        )
        emitter.simulation.random = Random(77)
        assertEquals(4, emitter.emit(4))
        assertEquals(4, emitter.liveParticleCount)
        assertEquals(0, emitter.spawnedTotal, "emit(count) does not consume lifecycle.burstCount")
        assertEquals(1, emitter.emit(3))
        assertEquals(5, emitter.liveParticleCount)
        assertEquals(2L, emitter.droppedParticleCount)
    }

    private fun assertReferenceParity(
        seed: Long,
        steps: Int,
        delta: Float,
        placed: Placed?,
        buildEmitter: () -> ParticleEmitter,
    ) {
        val actualEmitter = buildEmitter()
        val actualRandom = Random(seed)
        actualEmitter.simulation.random = actualRandom
        val world = World()
        val entity = world.create()
        world.add(entity, actualEmitter)
        if (placed != null) world.add(entity, placed)
        val system = ParticleSystem(PlacedEntities)

        val refEmitter = buildEmitter()
        val refRandom = Random(seed)
        val refState = ReferenceEmitterState(refEmitter, refRandom)

        repeat(steps) { stepIndex ->
            system.update(world, delta)
            refState.step(delta, placed?.worldMatrix)
            val sim = actualEmitter.simulation
            assertEquals(refState.spawnedTotal, sim.spawnedTotal, "spawnedTotal mismatch at step $stepIndex")
            assertEquals(refState.liveCount(), sim.liveParticleCount, "liveParticleCount mismatch at step $stepIndex")
            for (slot in 0 until sim.maxParticles) {
                val ref = refState.slots[slot]
                assertEquals(ref.alive, sim.alive[slot], "alive[$slot] mismatch at step $stepIndex")
                assertEquals(ref.pos.x, sim.posX[slot], "posX[$slot] mismatch at step $stepIndex")
                assertEquals(ref.pos.y, sim.posY[slot], "posY[$slot] mismatch at step $stepIndex")
                assertEquals(ref.pos.z, sim.posZ[slot], "posZ[$slot] mismatch at step $stepIndex")
                assertEquals(ref.vel.x, sim.velX[slot], "velX[$slot] mismatch at step $stepIndex")
                assertEquals(ref.vel.y, sim.velY[slot], "velY[$slot] mismatch at step $stepIndex")
                assertEquals(ref.vel.z, sim.velZ[slot], "velZ[$slot] mismatch at step $stepIndex")
                assertEquals(ref.age, sim.age[slot], "age[$slot] mismatch at step $stepIndex")
                assertEquals(ref.lifetime, sim.lifetime[slot], "lifetime[$slot] mismatch at step $stepIndex")
                assertEquals(ref.settled, sim.settled[slot], "settled[$slot] mismatch at step $stepIndex")
                assertEquals(ref.frameOffset, sim.frameOffset[slot], "frameOffset[$slot] mismatch at step $stepIndex")
                assertEquals(ref.rotation, sim.rotation[slot], "rotation[$slot] mismatch at step $stepIndex")
                assertEquals(ref.spinRate, sim.spinRate[slot], "spinRate[$slot] mismatch at step $stepIndex")
            }
        }
        assertEquals(refRandom.nextInt(), actualRandom.nextInt(), "RNG stream diverged after $steps steps")
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
}

private class ReferenceSlot {
    var alive = false
    val pos = Vec3f(0f, 0f, 0f)
    val vel = Vec3f(0f, 0f, 0f)
    var age = 0f
    var lifetime = 0f
    var settled = false
    var frameOffset = 0
    var rotation = 0f
    var spinRate = 0f

    fun reset() {
        alive = false
        pos.set(0f, 0f, 0f)
        vel.set(0f, 0f, 0f)
        age = 0f
        lifetime = 0f
        settled = false
        frameOffset = 0
        rotation = 0f
        spinRate = 0f
    }
}

private class ReferenceEmitterState(
    private val emitter: ParticleEmitter,
    private val random: Random,
) {
    val slots = Array(emitter.maxParticles) { ReferenceSlot() }
    private val orientation = FloatArray(9)
    var elapsedTime = 0f
    var spawnAccumulator = 0f
    var spawnedTotal = 0
    var burstsFired = 0

    fun liveCount(): Int = slots.count { it.alive }

    fun step(delta: Float, matrix: Mat4?) {
        loadOrientation(matrix)
        elapsedTime += delta
        spawn(delta)
        advance(delta)
    }

    private fun spawn(delta: Float) {
        val burstCount = emitter.lifecycle.burstCount
        if (burstCount != null && spawnedTotal >= burstCount) return
        val burstCycle = emitter.lifecycle.burstCycle
        if (burstCycle != null) {
            val burstsPerCycle = ceil(burstCycle.activeSeconds / burstCycle.burstInterval - 1e-4f).toInt().coerceAtLeast(1)
            val started = if (elapsedTime <= 0f) {
                0
            } else {
                val complete = floor(elapsedTime / burstCycle.cycleSeconds).toInt()
                val phase = elapsedTime - complete * burstCycle.cycleSeconds
                complete * burstsPerCycle + (floor(phase / burstCycle.burstInterval).toInt() + 1).coerceAtMost(burstsPerCycle)
            }
            spawnAccumulator += (started - burstsFired) * burstCycle.burstSize
            burstsFired = started
        } else {
            spawnAccumulator += emitter.spawnRate * delta
        }
        spawnAccumulator = spawnAccumulator.coerceAtMost(emitter.maxParticles.toFloat())
        while (spawnAccumulator >= 1f && (burstCount == null || spawnedTotal < burstCount)) {
            val slot = slots.firstOrNull { !it.alive } ?: break
            spawnAccumulator -= 1f
            spawnedTotal += 1
            val spawnPos = spawnPosition()
            launch(slot, spawnPos, spawnVelocity(spawnPos))
        }
        if (burstCycle != null) spawnAccumulator = 0f
    }

    private fun launch(slot: ReferenceSlot, position: Vec3f, velocity: Vec3f) {
        slot.alive = true
        slot.pos.set(position.x, position.y, position.z)
        slot.vel.set(velocity.x, velocity.y, velocity.z)
        slot.age = 0f
        slot.lifetime = emitter.lifetime
        slot.settled = false
        slot.frameOffset = random.nextInt(emitter.visual.frameCount.coerceAtLeast(1))
        val spin = emitter.visual.spin
        if (spin == null) {
            slot.rotation = 0f
            slot.spinRate = 0f
        } else {
            val deg = spin.minDegreesPerSecond + random.nextFloat() * (spin.maxDegreesPerSecond - spin.minDegreesPerSecond)
            slot.spinRate = deg * ((kotlin.math.PI / 180.0).toFloat())
            slot.rotation = if (spin.randomStartAngle) random.nextFloat() * ((2.0 * kotlin.math.PI).toFloat()) else 0f
        }
    }

    private fun spawnPosition(): Vec3f {
        val radius = emitter.motion.spawnRadius
        if (radius <= 0f) return emitter.origin
        val angle = random.nextFloat() * ((2.0 * kotlin.math.PI).toFloat())
        val offset = Vec3f(cos(angle) * radius, 0f, sin(angle) * radius)
        if (emitter.motion.inheritOrientation) turn(offset)
        return Vec3f(emitter.origin.x + offset.x, emitter.origin.y + offset.y, emitter.origin.z + offset.z)
    }

    private fun spawnVelocity(spawnPos: Vec3f): Vec3f {
        val motion = emitter.motion
        if (motion.convergeToOrigin) {
            val toOrigin = emitter.origin - spawnPos
            val dir = if (toOrigin.length3() > 0f) toOrigin.normalized() else Vec3f(0f, 1f, 0f)
            return dir * motion.baseVelocity.length3()
        }
        val cone = motion.coneHalfAngleDegrees
        val speed = motion.baseVelocity.length3()
        val vel = if (cone != null && speed > 0f) {
            coneDirection(motion.baseVelocity.normalized(), cone) * speed
        } else {
            Vec3f(
                motion.baseVelocity.x + (random.nextFloat() * 2f - 1f) * motion.velocityJitter,
                motion.baseVelocity.y + (random.nextFloat() * 2f - 1f) * motion.velocityJitter,
                motion.baseVelocity.z + (random.nextFloat() * 2f - 1f) * motion.velocityJitter,
            )
        }
        if (motion.inheritOrientation) turn(vel)
        if (motion.radialSpeed != 0f) {
            var awayX = spawnPos.x - emitter.origin.x
            var awayZ = spawnPos.z - emitter.origin.z
            val away = sqrt(awayX * awayX + awayZ * awayZ)
            if (away > 0f) {
                awayX /= away
                awayZ /= away
            } else {
                val angle = random.nextFloat() * ((2.0 * kotlin.math.PI).toFloat())
                awayX = cos(angle)
                awayZ = sin(angle)
            }
            vel.set(vel.x + awayX * motion.radialSpeed, vel.y, vel.z + awayZ * motion.radialSpeed)
        }
        return vel
    }

    private fun coneDirection(axis: Vec3f, halfAngleDegrees: Float): Vec3f {
        val fallback = if (abs(axis.y) > 0.99f) Vec3f(1f, 0f, 0f) else Vec3f(0f, 1f, 0f)
        val right = axis.cross(fallback).normalized()
        val up = right.cross(axis)
        val theta = random.nextFloat() * halfAngleDegrees * ((kotlin.math.PI / 180.0).toFloat())
        val phi = random.nextFloat() * ((2.0 * kotlin.math.PI).toFloat())
        return (axis * cos(theta)) + (right * (sin(theta) * cos(phi))) + (up * (sin(theta) * sin(phi)))
    }

    private fun advance(delta: Float) {
        for (slot in slots) {
            stepReferenceSlot(slot, emitter, elapsedTime, delta)
        }
    }

    private fun loadOrientation(placed: Mat4?) {
        if (placed == null) {
            orientation.fill(0f)
            orientation[0] = 1f
            orientation[4] = 1f
            orientation[8] = 1f
            return
        }
        writeAxis(0, placed.m00, placed.m10, placed.m20)
        writeAxis(3, placed.m01, placed.m11, placed.m21)
        writeAxis(6, placed.m02, placed.m12, placed.m22)
    }

    private fun writeAxis(offset: Int, x: Float, y: Float, z: Float) {
        val len = sqrt(x * x + y * y + z * z)
        val unit = if (len > 0f) 1f / len else 0f
        orientation[offset] = x * unit
        orientation[offset + 1] = y * unit
        orientation[offset + 2] = z * unit
        if (len == 0f) orientation[offset + offset / 3] = 1f
    }

    private fun turn(v: Vec3f) {
        val lx = v.x
        val ly = v.y
        val lz = v.z
        v.set(
            orientation[0] * lx + orientation[3] * ly + orientation[6] * lz,
            orientation[1] * lx + orientation[4] * ly + orientation[7] * lz,
            orientation[2] * lx + orientation[5] * ly + orientation[8] * lz,
        )
    }
}

private fun stepReferenceSlot(
    slot: ReferenceSlot,
    emitter: ParticleEmitter,
    elapsedTime: Float,
    delta: Float,
) {
    if (!slot.alive) return
    slot.age += delta
    if (slot.age >= slot.lifetime) {
        slot.reset()
    } else if (!slot.settled) {
        integrateReferenceMotion(slot, emitter, elapsedTime, delta)
    }
}

private fun integrateReferenceMotion(
    slot: ReferenceSlot,
    emitter: ParticleEmitter,
    elapsedTime: Float,
    delta: Float,
) {
    slot.rotation += slot.spinRate * delta
    val acc = emitter.motion.acceleration
    slot.vel.x += acc.x * delta
    slot.vel.y += acc.y * delta
    slot.vel.z += acc.z * delta
    val turb = emitter.motion.turbulence
    if (turb != 0f) {
        val freq = emitter.motion.turbulenceFrequency
        val x = slot.pos.x * freq
        val y = slot.pos.y * freq
        val z = slot.pos.z * freq
        slot.vel.x += (sin(y + elapsedTime) * cos(z * 0.7f + elapsedTime * 1.3f)) * turb * delta
        slot.vel.y += (sin(z + elapsedTime * 0.8f) * cos(x * 0.7f + elapsedTime)) * turb * delta
        slot.vel.z += (sin(x + elapsedTime * 1.1f) * cos(y * 0.7f + elapsedTime * 0.9f)) * turb * delta
    }
    slot.pos.x += slot.vel.x * delta
    slot.pos.y += slot.vel.y * delta
    slot.pos.z += slot.vel.z * delta
    val groundY = emitter.ground.groundY ?: return
    if (slot.pos.y <= groundY) {
        slot.pos.y = groundY
        val bounce = -slot.vel.y * emitter.ground.restitution
        if (emitter.ground.restitution > 0f && abs(bounce) >= BOUNCE_STOP_VELOCITY) {
            slot.vel.set(slot.vel.x * emitter.ground.friction, bounce, slot.vel.z * emitter.ground.friction)
        } else {
            slot.vel.set(0f, 0f, 0f)
            slot.settled = true
        }
    }
}
