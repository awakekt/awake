/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.AiEventQueue
import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.BehaviorTree
import com.awakekt.awake.ai.btree.Blackboard
import com.awakekt.awake.ai.fsm.AiStateMachine
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** One agent's context: its blackboard and the events it emits. Deltas of 0.25 add up exactly. */
internal class Harness(val blackboard: Blackboard = Blackboard()) {
    val events = AiEventQueue()
    val context = AiContext(World(), Entity(1L), 0f, blackboard, events)

    fun tick(tree: BehaviorTree, delta: Float = 0.25f): BehaviorStatus {
        context.delta = delta
        return tree.tick(context)
    }

    fun update(machine: AiStateMachine, delta: Float = 0.25f) {
        context.delta = delta
        machine.update(context)
    }
}

internal fun params(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

internal fun node(kind: String, params: String = "{}", vararg children: NodeDefinition, id: String? = null) =
    NodeDefinition(NodeKindId(kind), id, params(params), children.toList())

internal fun treeOf(root: NodeDefinition, registry: AiRegistry = AiRegistry(), seed: Long = 0L): BehaviorTree =
    registry.compileTree(BehaviorTreeDefinition(root = root)).newTree(seed)
