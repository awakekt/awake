/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.BehaviorTreeComponent
import com.awakekt.awake.ai.btree.BehaviorTreeSystem
import com.awakekt.awake.ai.definition.AiDefinitionJson
import com.awakekt.awake.ai.definition.AiRegistry
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A made-up stealth game: a castle guard looks east along a corridor with a pillar in it. Thieves
 * are hostile to the watch and merchants are not; both are factions in the game's data.
 */
class KeepWatchGameTest {
    private val watch = FactionId("watch")
    private val thieves = FactionId("thieves")
    private val merchants = FactionId("merchants")
    private val factions = FactionTable(listOf(FactionRelation(watch, thieves, Relation.Hostile)))

    private val scene = PerceptionScene()
    private val guard = scene.spawn(0f, 0f, faceX = 1f, faceZ = 0f)
    private val thief = scene.spawn(12f, 0f)
    private val merchant = scene.spawn(3f, 3f)
    private val system = PerceptionSystem(scene, factions, Wall(x = 6f, fromZ = 2f, toZ = 4f))

    init {
        val settings = PerceptionSettings(
            sight = SightSettings(focusRange = 15f, focusAngle = 60f, peripheralRange = 5f, peripheralAngle = 200f),
            forgetAfter = 3f,
            updateInterval = 0f,
        )
        scene.world.add(guard, Perceiver(watch, settings))
        scene.world.add(thief, Perceivable(thieves))
        scene.world.add(merchant, Perceivable(merchants))
    }

    private val memory get() = scene.world.get<Perceiver>(guard)!!.memory

    private fun step(seconds: Float = 0.5f) = system.update(scene.world, seconds)

    @Test
    fun theGuardSeesInItsConesAndNotThroughThePillar() {
        step()
        assertEquals(Sense.Focus, memory[thief]!!.sense)
        assertEquals(Relation.Hostile, memory[thief]!!.relation)
        assertEquals(Sense.Peripheral, memory[merchant]!!.sense)
        assertEquals(Relation.Neutral, memory[merchant]!!.relation)

        scene.place(thief, 10f, 8f) // 39 degrees off: outside the focus cone, too far for the side.
        step()
        assertFalse(memory[thief]!!.inSight)
        scene.place(thief, 10f, 5f) // Behind the pillar.
        step()
        assertFalse(memory[thief]!!.inSight)
        scene.place(thief, 1f, -4f) // Off to the side, but close: peripheral covers 100 degrees each way.
        step()
        assertTrue(memory[thief]!!.inSight)
        assertEquals(Sense.Peripheral, memory[thief]!!.sense)
    }

    @Test
    fun theGuardRemembersWhereTheThiefWasThenForgets() {
        step()
        scene.place(thief, 10f, 5f)
        step()
        assertEquals(Vec3f(12f, 0f, 0f), memory[thief]!!.lastKnownPosition)
        step(1f)
        step(1f)
        assertTrue(memory.remembersAny(Relation.Hostile))
        step(1f)
        assertNull(memory[thief])
    }

    @Test
    fun aHiddenThiefIsNotSeen() {
        scene.world.get<Perceivable>(thief)!!.visible = false
        step()
        assertNull(memory[thief])
    }

    @Test
    fun theGuardsTreeReactsThroughTheRegistryAndTheBlackboard() {
        val tree = """
            {
              "root": { "kind": "ai.selector", "children": [
                { "kind": "ai.sequence", "children": [
                  { "kind": "ai.perception.sees", "params": { "relation": "hostile", "focusOnly": true } },
                  { "kind": "ai.set", "params": { "key": "mode", "value": "chase" } }
                ] },
                { "kind": "ai.sequence", "children": [
                  { "kind": "ai.perception.remembers" },
                  { "kind": "ai.set", "params": { "key": "mode", "value": "search" } }
                ] },
                { "kind": "ai.set", "params": { "key": "mode", "value": "post" } }
              ] }
            }
        """.trimIndent()
        scene.world.add(guard, AiRegistry().compileTree(AiDefinitionJson.Default.decodeTree(tree)).newComponent())
        val behaviour = BehaviorTreeSystem()
        val board = scene.world.get<BehaviorTreeComponent>(guard)!!.blackboard
        fun tick(seconds: Float = 0.5f): String? {
            step(seconds)
            behaviour.update(scene.world, seconds)
            return board.get<String>("mode")
        }

        assertEquals("chase", tick())
        assertEquals(thief, board.get<Entity>("perception.target"))
        assertEquals(Vec3f(12f, 0f, 0f), board.get<Vec3f>("perception.target.position"))
        assertEquals(true, board.get<Boolean>("perception.sees-hostile"))
        assertEquals(1, board.get<Int>("perception.hostiles"))

        scene.place(thief, 10f, 5f)
        assertEquals("search", tick())
        assertEquals(false, board.get<Boolean>("perception.sees-hostile"))
        assertEquals(Vec3f(12f, 0f, 0f), board.get<Vec3f>("perception.target.position"))
        assertEquals("post", tick(3f))
        assertFalse(board.contains("perception.target"))
        assertEquals(BehaviorStatus.SUCCESS, scene.world.get<BehaviorTreeComponent>(guard)!!.activeStatus)
    }
}
