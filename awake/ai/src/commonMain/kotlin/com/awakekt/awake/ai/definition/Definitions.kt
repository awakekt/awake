/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalSerializationApi::class)

package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiJson
import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.BlackboardValue
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A behaviour tree as data: what a game authors without Kotlin, saves in a project, and an editor
 * edits. [AiRegistry.compileTree] checks it and builds running trees from it.
 *
 * @property formatVersion The version of this document format. [AiDefinitionJson] migrates older ones.
 * @property id A name for the tree, for messages and editors.
 * @property blackboard The values a new agent's blackboard starts with.
 * @property root The root node.
 */
@Serializable
data class BehaviorTreeDefinition(
    @EncodeDefault val formatVersion: Int = FORMAT_VERSION,
    val id: String = "",
    val blackboard: Map<String, BlackboardValue> = emptyMap(),
    val root: NodeDefinition,
) {
    /** The format version. */
    companion object {
        /** The [formatVersion] this build writes; [AiDefinitionJson] migrates older ones to it. */
        const val FORMAT_VERSION: Int = 1
    }
}

/**
 * One node of a [BehaviorTreeDefinition]: a registered [kind], its parameters and its children.
 *
 * [params] is the kind's parameter object as JSON. The registry reads it into the kind's typed
 * parameters and refuses unknown, missing or out-of-range ones. Build one from typed parameters
 * with [of].
 *
 * @property kind The kind, as registered in an [AiRegistry].
 * @property id An id unique in the tree, which editors and [com.awakekt.awake.ai.btree.BehaviorTree.pathOf] use.
 * @property params The kind's parameters.
 * @property children The children, in order.
 * @property layout Where an editor places the node; the runtime ignores it.
 */
@Serializable
data class NodeDefinition(
    val kind: NodeKindId,
    val id: String? = null,
    val params: JsonObject = JsonObject(emptyMap()),
    val children: List<NodeDefinition> = emptyList(),
    val layout: NodeLayout? = null,
) {
    /** Typed construction. */
    companion object {
        /** A node of [kind] with [params] written by the kind's own serializer. */
        fun <P : Any> of(
            kind: NodeKind<P>,
            params: P,
            children: List<NodeDefinition> = emptyList(),
            id: String? = null,
        ): NodeDefinition =
            NodeDefinition(kind.id, id, AiJson.data.encodeToJsonElement(kind.params, params) as JsonObject, children)
    }
}

/**
 * Where an editor draws a node or state, in canvas units.
 *
 * @property x Horizontal position.
 * @property y Vertical position.
 */
@Serializable
data class NodeLayout(val x: Float, val y: Float)

/**
 * A state machine as data. [AiRegistry.compileStateMachine] checks it and builds running machines
 * from it.
 *
 * @property formatVersion The version of this document format.
 * @property id A name for the machine, for messages and editors.
 * @property initial The state a new machine starts in.
 * @property blackboard The values a new agent's blackboard starts with.
 * @property states The states, in order.
 * @property transitions The transitions. Each state asks its outgoing ones in this order and takes
 *   the first that fires.
 */
@Serializable
data class StateMachineDefinition(
    @EncodeDefault val formatVersion: Int = FORMAT_VERSION,
    val id: String = "",
    val initial: String,
    val blackboard: Map<String, BlackboardValue> = emptyMap(),
    val states: List<StateDefinition>,
    val transitions: List<TransitionDefinition> = emptyList(),
) {
    /** The format version. */
    companion object {
        /** The [formatVersion] this build writes; [AiDefinitionJson] migrates older ones to it. */
        const val FORMAT_VERSION: Int = 1
    }
}

/**
 * One state: a registered [kind] with its [params], plus behaviour any state may carry.
 *
 * On entering, the state's own enter runs, then each [enter] node is ticked once, in order. Each
 * update ticks [tree]. On leaving, each [exit] node is ticked once, then the state's own exit
 * runs. Enter and exit nodes are for instant actions, such as setting a blackboard value or
 * emitting an event; a node that is still running then is not ticked again.
 *
 * @property name The state's name, unique in the machine.
 * @property kind The kind, as registered in an [AiRegistry]; [StateKindId.Plain] by default.
 * @property params The kind's parameters.
 * @property enter Nodes ticked once on entering.
 * @property exit Nodes ticked once on leaving.
 * @property tree A tree ticked on every update while the state is active.
 * @property loopTree Whether [tree] starts over after it finishes. When false it is ticked until it
 *   finishes once per visit.
 * @property layout Where an editor places the state; the runtime ignores it.
 */
@Serializable
data class StateDefinition(
    val name: String,
    val kind: StateKindId = StateKindId.Plain,
    val params: JsonObject = JsonObject(emptyMap()),
    val enter: List<NodeDefinition> = emptyList(),
    val exit: List<NodeDefinition> = emptyList(),
    val tree: NodeDefinition? = null,
    val loopTree: Boolean = true,
    val layout: NodeLayout? = null,
)

/**
 * A transition to [to]. It fires once the machine has spent [after] seconds in the state, the
 * state's tree has finished as [onTree] says, and [condition] succeeds; each part left out is met.
 * At least one must be given.
 *
 * @property from The state it leaves, or `null` for every state other than [to].
 * @property to The state it enters.
 * @property after Seconds in the state before it can fire.
 * @property onTree How the state's tree must have last finished on this visit.
 * @property condition A node ticked to decide; it fires on [BehaviorStatus.SUCCESS].
 * @property layout Where an editor draws the transition; the runtime ignores it.
 */
@Serializable
data class TransitionDefinition(
    val from: String? = null,
    val to: String,
    val after: Float = 0f,
    val onTree: TreeOutcome? = null,
    val condition: NodeDefinition? = null,
    val layout: NodeLayout? = null,
)

/** How a state's tree finished, for [TransitionDefinition.onTree]. */
@Serializable
enum class TreeOutcome {
    /** It succeeded. */
    @SerialName("success")
    Success,

    /** It failed. */
    @SerialName("failure")
    Failure,

    /** It succeeded or failed. */
    @SerialName("finished")
    Finished,
    ;

    /** Whether a tree that last finished with [status] (`null`: not yet) matches this. */
    fun matches(status: BehaviorStatus?): Boolean =
        when (this) {
            Success -> status == BehaviorStatus.SUCCESS
            Failure -> status == BehaviorStatus.FAILURE
            Finished -> status == BehaviorStatus.SUCCESS || status == BehaviorStatus.FAILURE
        }
}
