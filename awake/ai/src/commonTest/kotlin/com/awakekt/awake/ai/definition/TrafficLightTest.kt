/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiEventId
import com.awakekt.awake.ai.AiEventQueue
import com.awakekt.awake.ai.btree.BlackboardValue
import com.awakekt.awake.ai.fsm.AiStateMachineComponent
import com.awakekt.awake.ai.fsm.AiStateMachineSystem
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Not a game: a pedestrian crossing's traffic light, timed by transitions, with a request button
 * that shortens green. Nothing here is about agents or enemies; the machine is the same mechanism.
 */
class TrafficLightTest {
    private fun announce(colour: String) =
        NodeDefinition.of(BuiltInNodeKinds.Emit, EmitParams(AiEventId("light.changed"), mapOf("colour" to BlackboardValue.TextValue(colour))))

    private val light = StateMachineDefinition(
        id = "crossing",
        initial = "green",
        states = listOf(
            StateDefinition("green", enter = listOf(announce("green"))),
            StateDefinition("amber", enter = listOf(announce("amber"))),
            StateDefinition(
                "red",
                enter = listOf(announce("red")),
                exit = listOf(NodeDefinition.of(BuiltInNodeKinds.RemoveValue, KeyParams("button"))),
            ),
        ),
        transitions = listOf(
            TransitionDefinition(from = "green", to = "amber", after = 4f),
            TransitionDefinition(from = "green", to = "amber", after = 1f, condition = NodeDefinition.of(BuiltInNodeKinds.HasKey, KeyParams("button"))),
            TransitionDefinition(from = "amber", to = "red", after = 1f),
            TransitionDefinition(from = "red", to = "green", after = 3f),
        ),
    )

    @Test
    fun theLightCyclesOnItsTimersAndTheButtonShortensGreen() {
        val world = World()
        val events = AiEventQueue()
        val system = AiStateMachineSystem(events)
        val crossing = world.create()
        world.add(crossing, AiRegistry().compileStateMachine(light).newComponent())
        val board = world.get<AiStateMachineComponent>(crossing)!!.blackboard

        fun colourAfter(seconds: Float): String {
            repeat((seconds / 0.5f).toInt()) { system.update(world, 0.5f) }
            return world.get<AiStateMachineComponent>(crossing)!!.stateMachine.currentState.name
        }

        assertEquals("green", colourAfter(3.5f))
        assertEquals("amber", colourAfter(0.5f))
        assertEquals("red", colourAfter(1f))
        assertEquals("green", colourAfter(3f))
        board["button"] = true
        assertEquals("amber", colourAfter(1f))
        assertEquals("red", colourAfter(1f))
        assertEquals("green", colourAfter(3f))
        assertEquals(false, board.contains("button"))

        val colours = events.drain().map { (it.payload.getValue("colour") as BlackboardValue.TextValue).value }
        assertEquals(listOf("green", "amber", "red", "green", "amber", "red", "green"), colours)
    }
}
