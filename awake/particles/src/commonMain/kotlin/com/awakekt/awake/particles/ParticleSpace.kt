/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

/**
 * Where an emitter keeps its particles once they have spawned, set as [ParticleMotion.space].
 *
 * The names of these constants are what a scene file writes, so they are part of its format.
 */
enum class ParticleSpace {
    /**
     * Each particle stays where it was emitted in the world, wherever its emitter moves next: smoke left
     * behind a moving torch. The default.
     */
    World,

    /**
     * Particles are kept relative to the emitter's entity and drawn through its frame every frame, so they
     * move, turn and scale with it: a glow that travels with a moving hand. The frame is the entity's
     * [EmitterPlacement.position] and the axes of its [EmitterPlacement.orientation], scale included.
     *
     * [ParticleMotion.baseVelocity], the spawn ring and every other spawn vector are in the entity's axes,
     * so [ParticleMotion.inheritOrientation] and [ParticleMotion.orientSpawnRing] have nothing left to
     * add. [ParticleMotion.acceleration] stays a world-space acceleration, so gravity still pulls down
     * however the entity is turned. A [ParticleGround] does not apply: its planes and boxes are in the
     * world, which local particles do not keep to. Camera-facing quads still face the camera.
     */
    Local,
}

/** Whether this emitter or any emitter under it keeps its particles in [ParticleSpace.Local]. */
internal fun ParticleEmitter.keepsAnyLocal(): Boolean {
    var local = motion.space == ParticleSpace.Local
    var i = 0
    while (!local && i < children.size) {
        local = children[i].keepsAnyLocal()
        i += 1
    }
    return local
}
