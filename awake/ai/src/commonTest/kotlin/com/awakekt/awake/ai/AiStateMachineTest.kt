/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals

class AiStateMachineTest {

    @Test
    fun stateMachineTransitionsCorrectly() {
        var enteredIdle = false
        var exitedIdle = false
        var enteredChase = false

        val idleState = object : AiState("IDLE") {
            override fun onEnter(context: AiContext) {
                enteredIdle = true
            }
            override fun onExit(context: AiContext) {
                exitedIdle = true
            }
        }
        val chaseState = object : AiState("CHASE") {
            override fun onEnter(context: AiContext) {
                enteredChase = true
            }
        }

        var shouldChase = false
        val transitions = mapOf(
            "IDLE" to listOf(StateTransition("CHASE") { shouldChase }),
        )
        val fsm = AiStateMachine(
            states = mapOf("IDLE" to idleState, "CHASE" to chaseState),
            transitions = transitions,
            initialStateName = "IDLE",
        )

        val ctx = AiContext(World(), Entity.of(1, 0), 0.016f, Blackboard())

        fsm.update(ctx)
        assertEquals("IDLE", fsm.currentState.name)
        assertEquals(true, enteredIdle)
        assertEquals(false, exitedIdle)

        shouldChase = true
        fsm.update(ctx)
        assertEquals("CHASE", fsm.currentState.name)
        assertEquals(true, exitedIdle)
        assertEquals(true, enteredChase)
    }
}
