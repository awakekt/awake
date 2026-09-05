/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.fsm

import io.github.awakelab.awake.ai.btree.Blackboard

/**
 * ECS component attaching a state machine and blackboard to an entity.
 *
 * @property stateMachine The [AiStateMachine] instance driving this entity.
 * @property blackboard Entity-local memory store for sharing data across states.
 * @property enabled Whether this state machine is updated each frame.
 */
class AiStateMachineComponent(
    val stateMachine: AiStateMachine,
    val blackboard: Blackboard = Blackboard(),
    var enabled: Boolean = true,
)
