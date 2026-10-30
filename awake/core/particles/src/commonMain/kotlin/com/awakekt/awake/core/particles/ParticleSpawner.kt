/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val CONE_PARALLEL_Y_LIMIT: Float = 0.99f
private const val TWO_SIDED_SCALE: Float = 2f
private const val PERIMETER_SIDES_FACTOR: Float = 4f
private const val EDGE_SPAN_FACTOR: Float = 2f
private const val BASIS_Y_COL: Int = 3
private const val BASIS_Z_COL: Int = 6

/**
 * Spawns one particle into [slot] using [sim]'s active shape, velocity, lifetime, frame, and spin
 * configuration, preserving the exact 3D RNG call sequence (`spawnPosition` -> `spawnVelocity` ->
 * `launchSlot`).
 */
internal fun spawnConfiguredSlot(sim: ParticleSimulation, slot: Int) {
    val slotLifetime = if (sim.maxLifetime > sim.minLifetime) {
        sim.minLifetime + sim.random.nextFloat() * (sim.maxLifetime - sim.minLifetime)
    } else {
        sim.minLifetime
    }
    sampleSpawnPosition(sim, slot)
    sampleSpawnVelocity(sim, slot)
    launchSlotMetadata(sim, slot, slotLifetime)
}

internal fun launchSlotMetadata(sim: ParticleSimulation, slot: Int, slotLifetime: Float) {
    if (!sim.alive[slot]) {
        sim.alive[slot] = true
        sim.liveParticleCount += 1
    }
    sim.age[slot] = 0f
    sim.lifetime[slot] = slotLifetime
    sim.startAlpha[slot] = sim.initialAlpha
    sim.scale[slot] = sim.initialScale
    sim.settled[slot] = false
    sim.frameOffset[slot] = sim.random.nextInt(sim.frameCount.coerceAtLeast(1))
    val spin = sim.spin
    if (!spin.enabled) {
        sim.rotation[slot] = 0f
        sim.spinRate[slot] = 0f
    } else {
        val degrees = spin.minDegreesPerSecond +
            sim.random.nextFloat() * (spin.maxDegreesPerSecond - spin.minDegreesPerSecond)
        sim.spinRate[slot] = degrees * DEGREES_TO_RADIANS
        sim.rotation[slot] = if (spin.randomStartAngle) sim.random.nextFloat() * FULL_TURN_RADIANS else 0f
    }
}

private fun sampleSpawnPosition(sim: ParticleSimulation, slot: Int) {
    val shape = sim.spawnShape
    if (shape.kind == SHAPE_POINT) {
        sim.posX[slot] = sim.originX
        sim.posY[slot] = sim.originY
        sim.posZ[slot] = sim.originZ
        return
    }
    var ox = 0f
    var oy = 0f
    var oz = 0f
    when (shape.kind) {
        SHAPE_RING_XZ -> {
            val angle = sim.random.nextFloat() * FULL_TURN_RADIANS
            ox = cos(angle) * shape.p0
            oz = sin(angle) * shape.p0
        }
        SHAPE_RING_XY -> {
            val angle = sim.random.nextFloat() * FULL_TURN_RADIANS
            val inner = shape.p0
            val outer = shape.p1
            val radius = if (inner >= outer) {
                outer
            } else {
                sqrt(inner * inner + sim.random.nextFloat() * (outer * outer - inner * inner))
            }
            ox = cos(angle) * radius
            oy = sin(angle) * radius
        }
        SHAPE_BOX -> {
            ox = jitter(sim.random, shape.p0)
            oy = jitter(sim.random, shape.p1)
            oz = if (shape.p2 > 0f) jitter(sim.random, shape.p2) else 0f
        }
        SHAPE_RECT_EDGE -> {
            sampleRectEdgeOffset(sim.random, shape.p0, shape.p1, sim, slot)
            return
        }
    }
    writeOffsetPosition(sim, slot, ox, oy, oz)
}

private fun sampleRectEdgeOffset(
    random: Random,
    halfW: Float,
    halfH: Float,
    sim: ParticleSimulation,
    slot: Int,
) {
    val perimeter = PERIMETER_SIDES_FACTOR * (halfW + halfH)
    if (perimeter <= 0f) {
        sim.posX[slot] = sim.originX
        sim.posY[slot] = sim.originY
        sim.posZ[slot] = sim.originZ
        return
    }
    val width = EDGE_SPAN_FACTOR * halfW
    val height = EDGE_SPAN_FACTOR * halfH
    val d = random.nextFloat() * perimeter
    val ox: Float
    val oy: Float
    when {
        d < width -> {
            ox = -halfW + d
            oy = -halfH
        }
        d < width + height -> {
            ox = halfW
            oy = -halfH + (d - width)
        }
        d < EDGE_SPAN_FACTOR * width + height -> {
            ox = halfW - (d - width - height)
            oy = halfH
        }
        else -> {
            ox = -halfW
            oy = halfH - (d - EDGE_SPAN_FACTOR * width - height)
        }
    }
    writeOffsetPosition(sim, slot, ox, oy, 0f)
}

private fun writeOffsetPosition(sim: ParticleSimulation, slot: Int, ox: Float, oy: Float, oz: Float) {
    if (sim.inheritOrientation) {
        val m = sim.orientation.axes
        sim.posX[slot] = sim.originX + (m[0] * ox + m[BASIS_Y_COL] * oy + m[BASIS_Z_COL] * oz)
        sim.posY[slot] = sim.originY + (m[1] * ox + m[BASIS_Y_COL + 1] * oy + m[BASIS_Z_COL + 1] * oz)
        sim.posZ[slot] = sim.originZ + (m[2] * ox + m[BASIS_Y_COL + 2] * oy + m[BASIS_Z_COL + 2] * oz)
    } else {
        sim.posX[slot] = sim.originX + ox
        sim.posY[slot] = sim.originY + oy
        sim.posZ[slot] = sim.originZ + oz
    }
}

private fun sampleSpawnVelocity(sim: ParticleSimulation, slot: Int) {
    val bx = sim.baseVelX
    val by = sim.baseVelY
    val bz = sim.baseVelZ
    val baseSpeed = sqrt(bx * bx + by * by + bz * bz)
    val speed = if (sim.maxSpeed > sim.minSpeed) {
        sim.minSpeed + sim.random.nextFloat() * (sim.maxSpeed - sim.minSpeed)
    } else if (sim.minSpeed > 0f && baseSpeed > 0f) {
        sim.minSpeed
    } else {
        baseSpeed
    }
    if (sim.convergeToOrigin) {
        writeConvergingVelocity(sim, slot, speed)
        return
    }
    if (sim.hasConeHalfAngle && speed > 0f) {
        writeConeVelocity(sim, slot, baseSpeed, speed)
    } else {
        sim.velX[slot] = bx + jitter(sim.random, sim.velocityJitter)
        sim.velY[slot] = by + jitter(sim.random, sim.velocityJitter)
        sim.velZ[slot] = if (sim.plane == ParticlePlane.XZ) {
            bz + jitter(sim.random, sim.velocityJitter)
        } else {
            0f
        }
    }
    if (sim.inheritOrientation) {
        val m = sim.orientation.axes
        val lx = sim.velX[slot]
        val ly = sim.velY[slot]
        val lz = sim.velZ[slot]
        sim.velX[slot] = m[0] * lx + m[BASIS_Y_COL] * ly + m[BASIS_Z_COL] * lz
        sim.velY[slot] = m[1] * lx + m[BASIS_Y_COL + 1] * ly + m[BASIS_Z_COL + 1] * lz
        sim.velZ[slot] = m[2] * lx + m[BASIS_Y_COL + 2] * ly + m[BASIS_Z_COL + 2] * lz
    }
    if (sim.radialSpeed != 0f) {
        applyRadialPush(sim, slot)
    }
}

private fun writeConvergingVelocity(sim: ParticleSimulation, slot: Int, speed: Float) {
    val tx = sim.originX - sim.posX[slot]
    val ty = sim.originY - sim.posY[slot]
    val tz = sim.originZ - sim.posZ[slot]
    val len = sqrt(tx * tx + ty * ty + tz * tz)
    if (len > 0f) {
        val inv = 1f / len
        sim.velX[slot] = (tx * inv) * speed
        sim.velY[slot] = (ty * inv) * speed
        sim.velZ[slot] = (tz * inv) * speed
    } else {
        sim.velX[slot] = 0f
        sim.velY[slot] = speed
        sim.velZ[slot] = 0f
    }
}

private fun writeConeVelocity(
    sim: ParticleSimulation,
    slot: Int,
    baseSpeed: Float,
    speed: Float,
) {
    val invBase = if (baseSpeed != 0f) 1f / baseSpeed else 0f
    val ax = if (baseSpeed > 0f) sim.baseVelX * invBase else 0f
    val ay = if (baseSpeed > 0f) sim.baseVelY * invBase else 1f
    val az = if (baseSpeed > 0f) sim.baseVelZ * invBase else 0f
    if (sim.plane == ParticlePlane.XY) {
        val angle = jitter(sim.random, sim.coneHalfAngleDegrees * DEGREES_TO_RADIANS)
        val c = cos(angle)
        val s = sin(angle)
        sim.velX[slot] = (ax * c - ay * s) * speed
        sim.velY[slot] = (ax * s + ay * c) * speed
        sim.velZ[slot] = 0f
        return
    }
    val fx = if (abs(ay) > CONE_PARALLEL_Y_LIMIT) 1f else 0f
    val fy = if (abs(ay) > CONE_PARALLEL_Y_LIMIT) 0f else 1f
    val fz = 0f
    val rRawX = ay * fz - az * fy
    val rRawY = az * fx - ax * fz
    val rRawZ = ax * fy - ay * fx
    val rLen = sqrt(rRawX * rRawX + rRawY * rRawY + rRawZ * rRawZ)
    val rInv = if (rLen != 0f) 1f / rLen else 0f
    val rx = if (rLen != 0f) rRawX * rInv else rRawX
    val ry = if (rLen != 0f) rRawY * rInv else rRawY
    val rz = if (rLen != 0f) rRawZ * rInv else rRawZ
    val ux = ry * az - rz * ay
    val uy = rz * ax - rx * az
    val uz = rx * ay - ry * ax
    val theta = sim.random.nextFloat() * sim.coneHalfAngleDegrees * DEGREES_TO_RADIANS
    val phi = sim.random.nextFloat() * FULL_TURN_RADIANS
    val cosTheta = cos(theta)
    val sinTheta = sin(theta)
    val sinCosPhi = sinTheta * cos(phi)
    val sinSinPhi = sinTheta * sin(phi)
    sim.velX[slot] = (ax * cosTheta + rx * sinCosPhi + ux * sinSinPhi) * speed
    sim.velY[slot] = (ay * cosTheta + ry * sinCosPhi + uy * sinSinPhi) * speed
    sim.velZ[slot] = (az * cosTheta + rz * sinCosPhi + uz * sinSinPhi) * speed
}

private fun applyRadialPush(sim: ParticleSimulation, slot: Int) {
    if (sim.plane == ParticlePlane.XY) {
        var awayX = sim.posX[slot] - sim.originX
        var awayY = sim.posY[slot] - sim.originY
        val away = sqrt(awayX * awayX + awayY * awayY)
        if (away > 0f) {
            awayX /= away
            awayY /= away
        } else {
            val angle = sim.random.nextFloat() * FULL_TURN_RADIANS
            awayX = cos(angle)
            awayY = sin(angle)
        }
        sim.velX[slot] += awayX * sim.radialSpeed
        sim.velY[slot] += awayY * sim.radialSpeed
        return
    }
    var awayX = sim.posX[slot] - sim.originX
    var awayZ = sim.posZ[slot] - sim.originZ
    val away = sqrt(awayX * awayX + awayZ * awayZ)
    if (away > 0f) {
        awayX /= away
        awayZ /= away
    } else {
        val angle = sim.random.nextFloat() * FULL_TURN_RADIANS
        awayX = cos(angle)
        awayZ = sin(angle)
    }
    sim.velX[slot] += awayX * sim.radialSpeed
    sim.velZ[slot] += awayZ * sim.radialSpeed
}

private fun jitter(random: Random, magnitude: Float): Float =
    (random.nextFloat() * TWO_SIDED_SCALE - 1f) * magnitude
