/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/**
 * Where an emitter's entity is in the world: what [ParticleSystem] needs to make an emitter follow an
 * entity ([ParticleDynamics.followEntity]) and to turn a spawn by the entity's rotation
 * ([ParticleMotion.inheritOrientation]).
 *
 * The particle library does not know what places an entity. That may be a scene graph's transform, a
 * physics body, or a position a game keeps in a component of its own; an app supplies the one it has.
 * `awake:scene:particles` supplies one for the scene's `Transform`. With [None] no emitter follows
 * anything and none is turned.
 */
interface EmitterPlacement {
    /**
     * Writes the world position of [entity] into [into] and returns true, or returns false and leaves
     * [into] alone when it has none.
     */
    fun position(world: World, entity: Entity, into: Vec3f): Boolean

    /**
     * [entity]'s world matrix, whose upper 3 x 3 holds its axes (any scale in them is ignored), or null
     * when it has none.
     */
    fun orientation(world: World, entity: Entity): Mat4?

    /** Places nothing: no emitter follows an entity and none is turned by one. */
    object None : EmitterPlacement {
        override fun position(world: World, entity: Entity, into: Vec3f): Boolean = false

        override fun orientation(world: World, entity: Entity): Mat4? = null
    }
}
