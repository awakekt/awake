/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.particles.ParticleSimulation

/**
 * Test-only view over one slot of [ParticleSimulation] so existing unit assertions on
 * `emitter.particles[i]` inspect and tweak the SoA backing arrays without retaining any wrapper
 * objects in production `commonMain`.
 */
internal class ParticleSlotView(
    internal val sim: ParticleSimulation,
    internal val slot: Int,
) {
    var alive: Boolean
        get() = sim.alive[slot]
        set(value) {
            if (!value) {
                sim.clearSlot(slot)
            } else {
                sim.alive[slot] = true
            }
        }

    val position: Vec3f
        get() = Vec3f(sim.posX[slot], sim.posY[slot], sim.posZ[slot])

    val velocity: Vec3f
        get() = Vec3f(sim.velX[slot], sim.velY[slot], sim.velZ[slot])

    var age: Float
        get() = sim.age[slot]
        set(value) {
            sim.age[slot] = value
        }

    var lifetime: Float
        get() = sim.lifetime[slot]
        set(value) {
            sim.lifetime[slot] = value
        }

    var startAlpha: Float
        get() = sim.startAlpha[slot]
        set(value) {
            sim.startAlpha[slot] = value
        }

    var scale: Float
        get() = sim.scale[slot]
        set(value) {
            sim.scale[slot] = value
        }

    var settled: Boolean
        get() = sim.settled[slot]
        set(value) {
            sim.settled[slot] = value
        }

    var frameOffset: Int
        get() = sim.frameOffset[slot]
        set(value) {
            sim.frameOffset[slot] = value
        }

    var rotation: Float
        get() = sim.rotation[slot]
        set(value) {
            sim.rotation[slot] = value
        }

    var spinRate: Float
        get() = sim.spinRate[slot]
        set(value) {
            sim.spinRate[slot] = value
        }

    fun reset() {
        sim.clearSlot(slot)
    }
}

internal val ParticleEmitter.particles: List<ParticleSlotView>
    get() = List(maxParticles) { ParticleSlotView(simulation, it) }

internal fun ParticleSlotView.currentAlpha(): Float =
    startAlpha * (1f - (age / lifetime).coerceIn(0f, 1f))

internal fun ParticleSlotView.currentAlpha(emitter: ParticleEmitter): Float {
    val curve = emitter.visual.alphaCurve ?: return currentAlpha()
    return startAlpha * curve.factorAt(age / lifetime)
}

internal fun ParticleSlotView.currentScale(emitter: ParticleEmitter): Float {
    val endScale = emitter.visual.endScale ?: return scale
    return scale + (endScale - scale) * (age / lifetime).coerceIn(0f, 1f)
}

internal fun ParticleSlotView.currentColor(emitter: ParticleEmitter): Vec3f {
    val t = (age / lifetime).coerceIn(0f, 1f)
    val visual = emitter.visual
    return Vec3f(
        visual.startColor.x + (visual.endColor.x - visual.startColor.x) * t,
        visual.startColor.y + (visual.endColor.y - visual.startColor.y) * t,
        visual.startColor.z + (visual.endColor.z - visual.startColor.z) * t,
    )
}
