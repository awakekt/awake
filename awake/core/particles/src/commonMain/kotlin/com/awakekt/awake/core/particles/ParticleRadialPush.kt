/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val AXIS_Y: Int = 3
private const val AXIS_Z: Int = 6

/**
 * Adds [ParticleSimulation.radialSpeed] to [slot]'s velocity, away from the origin through its spawn
 * point: in the XY plane for a 2D simulation, horizontally in XZ for a 3D one, or in the turned spawn
 * ring's own plane when [ParticleSimulation.radialInSpawnPlane] is set and the ring is turned.
 */
internal fun applyRadialPush(sim: ParticleSimulation, slot: Int) {
    when {
        sim.plane == ParticlePlane.XY -> pushInXY(sim, slot)
        sim.radialInSpawnPlane && (sim.inheritOrientation || sim.orientSpawnShape) -> pushInSpawnPlane(sim, slot)
        else -> pushInXZ(sim, slot)
    }
}

private fun pushInXY(sim: ParticleSimulation, slot: Int) {
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
}

private fun pushInXZ(sim: ParticleSimulation, slot: Int) {
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

// Away from the origin in the plane spanned by the turned X and Z axes: the offset with its part along
// the turned Y axis (the ring's normal) removed, or a random direction in that plane at the origin.
private fun pushInSpawnPlane(sim: ParticleSimulation, slot: Int) {
    val m = sim.orientation.axes
    val nx = m[AXIS_Y]
    val ny = m[AXIS_Y + 1]
    val nz = m[AXIS_Y + 2]
    var awayX = sim.posX[slot] - sim.originX
    var awayY = sim.posY[slot] - sim.originY
    var awayZ = sim.posZ[slot] - sim.originZ
    val along = awayX * nx + awayY * ny + awayZ * nz
    awayX -= along * nx
    awayY -= along * ny
    awayZ -= along * nz
    val away = sqrt(awayX * awayX + awayY * awayY + awayZ * awayZ)
    if (away > 0f) {
        awayX /= away
        awayY /= away
        awayZ /= away
    } else {
        val angle = sim.random.nextFloat() * FULL_TURN_RADIANS
        val c = cos(angle)
        val s = sin(angle)
        awayX = m[0] * c + m[AXIS_Z] * s
        awayY = m[1] * c + m[AXIS_Z + 1] * s
        awayZ = m[2] * c + m[AXIS_Z + 2] * s
    }
    sim.velX[slot] += awayX * sim.radialSpeed
    sim.velY[slot] += awayY * sim.radialSpeed
    sim.velZ[slot] += awayZ * sim.radialSpeed
}
