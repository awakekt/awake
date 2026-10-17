/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.btree.BehaviorStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefinitionValidationTest {
    private val registry = AiRegistry()

    private fun problems(root: NodeDefinition) = registry.validate(BehaviorTreeDefinition(root = root))

    @Test
    fun anUnknownKindIsNamedWithTheClosestKnownOne() {
        val problem = problems(node("ai.sequence", "{}", node("ai.wiat", """{"seconds":1}"""))).single()
        assertEquals(AiProblemCode.UNKNOWN_KIND, problem.code)
        assertEquals("node 0.0 (ai.wiat): unknown node kind 'ai.wiat' (did you mean 'ai.wait'?).", problem.toString())
    }

    @Test
    fun parameterProblemsNameTheParameter() {
        assertEquals(
            "node 0 (ai.wait): unknown parameter 'secs' (did you mean 'seconds'?); it takes seconds.",
            problems(node("ai.wait", """{"secs":1}""")).single().toString(),
        )
        assertEquals(
            "node 0 (ai.wait): missing parameter 'seconds'; ai.wait needs it.",
            problems(node("ai.wait", "{}")).single().toString(),
        )
        assertEquals(
            "node 0 (ai.chance): probability is 1.5; it must be from 0 to 1.",
            problems(node("ai.chance", """{"probability":1.5}""")).single().toString(),
        )
        assertEquals(
            "node 0 (ai.compare): give value or otherKey, not both or neither.",
            problems(node("ai.compare", """{"key":"a","op":"==","value":1,"otherKey":"b"}""")).single().toString(),
        )
        val wrongType = problems(node("ai.wait", """{"seconds":"soon"}""")).single()
        assertEquals(AiProblemCode.INVALID_PARAMS, wrongType.code)
        assertTrue("seconds" in wrongType.message, wrongType.message)
    }

    @Test
    fun childCountsAndDuplicateIdsAreChecked() {
        val found = problems(
            node(
                "ai.sequence",
                "{}",
                node("ai.inverter", "{}", id = "flip"),
                node("ai.wait", """{"seconds":1}""", node("ai.status", """{"status":"success"}"""), id = "flip"),
            ),
        )
        assertEquals(
            listOf(
                "node 0.0 (ai.inverter): ai.inverter takes exactly 1 child, but has 0.",
                "node 0.1 (ai.wait): id 'flip' is already used in this tree.",
                "node 0.1 (ai.wait): ai.wait takes no children, but has 1.",
            ),
            found.map { it.toString() },
        )
        assertEquals("flip", found.last().nodeId)
    }

    @Test
    fun compileThrowsEveryProblemAtOnce() {
        val error = assertFailsWith<AiDefinitionException> {
            registry.compileTree(
                BehaviorTreeDefinition(formatVersion = 9, root = node("ai.selector", "{}", node("nope"), node("ai.wait", "{}"))),
            )
        }
        assertEquals(
            listOf(AiProblemCode.UNSUPPORTED_FORMAT, AiProblemCode.UNKNOWN_KIND, AiProblemCode.INVALID_PARAMS),
            error.problems.map { it.code },
        )
        assertTrue(error.message!!.startsWith("Invalid AI definition:\n- formatVersion 9"))
    }

    @Test
    fun stateMachineProblemsNameTheStateOrTransition() {
        val definition = StateMachineDefinition(
            initial = "opne",
            states = listOf(
                StateDefinition("open"),
                StateDefinition("open"),
                StateDefinition("shut", kind = StateKindId("door.jammed")),
            ),
            transitions = listOf(
                TransitionDefinition(from = "open", to = "shot", after = 1f),
                TransitionDefinition(from = "shut", to = "open"),
                TransitionDefinition(from = "open", to = "shut", onTree = TreeOutcome.Success),
                TransitionDefinition(to = "open", condition = node("ai.wait", "{}")),
            ),
        )
        assertEquals(
            listOf(
                "state 'open': the name is used twice.",
                "initial: 'opne' is not a state (did you mean 'open'?).",
                "state 'shut': unknown state kind 'door.jammed'.",
                "transition 0 (open -> shot): 'shot' is not a state (did you mean 'shut'?).",
                "transition 1 (shut -> open): it has no trigger; give it after, onTree or condition (an ai.status condition always fires).",
                "transition 2 (open -> shut): onTree waits for a tree, but 'open' has none.",
                "transition 3 (any -> open) condition node 0 (ai.wait): missing parameter 'seconds'; ai.wait needs it.",
            ),
            registry.validate(definition).map { it.toString() },
        )
    }

    @Test
    fun aKindIdIsRegisteredOnceAndMustBeWellFormed() {
        val custom = AiRegistry().action("garden.water") { BehaviorStatus.SUCCESS }
        assertFailsWith<IllegalArgumentException> { custom.action("garden.water") { BehaviorStatus.SUCCESS } }
        assertFailsWith<IllegalArgumentException> { NodeKindId("has space") }
        assertFailsWith<IllegalArgumentException> { NodeKindId("trailing.") }
    }
}
