/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

/**
 * ECS component attaching a behavior tree instance and blackboard to an entity.
 *
 * @property tree The running tree: its root, node statuses for editors, and snapshots.
 * @property blackboard Entity-local memory store for sharing data across tree nodes.
 * @property activeStatus Current execution status resulting from the most recent tick.
 * @property enabled Whether this entity's behavior tree is active and evaluated each frame.
 */
class BehaviorTreeComponent(
    val tree: BehaviorTree,
    val blackboard: Blackboard = Blackboard(),
    var activeStatus: BehaviorStatus = BehaviorStatus.RUNNING,
    var enabled: Boolean = true,
) {
    /**
     * Attaches a tree built in Kotlin, such as one from `behaviorTree { }`.
     *
     * @param root The root node of this entity's behavior tree.
     * @param blackboard Entity-local memory store for sharing data across tree nodes.
     * @param activeStatus Current execution status resulting from the most recent tick.
     * @param enabled Whether this entity's behavior tree is active and evaluated each frame.
     */
    constructor(
        root: BehaviorNode,
        blackboard: Blackboard = Blackboard(),
        activeStatus: BehaviorStatus = BehaviorStatus.RUNNING,
        enabled: Boolean = true,
    ) : this(BehaviorTree.of(root), blackboard, activeStatus, enabled)

    /** The root node of this entity's behavior tree. */
    val root: BehaviorNode get() = tree.root
}
