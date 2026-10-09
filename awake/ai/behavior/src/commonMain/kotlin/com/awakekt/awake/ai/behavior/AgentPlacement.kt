/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/**
 * Where an agent is, and how it moves: what [ChaseAiSystem], [FleeAiSystem] and [PatrolAiSystem]
 * need to read an entity's position and to step it along its route.
 *
 * The behaviours do not know what places an entity. That may be a scene graph's transform, a
 * physics body, or a position a game keeps in a component of its own; an app supplies the one it
 * has. `awake:scene:ai` supplies one for the scene's `Transform`.
 */
interface AgentPlacement {
    /**
     * Writes the world position of [entity] into [into] and returns true, or returns false and
     * leaves [into] alone when it has none. An agent with no position is skipped, and a target or
     * threat with none counts as gone.
     */
    fun position(world: World, entity: Entity, into: Vec3f): Boolean

    /**
     * Moves [entity] by [dx] and [dz] metres on the ground plane.
     *
     * Movement is kinematic and XZ-only: route waypoints carry no height, so whatever owns the
     * entity keeps it on the ground.
     */
    fun moveBy(world: World, entity: Entity, dx: Float, dz: Float)

    /**
     * Steers [entity] at the ground-plane velocity ([velocityX], [velocityZ]) metres per second for
     * [delta] seconds. The behaviours move agents through this, at their own speed.
     *
     * The default moves the entity that far now, through [moveBy]. A placement whose entities
     * something else moves, such as a character controller that stops at walls, overrides it to pass
     * the direction and speed on instead.
     */
    fun steer(world: World, entity: Entity, velocityX: Float, velocityZ: Float, delta: Float) {
        moveBy(world, entity, velocityX * delta, velocityZ * delta)
    }
}
