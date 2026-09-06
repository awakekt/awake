/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/**
 * Per-tick execution context passed through behavior trees and state machines.
 *
 * Reused by driver systems to guarantee zero allocations per tick in the 60fps frame loop.
 *
 * @property world The active ECS world containing entities and components.
 * @property entity The specific entity currently being ticked.
 * @property delta Elapsed seconds since the previous evaluation tick.
 * @property blackboard Entity-local key-value store for cross-node communication.
 */
class AiContext(
    var world: World,
    var entity: Entity,
    var delta: Float,
    var blackboard: Blackboard,
) {
    /**
     * Updates this context with the latest tick arguments.
     *
     * @param world Active ECS world.
     * @param entity Target entity.
     * @param delta Frame delta time in seconds.
     * @param blackboard Entity blackboard instance.
     */
    fun update(world: World, entity: Entity, delta: Float, blackboard: Blackboard) {
        this.world = world
        this.entity = entity
        this.delta = delta
        this.blackboard = blackboard
    }
}
