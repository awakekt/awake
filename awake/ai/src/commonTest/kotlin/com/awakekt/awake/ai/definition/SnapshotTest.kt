/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.btree.BehaviorTreeSnapshot
import com.awakekt.awake.ai.btree.Blackboard
import com.awakekt.awake.ai.btree.BlackboardSnapshot
import com.awakekt.awake.ai.fsm.AiState
import com.awakekt.awake.ai.fsm.StateMachineSnapshot
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class SnapshotTest {
    private val busy = node(
        "ai.sequence",
        "{}",
        node("ai.wait", """{"seconds":1}"""),
        node("ai.cooldown", """{"seconds":2}""", node("ai.add", """{"key":"n","amount":1}""")),
        node("ai.repeat", """{"count":3}""", node("ai.chance", """{"probability":0.5}""")),
        node("ai.emit", """{"event":"done"}"""),
    )

    @Test
    fun aRestoredTreeContinuesExactlyAsTheOriginal() {
        val compiled = AiRegistry().compileTree(BehaviorTreeDefinition(root = node("ai.repeat", "{}", busy)))
        val original = compiled.newTree(seed = 5)
        val h = Harness()
        repeat(7) { h.tick(original) }

        val saved = Json.encodeToString(original.snapshot())
        val savedBoard = Json.encodeToString(h.blackboard.snapshot())
        val expected = List(40) { h.tick(original) }

        val copy = compiled.newTree(seed = 5)
        copy.restore(Json.decodeFromString<BehaviorTreeSnapshot>(saved))
        val h2 = Harness(Blackboard().apply { restore(Json.decodeFromString<BlackboardSnapshot>(savedBoard)) })
        assertEquals(expected, List(40) { h2.tick(copy) })
        assertEquals(h.blackboard.snapshot(), h2.blackboard.snapshot())
        assertEquals(h.events.events.size, h2.events.events.size)
    }

    @Test
    fun aSnapshotOfAnotherTreeIsRefusedWithWhatDiffers() {
        val registry = AiRegistry()
        val snapshot = treeOf(busy, registry).snapshot()
        val other = treeOf(node("ai.selector", "{}", node("ai.wait", """{"seconds":1}""")), registry)
        val error = assertFailsWith<IllegalArgumentException> { other.restore(snapshot) }
        assertTrue("0.1: nothing there keeps state" in error.message!!, error.message)
        assertFailsWith<IllegalArgumentException> { other.restore(BehaviorTreeSnapshot(formatVersion = 9)) }
    }

    @Test
    fun aRestoredMachineIsAlreadyInItsStateWithoutEnteringAgain() {
        var enters = 0
        val registry = AiRegistry().state("counted", NoParams.serializer()) { name, _ ->
            object : AiState(name) {
                override fun onEnter(context: AiContext) {
                    enters++
                }
            }
        }
        val definition = StateMachineDefinition(
            initial = "idle",
            states = listOf(
                StateDefinition("idle", kind = StateKindId("counted")),
                StateDefinition("busy", kind = StateKindId("counted"), tree = node("ai.wait", """{"seconds":1}""")),
            ),
            transitions = listOf(
                TransitionDefinition(from = "idle", to = "busy", after = 0.5f),
                TransitionDefinition(from = "busy", to = "idle", onTree = TreeOutcome.Success),
            ),
        )
        val compiled = registry.compileStateMachine(definition)
        val original = compiled.newStateMachine()
        val h = Harness()
        repeat(4) { h.update(original) }
        assertEquals("busy", original.currentState.name)
        val saved = Json.encodeToString(original.snapshot())
        val expected = List(20) {
            h.update(original)
            original.currentState.name to original.stateTimer
        }

        val copy = compiled.newStateMachine()
        enters = 0
        copy.restore(Json.decodeFromString<StateMachineSnapshot>(saved))
        assertEquals("busy", copy.currentState.name)
        assertEquals(0, enters)
        assertEquals(
            expected,
            List(20) {
                h.update(copy)
                copy.currentState.name to copy.stateTimer
            },
        )
        assertFailsWith<IllegalArgumentException> { copy.restore(StateMachineSnapshot(current = "gone")) }
    }

    @Test
    fun aBlackboardSnapshotKeepsEveryValueType() {
        val board = Blackboard()
        val spot = Vec3f(1f, 2f, 3f)
        board["flag"] = true
        board["count"] = 4
        board["speed"] = 1.5f
        board["name"] = "north gate"
        board["target"] = Entity(42L)
        board["spot"] = spot
        val text = Json.encodeToString(board.snapshot())
        val copy = Blackboard().apply { restore(Json.decodeFromString<BlackboardSnapshot>(text)) }
        assertEquals(board.snapshot(), copy.snapshot())
        assertEquals(listOf("count", "flag", "name", "speed", "spot", "target"), board.snapshot().values.keys.toList())
        assertEquals(spot, copy.get<Vec3f>("spot"))
        assertNotSame(spot, copy.get<Vec3f>("spot"))
        assertEquals(Entity(42L), copy.get<Entity>("target"))

        board["handle"] = Any()
        val error = assertFailsWith<IllegalStateException> { board.snapshot() }
        assertTrue(error.message!!.startsWith("Blackboard key 'handle' holds a"), error.message)
    }
}
