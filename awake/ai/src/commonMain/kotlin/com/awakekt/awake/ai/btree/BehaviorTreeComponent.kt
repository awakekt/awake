/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

/**
 * ECS component attaching a behavior tree instance and blackboard to an entity.
 *
 * @property root The root node of this entity's behavior tree.
 * @property blackboard Entity-local memory store for sharing data across tree nodes.
 * @property activeStatus Current execution status resulting from the most recent tick.
 * @property enabled Whether this entity's behavior tree is active and evaluated each frame.
 */
class BehaviorTreeComponent(
    val root: BehaviorNode,
    val blackboard: Blackboard = Blackboard(),
    var activeStatus: BehaviorStatus = BehaviorStatus.RUNNING,
    var enabled: Boolean = true,
)
