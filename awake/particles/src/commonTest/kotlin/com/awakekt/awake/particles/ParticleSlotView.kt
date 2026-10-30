/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.particles.ParticleSimulation

/**
 * Test-only view over one slot of [ParticleSimulation] so existing unit assertions on
 * `emitter.particles[i]` inspect and tweak the SoA backing arrays without retaining any wrapper
 * objects in production `commonMain`.
 *
 * Liveness only changes through the pool ([ParticleSimulation.spawn] and
 * [ParticleSimulation.clearSlot]), so [ParticleSimulation.liveParticleCount] and
 * [ParticleSimulation.isComplete] stay true to it.
 */
internal class ParticleSlotView(
    internal val sim: ParticleSimulation,
    internal val slot: Int,
) {
    var alive: Boolean
        get() = sim.isAlive(slot)
        set(value) {
            if (value == sim.isAlive(slot)) return
            if (!value) {
                sim.clearSlot(slot)
                return
            }
            // spawn fills the lowest free slot, so this one is next only when every slot before it is live.
            check((0 until slot).all { sim.isAlive(it) }) { "slot $slot is not the pool's next free slot" }
            check(sim.spawn(sim.posX[slot], sim.posY[slot], sim.posZ[slot]) && sim.isAlive(slot)) {
                "slot $slot did not come alive"
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

/** This particle's opacity now, from the production [ParticleSimulation.alphaAt] with [emitter]'s settings. */
internal fun ParticleSlotView.currentAlpha(emitter: ParticleEmitter): Float {
    syncWith(emitter)
    return sim.alphaAt(slot)
}

/** This particle's size now, from the production [ParticleSimulation.scaleAt] with [emitter]'s settings. */
internal fun ParticleSlotView.currentScale(emitter: ParticleEmitter): Float {
    syncWith(emitter)
    return sim.scaleAt(slot)
}

/** This particle's colour now, from the production colour path the draw builder packs ([writeInstanceColor]). */
internal fun ParticleSlotView.currentColor(emitter: ParticleEmitter): Vec3f {
    syncWith(emitter)
    val color = emitter.writeInstanceColor(slot, Vec4())
    return Vec3f(color.x, color.y, color.z)
}

private fun ParticleSlotView.syncWith(emitter: ParticleEmitter) {
    require(sim === emitter.simulation) { "this slot belongs to another emitter" }
    emitter.syncToSimulation()
}
