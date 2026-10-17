/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.ai.AiEventId
import com.awakekt.awake.ai.AiEventQueue
import com.awakekt.awake.ai.definition.AiRegistry
import com.awakekt.awake.ai.definition.BuiltInNodeKinds
import com.awakekt.awake.ai.definition.EmitParams
import com.awakekt.awake.ai.definition.NoParams
import com.awakekt.awake.ai.definition.NodeDefinition
import com.awakekt.awake.ai.definition.StateDefinition
import com.awakekt.awake.ai.definition.StateMachineDefinition
import com.awakekt.awake.ai.definition.TransitionDefinition
import com.awakekt.awake.ai.fsm.AiStateMachineComponent
import com.awakekt.awake.ai.fsm.AiStateMachineSystem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Not a game: a shop's automatic door. Its "eye" is a perceiver with a short all-round sight and no
 * line-of-sight test, and it opens for anyone, whatever their faction.
 */
class AutomaticDoorTest {
    @Test
    fun theDoorOpensForAnyoneNearAndClosesAfterThemLeaving() {
        val scene = PerceptionScene()
        val door = scene.spawn(0f, 0f)
        val shopper = scene.spawn(10f, 0f)
        val cart = scene.spawn(10f, 1f)
        scene.world.add(
            door,
            Perceiver(
                FactionId("door"),
                PerceptionSettings(
                    sight = SightSettings(peripheralRange = 2f, peripheralAngle = 360f, eyeHeight = 0f, lineOfSight = false),
                    forgetAfter = 0f,
                    updateInterval = 0f,
                ),
            ),
        )
        scene.world.add(shopper, Perceivable(FactionId("shoppers"), height = 0f))
        scene.world.add(cart, Perceivable(FactionId("carts"), height = 0f))

        fun announce(event: String) = NodeDefinition.of(BuiltInNodeKinds.Emit, EmitParams(AiEventId(event)))
        val someone = NodeDefinition.of(PerceptionNodeKinds.Sees, SeesParams(RelationFilter.Any))
        val machine = StateMachineDefinition(
            initial = "closed",
            states = listOf(StateDefinition("closed", enter = listOf(announce("door.closed"))), StateDefinition("open", enter = listOf(announce("door.opened")))),
            transitions = listOf(
                TransitionDefinition(from = "closed", to = "open", condition = someone),
                TransitionDefinition(from = "open", to = "closed", after = 2f, condition = NodeDefinition.of(BuiltInNodeKinds.Inverter, NoParams, listOf(someone))),
            ),
        )
        scene.world.add(door, AiRegistry().compileStateMachine(machine).newComponent())
        val events = AiEventQueue()
        val perception = PerceptionSystem(scene)
        val behaviour = AiStateMachineSystem(events)
        fun state(): String {
            perception.update(scene.world, 0.5f)
            behaviour.update(scene.world, 0.5f)
            return scene.world.get<AiStateMachineComponent>(door)!!.stateMachine.currentState.name
        }

        assertEquals("closed", state())
        scene.place(cart, -1.5f, 0.5f) // A trolley rolls past behind it: anything opens it.
        assertEquals("open", state())
        scene.place(cart, -9f, 0f)
        assertEquals(listOf("open", "open", "open", "closed"), List(4) { state() })
        scene.place(shopper, 0f, 1.9f)
        assertEquals("open", state())
        assertEquals(listOf("door.closed", "door.opened", "door.closed", "door.opened"), events.drain().map { it.event.value })
    }
}
