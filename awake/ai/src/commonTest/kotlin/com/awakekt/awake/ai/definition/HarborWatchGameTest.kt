/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiEventId
import com.awakekt.awake.ai.btree.BlackboardValue
import com.awakekt.awake.ai.btree.CompareOp
import com.awakekt.awake.ai.btree.ParallelPolicy
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A made-up stealth game: harbour guards patrol, grow suspicious at noise, raise the alarm, and are
 * knocked dazed by the player. The machine is written in Kotlin with typed parameters; "dazed" is a
 * status the game sets on the blackboard, not something Core knows.
 */
class HarborWatchGameTest {
    /** How far the intruder is from the guard, which the game updates. */
    private var intruderDistance = 100f

    @Serializable
    data class SeesParams(val range: Float)

    private val registry = AiRegistry()
        .condition("harbor.sees-intruder", SeesParams.serializer(), "Sees Intruder") { p, _ -> intruderDistance <= p.range }

    private fun compare(key: String, op: CompareOp, value: Float) =
        NodeDefinition.of(BuiltInNodeKinds.Compare, CompareParams(key, op, BlackboardValue.FloatValue(value)))

    private fun emit(event: String) = NodeDefinition.of(BuiltInNodeKinds.Emit, EmitParams(AiEventId(event)))

    private val dazed = NodeDefinition.of(BuiltInNodeKinds.HasKey, KeyParams("status.dazed"))

    private val guard = StateMachineDefinition(
        id = "harbor-guard",
        initial = "patrol",
        states = listOf(
            StateDefinition(
                "patrol",
                tree = NodeDefinition.of(
                    BuiltInNodeKinds.Parallel,
                    ParallelParams(ParallelPolicy.REQUIRE_ALL),
                    listOf(
                        NodeDefinition.of(
                            BuiltInNodeKinds.Sequence,
                            NoParams,
                            listOf(
                                NodeDefinition.of(BuiltInNodeKinds.Wait, DurationParams(1f)),
                                NodeDefinition.of(BuiltInNodeKinds.AddToValue, AddParams("route.step", BlackboardValue.IntValue(1))),
                            ),
                        ),
                        NodeDefinition.of(
                            BuiltInNodeKinds.Succeeder,
                            NoParams,
                            listOf(
                                NodeDefinition.of(
                                    BuiltInNodeKinds.Sequence,
                                    NoParams,
                                    listOf(NodeDefinition.of(BuiltInNodeKinds.Chance, ChanceParams(0.25f)), emit("guard.glance")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            StateDefinition("suspicious", enter = listOf(emit("guard.suspicious")), tree = NodeDefinition.of(BuiltInNodeKinds.Wait, DurationParams(2f)), loopTree = false),
            StateDefinition("alarm", enter = listOf(emit("guard.alarm"))),
            StateDefinition("dazed", exit = listOf(NodeDefinition.of(BuiltInNodeKinds.SetValue, SetParams("noise", BlackboardValue.FloatValue(0f))))),
        ),
        transitions = listOf(
            TransitionDefinition(to = "dazed", condition = dazed),
            TransitionDefinition(from = "dazed", to = "patrol", condition = NodeDefinition.of(BuiltInNodeKinds.Inverter, NoParams, listOf(dazed))),
            TransitionDefinition(from = "patrol", to = "alarm", condition = NodeDefinition(NodeKindId("harbor.sees-intruder"), params = params("""{"range":8}"""))),
            TransitionDefinition(from = "patrol", to = "suspicious", condition = compare("noise", CompareOp.GreaterOrEqual, 0.3f)),
            TransitionDefinition(from = "suspicious", to = "alarm", condition = compare("noise", CompareOp.GreaterOrEqual, 0.8f)),
            TransitionDefinition(from = "suspicious", to = "patrol", onTree = TreeOutcome.Success),
            TransitionDefinition(from = "alarm", to = "patrol", after = 5f),
        ),
    )

    @Test
    fun aGuardReactsToNoiseSightAndBeingDazed() {
        val compiled = registry.compileStateMachine(AiDefinitionJson.Default.decodeStateMachine(AiDefinitionJson.Default.encode(guard)))
        val machine = compiled.newStateMachine(seed = 1)
        val h = Harness(compiled.newBlackboard())
        val states = mutableListOf<String>()
        fun run(updates: Int) = repeat(updates) {
            h.update(machine)
            if (states.lastOrNull() != machine.currentState.name) states += machine.currentState.name
        }

        run(8)
        h.blackboard["noise"] = 0.4f
        run(1)
        h.blackboard["noise"] = 0.1f
        run(10) // Suspicious for two quiet seconds, then back to patrol.
        h.blackboard["noise"] = 0.9f
        run(2) // Suspicious, then the alarm.
        h.blackboard["status.dazed"] = true
        run(1)
        h.blackboard.remove("status.dazed")
        run(1)
        intruderDistance = 5f
        run(1)

        assertEquals(listOf("patrol", "suspicious", "patrol", "suspicious", "alarm", "dazed", "patrol", "alarm"), states)
        val events = h.events.drain().map { it.event.value }.filter { it != "guard.glance" }
        assertEquals(listOf("guard.suspicious", "guard.suspicious", "guard.alarm", "guard.alarm"), events)
        // Leaving "dazed" cleared the noise.
        assertEquals(0f, h.blackboard.get<Float>("noise"))
    }

    @Test
    fun guardsWithTheSameSeedGlanceTheSameAndOthersDiffer() {
        val compiled = registry.compileStateMachine(guard)
        fun glances(seed: Long): List<Int> {
            val machine = compiled.newStateMachine(seed)
            val h = Harness(compiled.newBlackboard())
            return (0 until 80).filter {
                h.update(machine)
                h.events.drain().any { event -> event.event.value == "guard.glance" }
            }
        }
        assertEquals(glances(7), glances(7))
        assertNotEquals(glances(7), glances(8))
    }
}
