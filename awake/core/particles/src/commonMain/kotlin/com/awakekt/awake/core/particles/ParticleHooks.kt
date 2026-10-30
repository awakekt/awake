/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

/**
 * Told where each particle dies. [ParticleSimulation.step] calls [onDeath] once for every particle
 * whose age reached its lifetime, just before its slot is cleared.
 *
 * A `fun interface` rather than a Kotlin function type, so the three coordinates travel as
 * primitives and a death allocates nothing. A lambda converts to it:
 * `sim.step(delta) { x, y, z -> spawnImpactAt(x, y, z) }`.
 */
fun interface ParticleDeathListener {
    /** Called with the dying particle's last position, in the simulation's units. */
    fun onDeath(x: Float, y: Float, z: Float)
}

/**
 * Asked for an emitter's spawn rate, in particles per second, once a frame in place of its fixed
 * rate, as the 2D and canvas emitters' `dynamicSpawnRate`. The result becomes the
 * `effectiveSpawnRate` passed to [ParticleSimulation.step]. A `fun interface` rather than
 * `() -> Float`, so the rate comes back as a primitive and asking allocates nothing.
 */
fun interface ParticleSpawnRate {
    /** The spawn rate for this frame, in particles per second. */
    fun particlesPerSecond(): Float
}

/**
 * The height of the ground under a point, set as [ParticleGroundConfig.groundHeightProvider].
 * [ParticleSimulation.step] asks it once per moving particle per step, so it is a `fun interface`
 * rather than a Kotlin function type: the query and its answer stay primitive and allocate nothing.
 */
fun interface ParticleGroundHeight {
    /**
     * The ground's height (the `Y` a falling particle lands on) at `(x, z)`, or `Float.NaN` where
     * there is no ground, so a particle there keeps falling.
     */
    fun heightAt(x: Float, z: Float): Float
}
