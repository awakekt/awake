/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai

import io.github.awakelab.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals

class AiEcsSystemsTest {

    @Test
    fun behaviorTreeSystemUpdatesEntityComponent() {
        val world = World()
        val system = BehaviorTreeSystem()

        var tickCount = 0
        val tree = behaviorTree {
            action {
                tickCount++
                BehaviorStatus.RUNNING
            }
        }

        val entity = world.create()
        world.add(entity, BehaviorTreeComponent(tree))

        system.update(world, 0.016f)
        assertEquals(1, tickCount)
        assertEquals(BehaviorStatus.RUNNING, world.get<BehaviorTreeComponent>(entity)?.activeStatus)

        system.update(world, 0.016f)
        assertEquals(2, tickCount)
    }

    @Test
    fun stateMachineSystemUpdatesEntityComponent() {
        val world = World()
        val system = AiStateMachineSystem()

        var updateCount = 0
        val testState = object : AiState("PATROL") {
            override fun onUpdate(context: AiContext) {
                updateCount++
            }
        }

        val fsm = AiStateMachine(
            states = mapOf("PATROL" to testState),
            transitions = emptyMap(),
            initialStateName = "PATROL",
        )

        val entity = world.create()
        world.add(entity, AiStateMachineComponent(fsm))

        system.update(world, 0.016f)
        assertEquals(1, updateCount)

        system.update(world, 0.016f)
        assertEquals(2, updateCount)
    }
}
