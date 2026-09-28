/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.nodegraph.GraphIssueCode
import com.awakekt.awake.nodegraph.InvalidNodeGraphException
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlueprintRuntimeTest {
    private val log = ArrayList<String>()
    private val counted = Counted()
    private val nodes = BlueprintNodes.core()
        .register(Log(log))
        .register(Log(log, type = "test.sound", effect = Effect.Presentation))
        .register(counted)
        .register(Touched)
    private val interpreter = BlueprintInterpreter()

    private fun run(block: GraphBuilder.() -> Unit): BlueprintInstance =
        BlueprintInstance(BlueprintCompiler.compile(graph(block), nodes)).also(interpreter::start)

    @Test
    fun executionFollowsWiresInWireOrder() {
        run {
            node("start", "event.start")
            node("a", "test.log", "label" to "a")
            node("b", "test.log", "label" to "b")
            node("c", "test.log", "label" to "c")
            wire("start.then", "a.exec")
            wire("start.then", "b.exec")
            wire("a.then", "c.exec")
        }
        // Fan-out runs b after a's whole chain: a depth-first walk from the first wire.
        assertEquals(listOf("a", "c", "b"), log)
    }

    @Test
    fun anUnwiredInputUsesTheValueSetOnTheNode() {
        run {
            node("start", "event.start")
            node("log", "test.log", "label" to "hp", "amount" to 3.5f)
            wire("start.then", "log.exec")
        }
        assertEquals(listOf("hp=3.5"), log)
    }

    @Test
    fun dataIsReadWhenTheNodeRunsAndAPureNodeRunsOncePerStep() {
        run {
            node("start", "event.start")
            node("seven", "test.counted")
            node("sum", "math.add")
            node("log", "test.log", "label" to "sum")
            wire("start.then", "log.exec")
            wire("seven.value", "sum.a")
            wire("seven.value", "sum.b")
            wire("sum.sum", "log.amount")
        }
        assertEquals(listOf("sum=14.0"), log)
        assertEquals(1, counted.evaluations, "both of Add's inputs read one evaluation")
    }

    @Test
    fun branchChoosesItsOutputFromTheCondition() {
        run {
            node("start", "event.start")
            node("big", "math.greater", "a" to 5f, "b" to 2f)
            node("branch", "flow.branch")
            node("yes", "test.log", "label" to "yes")
            node("no", "test.log", "label" to "no")
            wire("start.then", "branch.exec")
            wire("big.result", "branch.condition")
            wire("branch.true", "yes.exec")
            wire("branch.false", "no.exec")
        }
        assertEquals(listOf("yes"), log)
    }

    @Test
    fun delayResumesOnTheRightTick() {
        val instance = run {
            node("start", "event.start")
            node("wait", "flow.delay", "seconds" to 0.05f)
            node("done", "test.log", "label" to "done")
            wire("start.then", "wait.exec")
            wire("wait.then", "done.exec")
        }
        repeat(2) { interpreter.tick(instance, TICK) }
        assertEquals(emptyList(), log, "0.05 s is three 1/60 s ticks")
        interpreter.tick(instance, TICK)
        assertEquals(listOf("done"), log)
        assertFalse(instance.isWaiting)
    }

    @Test
    fun aCancelledDelayNeverResumes() {
        val instance = run {
            node("start", "event.start")
            node("wait", "flow.delay", "seconds" to 0.05f)
            node("done", "test.log", "label" to "done")
            wire("start.then", "wait.exec")
            wire("wait.then", "done.exec")
        }
        interpreter.cancelPending(instance)
        repeat(10) { interpreter.tick(instance, TICK) }
        assertEquals(emptyList(), log)
    }

    @Test
    fun aDelayTriggeredAgainWhileWaitingRestarts() {
        val instance = run {
            node("touched", "test.touched")
            node("wait", "flow.delay", "seconds" to 1f)
            node("done", "test.log", "label" to "done")
            wire("touched.then", "wait.exec")
            wire("wait.then", "done.exec")
        }
        interpreter.fire(instance, "test.touched")
        repeat(30) { interpreter.tick(instance, TICK) }
        interpreter.fire(instance, "test.touched")

        repeat(59) { interpreter.tick(instance, TICK) }
        assertEquals(emptyList(), log, "the second trigger restarts the full second")
        repeat(2) { interpreter.tick(instance, TICK) }
        assertEquals(listOf("done"), log)
    }

    @Test
    fun anExecutionLoopThroughADelayIsAllowed() {
        val instance = run {
            node("start", "event.start")
            node("tick", "test.log", "label" to "tick")
            node("wait", "flow.delay", "seconds" to TICK)
            wire("start.then", "tick.exec")
            wire("tick.then", "wait.exec")
            wire("wait.then", "tick.exec")
        }
        repeat(3) { interpreter.tick(instance, TICK) }
        assertEquals(listOf("tick", "tick", "tick", "tick"), log)
    }

    @Test
    fun variablesAreSetAndRead() {
        val instance = run {
            node("start", "event.start")
            node("set", "var.set.float", "name" to "hp", "value" to 40f)
            node("get", "var.get.float", "name" to "hp")
            node("log", "test.log", "label" to "hp")
            wire("start.then", "set.exec")
            wire("set.then", "log.exec")
            wire("get.value", "log.amount")
        }
        assertEquals(listOf("hp=40.0"), log)
        assertEquals(40f, instance.variable("hp"))
    }

    @Test
    fun variablesAreSetFromJsonValuesOfTheirOwnType() {
        val instance = run {
            node("hp", "var.get.float", "name" to "hp")
            node("open", "var.get.bool", "name" to "open")
            node("label", "var.get.string", "name" to "label")
            node("target", "var.get.entity", "name" to "target")
        }
        instance.setVariable("hp", JsonPrimitive(12))
        instance.setVariable("open", JsonPrimitive(true))
        instance.setVariable("label", JsonPrimitive("gate"))
        assertEquals(listOf(12f, true, "gate"), listOf(instance.variable("hp"), instance.variable("open"), instance.variable("label")))

        assertFailsWith<IllegalArgumentException> { instance.setVariable("hp", JsonPrimitive("tall")) }
        assertFailsWith<IllegalArgumentException> { instance.setVariable("label", JsonPrimitive(3)) }
        assertFailsWith<IllegalArgumentException> { instance.setVariable("target", JsonPrimitive("door")) }
        assertFailsWith<IllegalArgumentException> { instance.setVariable("missing", JsonPrimitive(1)) }
    }

    @Test
    fun reloadKeepsMatchingVariablesCancelsWaitsAndDoesNotRefireStart() {
        val instance = run {
            node("start", "event.start")
            node("set", "var.set.float", "name" to "hp", "value" to 40f)
            node("setOld", "var.set.int", "name" to "removed", "value" to 1)
            node("wait", "flow.delay", "seconds" to 1f)
            node("late", "test.log", "label" to "late")
            wire("start.then", "set.exec")
            wire("set.then", "setOld.exec")
            wire("setOld.then", "wait.exec")
            wire("wait.then", "late.exec")
        }
        val reloaded = BlueprintCompiler.compile(
            graph {
                node("start", "event.start")
                node("started", "test.log", "label" to "started")
                node("get", "var.get.float", "name" to "hp")
                node("other", "var.get.string", "name" to "mood")
                wire("start.then", "started.exec")
            },
            nodes,
        )
        interpreter.reload(instance, reloaded)
        repeat(120) { interpreter.tick(instance, TICK) }

        assertEquals(40f, instance.variable("hp"), "hp kept its value across the reload")
        assertEquals(null, instance.variable("removed"), "a variable the new graph lacks is gone")
        assertEquals(emptyList(), log, "neither the cancelled wait nor On Start ran")
    }

    @Test
    fun withPresentationSkippedPresentationNodesPassExecutionThrough() {
        val serverInterpreter = BlueprintInterpreter(runPresentation = false)
        val program = BlueprintCompiler.compile(
            graph {
                node("start", "event.start")
                node("sound", "test.sound", "label" to "sound")
                node("logic", "test.log", "label" to "logic")
                wire("start.then", "sound.exec")
                wire("sound.then", "logic.exec")
            },
            nodes,
        )
        serverInterpreter.start(BlueprintInstance(program))
        assertEquals(listOf("logic"), log)
    }

    @Test
    fun theTraceListsExecutedNodesInOrder() {
        val program = BlueprintCompiler.compile(
            graph {
                node("start", "event.start")
                node("a", "test.log", "label" to "a")
                node("b", "test.log", "label" to "b")
                wire("start.then", "a.exec")
                wire("a.then", "b.exec")
            },
            nodes,
        )
        val instance = BlueprintInstance(program).apply { trace = BlueprintTrace() }
        interpreter.start(instance)
        assertEquals(listOf("start", "a", "b"), instance.trace!!.nodeIds(program))
    }

    @Test
    fun anEventPayloadReachesTheChain() {
        val instance = run {
            node("touched", "test.touched")
            node("set", "var.set.entity", "name" to "last")
            wire("touched.then", "set.exec")
            wire("touched.other", "set.value")
        }
        val other = Entity.of(7, 0)
        interpreter.fire(instance, "test.touched") { it.setEntity("other", other) }
        assertEquals(other, instance.variable("last"))
    }

    @Test
    fun aDataCycleIsRejected() {
        val failure = assertFailsWith<InvalidNodeGraphException> {
            BlueprintCompiler.compile(
                graph {
                    node("x", "math.add")
                    node("y", "math.add")
                    wire("x.sum", "y.a")
                    wire("y.sum", "x.a")
                },
                nodes,
            )
        }
        assertTrue(failure.issues.all { it.code == GraphIssueCode.CYCLE })
    }

    @Test
    fun anExecutionLoopWithNoWaitIsRejected() {
        val failure = assertFailsWith<InvalidNodeGraphException> {
            BlueprintCompiler.compile(
                graph {
                    node("a", "test.log")
                    node("b", "test.log")
                    wire("a.then", "b.exec")
                    wire("b.then", "a.exec")
                },
                nodes,
            )
        }
        assertTrue(failure.issues.all { it.code == GraphIssueCode.CYCLE })
    }

    @Test
    fun aVariableUsedAsTwoTypesIsRejected() {
        val failure = assertFailsWith<InvalidNodeGraphException> {
            BlueprintCompiler.compile(
                graph {
                    node("f", "var.get.float", "name" to "hp")
                    node("i", "var.get.int", "name" to "hp")
                },
                nodes,
            )
        }
        assertEquals(listOf(GraphIssueCode.INCOMPATIBLE_PORTS), failure.issues.map { it.code })
    }

    private companion object {
        const val TICK = 1f / 60f
    }
}
