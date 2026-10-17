/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiEventId
import com.awakekt.awake.ai.AiEventQueue
import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.BehaviorTreeComponent
import com.awakekt.awake.ai.btree.BehaviorTreeSystem
import com.awakekt.awake.ecs.World
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A made-up gardening game: a robot keeps the plots of a greenhouse watered. The game registers
 * two kinds of its own in Kotlin, and the robot's behaviour is a JSON tree using them beside Core's.
 */
class GreenhouseGameTest {
    /** The game's own state, which its node kinds read and write. */
    private class Greenhouse(vararg moisture: Float) {
        val plots = moisture.toMutableList()
    }

    @Serializable
    data class DryParams(val plot: Int, val below: Float = 0.3f)

    @Serializable
    data class WaterParams(val plot: Int, val perSecond: Float = 0.5f)

    private fun registryFor(greenhouse: Greenhouse) = AiRegistry()
        .condition("garden.dry", DryParams.serializer(), "Plot Is Dry") { p, _ -> greenhouse.plots[p.plot] < p.below }
        .action("garden.water", WaterParams.serializer(), "Water Plot") { p, context ->
            greenhouse.plots[p.plot] = (greenhouse.plots[p.plot] + p.perSecond * context.delta).coerceAtMost(1f)
            if (greenhouse.plots[p.plot] >= 0.9f) BehaviorStatus.SUCCESS else BehaviorStatus.RUNNING
        }

    // A robot with "status.charging" set (by the game) does nothing; the status is the game's data.
    private val robot = """
        {
          "formatVersion": 1,
          "id": "greenhouse-robot",
          "blackboard": { "watered": 0 },
          "root": {
            "kind": "ai.sequence",
            "children": [
              { "kind": "ai.inverter", "children": [ { "kind": "ai.has", "params": { "key": "status.charging" } } ] },
              {
                "kind": "ai.selector",
                "children": [
                  { "kind": "ai.sequence", "id": "tend-0", "children": [
                    { "kind": "garden.dry", "params": { "plot": 0 } },
                    { "kind": "garden.water", "params": { "plot": 0 } },
                    { "kind": "ai.add", "params": { "key": "watered", "amount": 1 } }
                  ] },
                  { "kind": "ai.sequence", "id": "tend-1", "children": [
                    { "kind": "garden.dry", "params": { "plot": 1, "below": 0.5 } },
                    { "kind": "garden.water", "params": { "plot": 1, "perSecond": 1.0 } },
                    { "kind": "ai.add", "params": { "key": "watered", "amount": 1 } }
                  ] },
                  { "kind": "ai.cooldown", "params": { "seconds": 2 }, "children": [
                    { "kind": "ai.emit", "params": { "event": "robot.idle" } }
                  ] }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun theRobotWatersDryPlotsInOrderThenIdles() {
        val greenhouse = Greenhouse(0.1f, 0.4f)
        val compiled = registryFor(greenhouse).compileTree(AiDefinitionJson.Default.decodeTree(robot))
        val world = World()
        val events = AiEventQueue()
        val system = BehaviorTreeSystem(events)
        val entity = world.create()
        world.add(entity, compiled.newComponent())
        val component = world.get<BehaviorTreeComponent>(entity)!!

        repeat(4) { system.update(world, 0.25f) }
        // Plot 0 goes from 0.1 to 0.9 at 0.5 a second, so after a second it is still being watered.
        assertEquals(BehaviorStatus.RUNNING, component.activeStatus)
        assertEquals(listOf("0", "0.1", "0.1.0", "0.1.0.1"), component.tree.runningPaths())
        assertEquals("0.1.0", component.tree.pathOf("tend-0"))

        repeat(10) { system.update(world, 0.25f) }
        assertTrue(greenhouse.plots.all { it >= 0.9f }, "${greenhouse.plots}")
        assertEquals(2, component.blackboard.get<Int>("watered"))
        assertEquals(listOf(AiEventId("robot.idle")), events.drain().map { it.event }.distinct())
    }

    @Test
    fun aStatusTheGameSetsStopsTheRobot() {
        val greenhouse = Greenhouse(0.1f, 0.1f)
        val tree = registryFor(greenhouse).compileTree(AiDefinitionJson.Default.decodeTree(robot))
        val h = Harness(tree.newBlackboard())
        h.blackboard["status.charging"] = true
        val running = tree.newTree()
        repeat(10) { assertEquals(BehaviorStatus.FAILURE, h.tick(running)) }
        assertEquals(listOf(0.1f, 0.1f), greenhouse.plots)

        h.blackboard.remove("status.charging")
        h.tick(running)
        assertTrue(greenhouse.plots[0] > 0.1f)
    }

    @Test
    fun theGamesKindsShowInTheEditorWithTheirParameters() {
        val registry = registryFor(Greenhouse())
        val dry = registry.parameterSchema(NodeKindId("garden.dry"))
        assertEquals(listOf("plot", "below"), dry.children.map { it.name })
        assertEquals("Water Plot", registry.behaviorTreeNodeRegistry()["garden.water"]!!.displayName)
    }
}
