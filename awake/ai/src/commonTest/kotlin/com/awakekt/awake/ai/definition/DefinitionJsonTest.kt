/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.BehaviorTree
import com.awakekt.awake.ai.btree.BlackboardValue
import com.awakekt.awake.ai.btree.CompareOp
import com.awakekt.awake.ai.btree.ParallelPolicy
import com.awakekt.awake.ai.btree.behaviorTree
import com.awakekt.awake.ai.fsm.AiState
import com.awakekt.awake.ai.fsm.AiStateMachine
import com.awakekt.awake.ai.fsm.StateTransition
import com.awakekt.awake.ecs.Entity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefinitionJsonTest {
    private val codec = AiDefinitionJson.Default

    // Alert above 0.5 => record it; otherwise wait 0.5 s and count a patrol step.
    private val tree = BehaviorTreeDefinition(
        id = "sentry",
        blackboard = mapOf("alert" to BlackboardValue.FloatValue(0f)),
        root = NodeDefinition.of(
            BuiltInNodeKinds.Selector,
            NoParams,
            listOf(
                NodeDefinition.of(
                    BuiltInNodeKinds.Sequence,
                    NoParams,
                    listOf(
                        NodeDefinition.of(BuiltInNodeKinds.Compare, CompareParams("alert", CompareOp.Greater, BlackboardValue.FloatValue(0.5f))),
                        NodeDefinition.of(BuiltInNodeKinds.AddToValue, AddParams("alerts", BlackboardValue.IntValue(1))),
                    ),
                ),
                NodeDefinition.of(
                    BuiltInNodeKinds.Sequence,
                    NoParams,
                    listOf(
                        NodeDefinition.of(BuiltInNodeKinds.Wait, DurationParams(0.5f), id = "pause"),
                        NodeDefinition.of(BuiltInNodeKinds.AddToValue, AddParams("steps", BlackboardValue.IntValue(1))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun aTreeRoundTripsThroughJson() {
        val text = codec.encode(tree)
        assertEquals(tree, codec.decodeTree(text))
        assertTrue("\"formatVersion\": 1" in text, text)
        assertTrue("\"op\": \">\"" in text, text)
        assertTrue("\"value\": 0.5" in text, text)
    }

    @Test
    fun aTreeFromJsonBehavesLikeItsDslTwin() {
        val loaded = AiRegistry().compileTree(codec.decodeTree(codec.encode(tree))).newTree()
        var waited = 0f
        val dsl = BehaviorTree.of(
            behaviorTree {
                selector {
                    sequence {
                        condition { (it.blackboard.get<Float>("alert") ?: 0f) > 0.5f }
                        action { it.blackboard["alerts"] = (it.blackboard.get<Int>("alerts") ?: 0) + 1; BehaviorStatus.SUCCESS }
                    }
                    sequence {
                        action {
                            waited += it.delta
                            if (waited < 0.5f) BehaviorStatus.RUNNING else BehaviorStatus.SUCCESS.also { waited = 0f }
                        }
                        action { it.blackboard["steps"] = (it.blackboard.get<Int>("steps") ?: 0) + 1; BehaviorStatus.SUCCESS }
                    }
                }
            },
        )
        val a = Harness()
        val b = Harness()
        repeat(12) { step ->
            val alert = if (step in 5..7) 0.9f else 0.1f
            a.blackboard["alert"] = alert
            b.blackboard["alert"] = alert
            assertEquals(b.tick(dsl), a.tick(loaded), "tick $step")
        }
        assertEquals(b.blackboard.snapshot(), a.blackboard.snapshot())
        // Step 5 finishes the wait it was in; steps 6 and 7 see the alert.
        assertEquals(2, a.blackboard.get<Int>("alerts"))
    }

    @Test
    fun aStateMachineRoundTripsAndBehavesLikeItsKotlinTwin() {
        val machine = StateMachineDefinition(
            id = "lamp",
            initial = "off",
            states = listOf(
                StateDefinition("off", enter = listOf(NodeDefinition.of(BuiltInNodeKinds.SetValue, SetParams("lit", BlackboardValue.BoolValue(false))))),
                StateDefinition("on", enter = listOf(NodeDefinition.of(BuiltInNodeKinds.SetValue, SetParams("lit", BlackboardValue.BoolValue(true))))),
            ),
            transitions = listOf(
                TransitionDefinition(from = "off", to = "on", condition = NodeDefinition.of(BuiltInNodeKinds.HasKey, KeyParams("dark"))),
                TransitionDefinition(from = "on", to = "off", after = 1f),
            ),
        )
        val text = codec.encode(machine)
        assertEquals(machine, codec.decodeStateMachine(text))

        val loaded = AiRegistry().compileStateMachine(codec.decodeStateMachine(text)).newStateMachine()
        val kotlin = AiStateMachine(
            states = mapOf(
                "off" to object : AiState("off") {
                    override fun onEnter(context: AiContext) = context.blackboard.set("lit", false)
                },
                "on" to object : AiState("on") {
                    override fun onEnter(context: AiContext) = context.blackboard.set("lit", true)
                },
            ),
            transitions = mapOf(
                "off" to listOf(StateTransition("on") { it.blackboard.contains("dark") }),
                "on" to listOf(StateTransition("off", afterSeconds = 1f) { true }),
            ),
            initialStateName = "off",
        )
        val a = Harness()
        val b = Harness()
        repeat(16) { step ->
            if (step == 3) {
                a.blackboard["dark"] = true
                b.blackboard["dark"] = true
            }
            if (step == 6) {
                a.blackboard.remove("dark")
                b.blackboard.remove("dark")
            }
            a.update(loaded)
            b.update(kotlin)
            assertEquals(kotlin.currentState.name, loaded.currentState.name, "update $step")
            assertEquals(b.blackboard.get<Boolean>("lit"), a.blackboard.get<Boolean>("lit"), "update $step")
        }
    }

    @Test
    fun blackboardValuesReadAsPlainJson() {
        val values = Json.decodeFromString<Map<String, BlackboardValue>>(
            """{"a":true,"b":2,"c":2.0,"d":"two","e":{"entity":4294967297},"f":{"x":1,"y":2.5,"z":-3}}""",
        )
        assertEquals(
            mapOf(
                "a" to BlackboardValue.BoolValue(true),
                "b" to BlackboardValue.IntValue(2),
                "c" to BlackboardValue.FloatValue(2f),
                "d" to BlackboardValue.TextValue("two"),
                "e" to BlackboardValue.EntityValue(Entity(4294967297L)),
                "f" to BlackboardValue.VectorValue(1f, 2.5f, -3f),
            ),
            values,
        )
        assertEquals(values, Json.decodeFromString<Map<String, BlackboardValue>>(Json.encodeToString(values)))
        assertEquals("2.0", Json.encodeToString<BlackboardValue>(BlackboardValue.FloatValue(2f)))
        assertFailsWith<IllegalArgumentException> { Json.decodeFromString<BlackboardValue>("""{"w":1}""") }
        assertFailsWith<IllegalArgumentException> { Json.decodeFromString<BlackboardValue>("null") }
        assertFailsWith<IllegalArgumentException> { Json.decodeFromString<BlackboardValue>("9999999999") }
    }

    @Test
    fun anOlderFormatIsMigratedAndANewerOneRefused() {
        // A made-up format 0 called the root "start"; format 1 calls it "root".
        val older = """{"formatVersion":0,"start":{"kind":"ai.status","params":{"status":"success"}}}"""
        val renamed = AiFormatMigration(AiDocumentType.BehaviorTree, fromVersion = 0) { document ->
            JsonObject(document - "start" + ("root" to document.getValue("start")))
        }
        val migrated = AiDefinitionJson(listOf(renamed)).decodeTree(older)
        assertEquals(BehaviorTreeDefinition.FORMAT_VERSION, migrated.formatVersion)
        assertEquals(NodeKindId("ai.status"), migrated.root.kind)

        val noMigration = assertFailsWith<AiDefinitionException> { codec.decodeTree(older) }
        assertEquals("no migration reads formatVersion 0 to 1.", noMigration.problems.single().message)
        val newer = assertFailsWith<AiDefinitionException> { codec.decodeTree("""{"formatVersion":2,"root":{"kind":"ai.wait"}}""") }
        assertEquals(AiProblemCode.UNSUPPORTED_FORMAT, newer.problems.single().code)
        assertEquals(1, codec.decodeTree("""{"root":{"kind":"ai.wait"}}""").formatVersion)
    }

    @Test
    fun aMalformedDocumentSaysWhere() {
        val typo = assertFailsWith<AiDefinitionException> { codec.decodeTree("""{"root":{"kind":"ai.wait","parms":{}}}""") }
        val problem = typo.problems.single()
        assertEquals(AiProblemCode.MALFORMED, problem.code)
        assertTrue("unknown key 'parms'" in problem.message, problem.message)
        assertEquals(AiProblemCode.MALFORMED, assertFailsWith<AiDefinitionException> { codec.decodeTree("{") }.problems.single().code)
        assertEquals(AiProblemCode.MALFORMED, assertFailsWith<AiDefinitionException> { codec.decodeTree("[]") }.problems.single().code)
    }

    @Test
    fun aDefinitionWrittenInKotlinMatchesItsJsonParams() {
        val wait = NodeDefinition.of(BuiltInNodeKinds.Wait, DurationParams(2f))
        assertEquals(JsonObject(mapOf("seconds" to JsonPrimitive(2f))), wait.params)
        val parallel = NodeDefinition.of(BuiltInNodeKinds.Parallel, ParallelParams(ParallelPolicy.REQUIRE_ONE), listOf(wait))
        assertEquals("one", (parallel.params["policy"] as JsonPrimitive).content)
        assertEquals(parallel, Json.decodeFromString<NodeDefinition>(Json.encodeToString(parallel)))
        assertEquals(params("""{"policy":"one"}"""), Json.parseToJsonElement(Json.encodeToString(parallel)).jsonObject["params"])
    }
}
