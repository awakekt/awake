/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiEventId
import com.awakekt.awake.ai.btree.BehaviorStatus.FAILURE
import com.awakekt.awake.ai.btree.BehaviorStatus.RUNNING
import com.awakekt.awake.ai.btree.BehaviorStatus.SUCCESS
import com.awakekt.awake.ai.btree.BlackboardValue
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BuiltInNodeKindsTest {
    private val ok = node("ai.status", """{"status":"success"}""")
    private val no = node("ai.status", """{"status":"failure"}""")
    private val wait = node("ai.wait", """{"seconds":0.5}""")

    @Test
    fun everyBuiltInIsRegisteredWithADescription() {
        val registry = AiRegistry()
        assertEquals(BuiltInNodeKinds.nodeKinds.map { it.id }, registry.nodeKinds.map { it.id })
        assertTrue(registry.nodeKinds.all { it.id.value.startsWith("ai.") && it.description.isNotBlank() })
        assertTrue(AiRegistry(includeBuiltIns = false).nodeKinds.isEmpty())
    }

    @Test
    fun sequenceStopsAtTheFirstFailureAndSelectorAtTheFirstSuccess() {
        val h = Harness()
        assertEquals(FAILURE, h.tick(treeOf(node("ai.sequence", "{}", ok, no, ok))))
        assertEquals(SUCCESS, h.tick(treeOf(node("ai.sequence", "{}", ok, ok))))
        assertEquals(SUCCESS, h.tick(treeOf(node("ai.selector", "{}", no, ok))))
        assertEquals(FAILURE, h.tick(treeOf(node("ai.selector", "{}", no, no))))
    }

    @Test
    fun parallelFinishesByItsPolicy() {
        val h = Harness()
        assertEquals(RUNNING, h.tick(treeOf(node("ai.parallel", """{"policy":"all"}""", ok, wait))))
        assertEquals(SUCCESS, h.tick(treeOf(node("ai.parallel", """{"policy":"one"}""", ok, wait))))
        assertEquals(FAILURE, h.tick(treeOf(node("ai.parallel", "{}", no, wait))))
    }

    @Test
    fun decoratorsChangeTheirChild() {
        val h = Harness()
        assertEquals(SUCCESS, h.tick(treeOf(node("ai.inverter", "{}", no))))
        assertEquals(SUCCESS, h.tick(treeOf(node("ai.succeeder", "{}", no))))
        assertEquals(RUNNING, h.tick(treeOf(node("ai.succeeder", "{}", wait))))
    }

    @Test
    fun repeatCountsSuccessfulRuns() {
        val h = Harness()
        val tree = treeOf(node("ai.repeat", """{"count":3}""", node("ai.add", """{"key":"n","amount":1}""")))
        assertEquals(SUCCESS, h.tick(tree))
        assertEquals(3, h.blackboard.get<Int>("n"))
    }

    @Test
    fun cooldownFailsUntilItsTimeHasBeenTicked() {
        val h = Harness()
        val tree = treeOf(node("ai.cooldown", """{"seconds":0.5}""", ok))
        assertEquals(SUCCESS, h.tick(tree))
        assertEquals(FAILURE, h.tick(tree))
        assertEquals(FAILURE, h.tick(tree))
        assertEquals(SUCCESS, h.tick(tree))
    }

    @Test
    fun timeoutFailsAChildThatRunsTooLong() {
        val h = Harness()
        val tree = treeOf(node("ai.timeout", """{"seconds":0.5}""", node("ai.wait", """{"seconds":5}""")))
        assertEquals(RUNNING, h.tick(tree))
        assertEquals(FAILURE, h.tick(tree))
    }

    @Test
    fun waitRunsForItsSecondsThenSucceeds() {
        val h = Harness()
        val tree = treeOf(wait)
        assertEquals(listOf(RUNNING, SUCCESS, RUNNING, SUCCESS), List(4) { h.tick(tree) })
    }

    @Test
    fun compareTestsNumbersTextAndOtherKeys() {
        val h = Harness()
        h.blackboard["level"] = 3
        h.blackboard["limit"] = 2.5f
        h.blackboard["mode"] = "open"
        fun compare(json: String) = h.tick(treeOf(node("ai.compare", json)))
        assertEquals(SUCCESS, compare("""{"key":"level","op":">","value":2.5}"""))
        assertEquals(SUCCESS, compare("""{"key":"level","op":"==","value":3.0}"""))
        assertEquals(FAILURE, compare("""{"key":"level","op":"<=","otherKey":"limit"}"""))
        assertEquals(SUCCESS, compare("""{"key":"mode","op":"==","value":"open"}"""))
        assertEquals(SUCCESS, compare("""{"key":"mode","op":"!=","value":"shut"}"""))
        assertEquals(FAILURE, compare("""{"key":"absent","op":"!=","value":1}"""))
        assertEquals(FAILURE, compare("""{"key":"mode","op":"==","value":1}"""))
    }

    @Test
    fun blackboardWritesStoreTypedValues() {
        val h = Harness()
        h.tick(treeOf(node("ai.set", """{"key":"where","value":{"x":1.0,"y":0.0,"z":2.0}}""")))
        h.tick(treeOf(node("ai.set", """{"key":"who","value":{"entity":7}}""")))
        h.tick(treeOf(node("ai.set", """{"key":"calm","value":true}""")))
        assertEquals(Vec3f(1f, 0f, 2f), h.blackboard.get<Vec3f>("where"))
        assertEquals(Entity(7L), h.blackboard.get<Entity>("who"))
        assertEquals(true, h.blackboard.get<Boolean>("calm"))

        assertEquals(SUCCESS, h.tick(treeOf(node("ai.copy", """{"from":"calm","to":"wasCalm"}"""))))
        assertEquals(FAILURE, h.tick(treeOf(node("ai.copy", """{"from":"absent","to":"x"}"""))))
        assertEquals(SUCCESS, h.tick(treeOf(node("ai.has", """{"key":"wasCalm"}"""))))
        h.tick(treeOf(node("ai.remove", """{"key":"wasCalm"}""")))
        assertEquals(FAILURE, h.tick(treeOf(node("ai.has", """{"key":"wasCalm"}"""))))
    }

    @Test
    fun addKeepsIntsWholeAndRefusesText() {
        val h = Harness()
        h.tick(treeOf(node("ai.add", """{"key":"count","amount":2}""")))
        assertEquals(2, h.blackboard.get<Int>("count"))
        h.tick(treeOf(node("ai.add", """{"key":"count","amount":0.5}""")))
        assertEquals(2.5f, h.blackboard.get<Float>("count"))
        h.blackboard["name"] = "x"
        assertEquals(FAILURE, h.tick(treeOf(node("ai.add", """{"key":"name","amount":1}"""))))
    }

    @Test
    fun chanceRepeatsForASeedAndDiffersAcrossSeeds() {
        fun draws(seed: Long): List<Boolean> {
            val h = Harness()
            val tree = treeOf(node("ai.chance", """{"probability":0.5}"""), seed = seed)
            return List(64) { h.tick(tree) == SUCCESS }
        }
        assertEquals(draws(11), draws(11))
        assertNotEquals(draws(11), draws(12))
        val hits = draws(3).count { it }
        assertTrue(hits in 16..48, "about half of 64 draws succeed, got $hits")

        val never = treeOf(node("ai.chance", """{"probability":0}"""))
        assertFalse(List(32) { Harness().tick(never) }.contains(SUCCESS))
    }

    @Test
    fun emitSendsItsEventToTheSink() {
        val h = Harness()
        h.tick(treeOf(node("ai.emit", """{"event":"bell.rang","payload":{"times":3}}""")))
        val event = h.events.drain().single()
        assertEquals(AiEventId("bell.rang"), event.event)
        assertEquals(mapOf("times" to BlackboardValue.IntValue(3)), event.payload)
        assertEquals(Entity(1L), event.entity)
    }
}
