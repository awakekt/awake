/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

import com.awakekt.awake.core.math.Aabb
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TURB_AXIS_COUPLING: Float = 0.7f
private const val TURB_TIME_X_COS: Float = 1.3f
private const val TURB_TIME_Y_SIN: Float = 0.8f
private const val TURB_TIME_Z_SIN: Float = 1.1f
private const val TURB_TIME_Z_COS: Float = 0.9f

/**
 * Advances every live slot in [sim] by [delta] seconds, invoking [onDeath] with the particle's
 * final coordinates immediately before resetting slots whose age reaches their lifetime.
 */
internal fun advanceSimulationSlots(
    sim: ParticleSimulation,
    delta: Float,
    onDeath: ParticleDeathListener?,
) {
    val max = sim.maxParticles
    val dragFactor = if (sim.drag > 0f) (1f - sim.drag * delta).coerceAtLeast(0f) else 1f
    for (slot in 0 until max) {
        if (sim.alive[slot]) {
            val nextAge = sim.age[slot] + delta
            sim.age[slot] = nextAge
            if (nextAge >= sim.lifetime[slot]) {
                onDeath?.onDeath(sim.posX[slot], sim.posY[slot], sim.posZ[slot])
                resetSlot(sim, slot)
            } else if (!sim.settled[slot]) {
                integrateMovingSlot(sim, slot, delta, dragFactor)
            }
        }
    }
}

private fun integrateMovingSlot(
    sim: ParticleSimulation,
    slot: Int,
    delta: Float,
    dragFactor: Float,
) {
    sim.rotation[slot] += sim.spinRate[slot] * delta
    var vx = sim.velX[slot] + sim.accelX * delta
    var vy = sim.velY[slot] + sim.accelY * delta
    var vz = sim.velZ[slot] + sim.accelZ * delta
    val turbulence = sim.turbulence
    if (turbulence != 0f) {
        val freq = sim.turbulenceFrequency
        val t = sim.elapsedTime
        val fx = sim.posX[slot] * freq
        val fy = sim.posY[slot] * freq
        val fz = sim.posZ[slot] * freq
        val flowX = sin(fy + t) * cos(fz * TURB_AXIS_COUPLING + t * TURB_TIME_X_COS)
        val flowY = sin(fz + t * TURB_TIME_Y_SIN) * cos(fx * TURB_AXIS_COUPLING + t)
        val flowZ = if (sim.plane == ParticlePlane.XZ) {
            sin(fx + t * TURB_TIME_Z_SIN) * cos(fy * TURB_AXIS_COUPLING + t * TURB_TIME_Z_COS)
        } else {
            0f
        }
        vx += flowX * turbulence * delta
        vy += flowY * turbulence * delta
        vz += flowZ * turbulence * delta
    }
    if (dragFactor != 1f) {
        vx *= dragFactor
        vy *= dragFactor
        vz *= dragFactor
    }
    val nx = sim.posX[slot] + vx * delta
    val ny = sim.posY[slot] + vy * delta
    val nz = sim.posZ[slot] + vz * delta
    sim.velX[slot] = vx
    sim.velY[slot] = vy
    sim.velZ[slot] = vz
    sim.posX[slot] = nx
    sim.posY[slot] = ny
    sim.posZ[slot] = nz
    if (sim.ground.hasAnySurface) {
        clampToGroundIfNeeded(sim, slot, nx, ny, nz)
    }
}

private fun clampToGroundIfNeeded(
    sim: ParticleSimulation,
    slot: Int,
    px: Float,
    py: Float,
    pz: Float,
) {
    val ground = sim.ground
    val groundHeight = resolveGroundHeight(ground, px, pz)
    if (groundHeight.isNaN() || py > groundHeight) return
    sim.posY[slot] = groundHeight
    val bounceVelocity = -sim.velY[slot] * ground.restitution
    if (ground.restitution > 0f && abs(bounceVelocity) >= ground.bounceStopVelocity) {
        sim.velX[slot] *= ground.friction
        sim.velY[slot] = bounceVelocity
        sim.velZ[slot] *= ground.friction
    } else {
        sim.velX[slot] = 0f
        sim.velY[slot] = 0f
        sim.velZ[slot] = 0f
        sim.settled[slot] = true
    }
}

private fun resolveGroundHeight(ground: ParticleGroundConfig, x: Float, z: Float): Float {
    val provider = ground.groundHeightProvider
    if (provider != null) return provider.heightAt(x, z)
    val colliderY = colliderTopAt(ground.colliders, x, z)
    return when {
        !colliderY.isNaN() -> colliderY
        ground.hasGroundY -> ground.groundY
        else -> Float.NaN
    }
}

private fun colliderTopAt(colliders: List<Aabb>, x: Float, z: Float): Float {
    val count = colliders.size
    for (i in 0 until count) {
        val box = colliders[i]
        val withinX = x >= box.min.x && x <= box.max.x
        val withinZ = z >= box.min.z && z <= box.max.z
        if (withinX && withinZ) return box.max.y
    }
    return Float.NaN
}

internal fun spawnScheduledSlots(sim: ParticleSimulation, deltaSeconds: Float, activeRate: Float) {
    if (sim.burstLimit >= 0 && sim.spawnedTotal >= sim.burstLimit) return
    val scheduled = sim.burstSchedule.enabled
    var allowedBurstSpawns = 0
    if (scheduled) {
        val started = scheduleBurstsStartedBy(
            sim.elapsedTime,
            sim.burstSchedule.cycleSeconds,
            sim.burstSchedule.activeSeconds,
            sim.burstSchedule.burstInterval,
        )
        val requested = (started - sim.burstsFired) * sim.burstSchedule.burstSize
        allowedBurstSpawns = if (sim.burstLimit >= 0) {
            minOf(requested, (sim.burstLimit - sim.spawnedTotal).coerceAtLeast(0))
        } else {
            requested
        }
        sim.spawnAccumulator += requested
        sim.burstsFired = started
    } else {
        sim.spawnAccumulator += activeRate * deltaSeconds
    }
    sim.spawnAccumulator = sim.spawnAccumulator.coerceAtMost(sim.maxParticles.toFloat())
    var spawnedThisStep = 0
    var searchFrom = 0
    while (sim.spawnAccumulator >= 1f && (sim.burstLimit < 0 || sim.spawnedTotal < sim.burstLimit)) {
        val slot = findFreeSimulationSlot(sim, searchFrom)
        if (slot < 0) break
        sim.spawnAccumulator -= 1f
        sim.spawnedTotal += 1
        spawnedThisStep += 1
        spawnConfiguredSlot(sim, slot)
        searchFrom = slot + 1
    }
    if (scheduled) {
        sim.droppedParticleCount += (allowedBurstSpawns - spawnedThisStep).coerceAtLeast(0).toLong()
        sim.spawnAccumulator = 0f
    }
}

internal fun findFreeSimulationSlot(sim: ParticleSimulation, startIndex: Int): Int {
    for (i in startIndex until sim.maxParticles) {
        if (!sim.alive[i]) return i
    }
    return -1
}

internal fun resetSlot(sim: ParticleSimulation, slot: Int) {
    if (sim.alive[slot]) {
        sim.alive[slot] = false
        sim.liveParticleCount -= 1
    }
    sim.posX[slot] = 0f
    sim.posY[slot] = 0f
    sim.posZ[slot] = 0f
    sim.velX[slot] = 0f
    sim.velY[slot] = 0f
    sim.velZ[slot] = 0f
    sim.age[slot] = 0f
    sim.lifetime[slot] = 0f
    sim.startAlpha[slot] = 0f
    sim.scale[slot] = 1f
    sim.settled[slot] = false
    sim.frameOffset[slot] = 0
    sim.rotation[slot] = 0f
    sim.spinRate[slot] = 0f
}
