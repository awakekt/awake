/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

import com.awakekt.awake.nodegraph.NodeSpec

/** Whether a node changes game state or only shows something. A server skips [Presentation]. */
enum class Effect { Logic, Presentation }

/** The port types a blueprint node may declare. A graph kind that knows no others. */
object PortTypes {
    const val EXEC = "exec"
    const val BOOL = "bool"
    const val INT = "int"
    const val FLOAT = "float"
    const val STRING = "string"
    const val ENTITY = "entity"
    const val ASSET = "asset"

    internal val data = setOf(BOOL, INT, FLOAT, STRING, ENTITY, ASSET)
}

/**
 * One node type: its [spec], shown in the palette and checked by the validator, beside the code that
 * runs it, so the two cannot drift apart.
 *
 * Implement one of the four shapes. Node types are stateless singletons: everything that varies per
 * entity lives in slots the runtime owns and a [BlueprintContext] reads and writes.
 */
sealed interface BlueprintNode {
    val spec: NodeSpec
    val effect: Effect get() = Effect.Logic
}

/** Starts a chain when something happens; the runtime fires it by [spec] type. */
interface EventNode : BlueprintNode

/** Runs when execution reaches it, then continues from one of its execution outputs. */
interface ActionNode : BlueprintNode {
    fun run(ctx: BlueprintContext): Step
}

/** Computes its outputs when a node that reads them is about to run. No execution wires. */
interface PureNode : BlueprintNode {
    fun evaluate(ctx: BlueprintContext)
}

/**
 * An action that may take more than one tick. [run] starts it and returns [Step.Suspend] to wait;
 * the runtime then calls [poll] every tick until it continues or stops.
 *
 * State lives in [floatState] float slots the runtime reserves per node, not in the node, so a wait
 * allocates nothing.
 */
interface LatentNode : ActionNode {
    val floatState: Int get() = 0

    fun poll(ctx: BlueprintContext, delta: Float): Step

    /** Called when a pending wait is dropped, for example by a reload. */
    fun cancel(ctx: BlueprintContext) = Unit
}

/** What an action does next. Keep [Continue] instances in the node, so returning one allocates nothing. */
sealed interface Step {
    /** Continue from the execution output named [output]. */
    class Continue(val output: String) : Step

    /** Wait; the runtime polls a [LatentNode] until it continues. */
    data object Suspend : Step

    /** End this chain here. */
    data object Stop : Step
}
