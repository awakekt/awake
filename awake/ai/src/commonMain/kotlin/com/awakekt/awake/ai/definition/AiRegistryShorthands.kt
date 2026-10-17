/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.btree.ActionNode
import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.ConditionNode
import com.awakekt.awake.ai.fsm.AiState
import kotlinx.serialization.KSerializer

/**
 * Registers an action kind [id] with typed [params]: a leaf that calls [run] each tick. This is how
 * a game makes its own actions, such as moving or playing an animation, usable from data.
 */
fun <P : Any> AiRegistry.action(
    id: String,
    params: KSerializer<P>,
    displayName: String = id,
    description: String = "",
    run: (P, AiContext) -> BehaviorStatus,
): AiRegistry =
    register(
        NodeKind(NodeKindId(id), params, NodeCategory.Action, displayName = displayName, description = description) { p, _ ->
            ActionNode { context -> run(p, context) }
        },
    )

/** Registers an action kind [id] that takes no parameters. */
fun AiRegistry.action(
    id: String,
    displayName: String = id,
    description: String = "",
    run: (AiContext) -> BehaviorStatus,
): AiRegistry = action(id, NoParams.serializer(), displayName, description) { _, context -> run(context) }

/** Registers a condition kind [id] with typed [params]: a leaf that succeeds when [test] is true. */
fun <P : Any> AiRegistry.condition(
    id: String,
    params: KSerializer<P>,
    displayName: String = id,
    description: String = "",
    test: (P, AiContext) -> Boolean,
): AiRegistry =
    register(
        NodeKind(NodeKindId(id), params, NodeCategory.Condition, displayName = displayName, description = description) { p, _ ->
            ConditionNode { context -> test(p, context) }
        },
    )

/** Registers a condition kind [id] that takes no parameters. */
fun AiRegistry.condition(
    id: String,
    displayName: String = id,
    description: String = "",
    test: (AiContext) -> Boolean,
): AiRegistry = condition(id, NoParams.serializer(), displayName, description) { _, context -> test(context) }

/**
 * Registers a state kind [id] with typed [params], built by [create] with the name the definition
 * gives it.
 */
fun <P : Any> AiRegistry.state(
    id: String,
    params: KSerializer<P>,
    displayName: String = id,
    description: String = "",
    create: (name: String, params: P) -> AiState,
): AiRegistry =
    register(StateKind(StateKindId(id), params, displayName, description) { p, scope -> create(scope.name, p) })

/** Every problem with [definition] against this registry; empty when it compiles. */
fun AiRegistry.validate(definition: BehaviorTreeDefinition): List<AiDefinitionProblem> =
    try {
        compileTree(definition)
        emptyList()
    } catch (e: AiDefinitionException) {
        e.problems
    }

/** Every problem with [definition] against this registry; empty when it compiles. */
fun AiRegistry.validate(definition: StateMachineDefinition): List<AiDefinitionProblem> =
    try {
        compileStateMachine(definition)
        emptyList()
    } catch (e: AiDefinitionException) {
        e.problems
    }
