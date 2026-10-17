/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.AiJson
import com.awakekt.awake.ai.Snapshottable
import com.awakekt.awake.ai.definition.NodeKindId
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement

/**
 * One running behaviour tree: its [root], every node in it by path, what each node returned on the
 * latest tick, and its state as data.
 *
 * A path names a node by where it sits: the root is `"0"`, and the third child of the root is
 * `"0.2"`. Paths are the same for every tree built from one definition, so a snapshot of one
 * restores into another, and an editor highlights the same node in each.
 *
 * Build one from a definition with
 * [com.awakekt.awake.ai.definition.AiRegistry.compileTree], or wrap a tree built in Kotlin with
 * [of]. A tree built from a definition records every node's status; one wrapped with [of] records
 * its root's only.
 */
class BehaviorTree internal constructor(
    /** The node [tick] ticks. */
    val root: BehaviorNode,
    /** Every node, depth first, root first. */
    val nodes: List<BehaviorTreeNode>,
    private val trace: TreeTrace,
) {
    private val slotByPath: Map<String, Int> = nodes.withIndex().associate { (slot, node) -> node.path to slot }

    /** How many times [tick] has run. */
    val tickCount: Long get() = trace.tick

    /** Ticks [root] once and returns its status. */
    fun tick(context: AiContext): BehaviorStatus {
        trace.tick++
        val status = root.tick(context)
        trace.record(ROOT_SLOT, status)
        return status
    }

    /** Resets [root], which resets the running branch. */
    fun reset() {
        root.reset()
    }

    /**
     * What the node at [path] returned on the latest tick, or `null` when it was not ticked then,
     * or its status is not recorded.
     */
    fun statusOf(path: String): BehaviorStatus? = slotByPath[path]?.let(trace::statusOf)

    /** The path of the node a definition gave [id], or `null`. */
    fun pathOf(id: String): String? = nodes.firstOrNull { it.id == id }?.path

    /** The paths of the nodes that returned [BehaviorStatus.RUNNING] on the latest tick, root first. */
    fun runningPaths(): List<String> =
        nodes.indices.filter { trace.statusOf(it) == BehaviorStatus.RUNNING }.map { nodes[it].path }

    /** The state every [Snapshottable] node keeps now, by path. */
    fun snapshot(): BehaviorTreeSnapshot =
        BehaviorTreeSnapshot(
            nodes = buildMap {
                for (entry in nodes) {
                    val node = entry.node as? Snapshottable<*> ?: continue
                    put(entry.path, node.encodeState())
                }
            },
        )

    /**
     * Puts back [snapshot], taken from this tree or another built from the same definition.
     *
     * @throws IllegalArgumentException naming each path that does not match: state for a node this
     *   tree does not have, a stateful node the snapshot leaves out, or state of the wrong shape.
     */
    fun restore(snapshot: BehaviorTreeSnapshot) {
        require(snapshot.formatVersion == BehaviorTreeSnapshot.FORMAT_VERSION) {
            "Behaviour tree snapshot formatVersion ${snapshot.formatVersion} is not supported; " +
                "expected ${BehaviorTreeSnapshot.FORMAT_VERSION}."
        }
        val problems = mutableListOf<String>()
        val stateful = nodes.mapNotNull { entry -> (entry.node as? Snapshottable<*>)?.let { entry.path to it } }.toMap()
        val pending = prepareRestores(stateful, snapshot.nodes, problems)
        require(problems.isEmpty()) { problems.joinToString("\n", prefix = "Cannot restore the behaviour tree:\n") { "- $it" } }
        pending.forEach { it() }
    }

    /** Wrapping trees built in Kotlin. */
    companion object {
        /** The path of the root. */
        const val ROOT_PATH: String = "0"

        private const val ROOT_SLOT = 0

        /**
         * Wraps [root], a tree built in Kotlin, finding its nodes through [BehaviorNode.children].
         * Only the root's status is recorded.
         */
        fun of(root: BehaviorNode): BehaviorTree {
            val nodes = mutableListOf<BehaviorTreeNode>()
            fun walk(node: BehaviorNode, path: String) {
                val inner = (node as? TracedNode)?.inner ?: node
                nodes += BehaviorTreeNode(path, inner)
                inner.children.forEachIndexed { index, child -> walk(child, "$path.$index") }
            }
            walk(root, ROOT_PATH)
            return BehaviorTree(root, nodes, TreeTrace(nodes.size))
        }
    }
}

/**
 * One node of a [BehaviorTree].
 *
 * @property path Where it sits; see [BehaviorTree].
 * @property node The node.
 * @property id The id its definition gave it, if any.
 * @property kind The kind it was built from, or `null` for a node built in Kotlin.
 */
data class BehaviorTreeNode(
    val path: String,
    val node: BehaviorNode,
    val id: String? = null,
    val kind: NodeKindId? = null,
)

/**
 * The state of a running [BehaviorTree], as data: what each stateful node keeps, by path. Blackboards
 * are snapshotted on their own, with [Blackboard.snapshot].
 *
 * @property formatVersion The version of this format.
 * @property nodes Each stateful node's state as JSON, by path.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BehaviorTreeSnapshot(
    @EncodeDefault val formatVersion: Int = FORMAT_VERSION,
    val nodes: Map<String, JsonElement> = emptyMap(),
) {
    /** The format version. */
    companion object {
        /** The only [formatVersion] this build reads and writes. */
        const val FORMAT_VERSION: Int = 1
    }
}

/** Records the status each node returned, and on which tick, without allocating. */
internal class TreeTrace(size: Int) {
    private val statuses = arrayOfNulls<BehaviorStatus>(size)
    private val ticks = LongArray(size) { -1L }
    var tick: Long = 0L

    fun record(slot: Int, status: BehaviorStatus) {
        statuses[slot] = status
        ticks[slot] = tick
    }

    fun statusOf(slot: Int): BehaviorStatus? = if (ticks[slot] == tick) statuses[slot] else null
}

/** Records [inner]'s status in [trace]; a tree built from a definition wraps every node in one. */
internal class TracedNode(
    val inner: BehaviorNode,
    private val slot: Int,
    private val trace: TreeTrace,
) : BehaviorNode {
    override val children: List<BehaviorNode> get() = inner.children

    override fun tick(context: AiContext): BehaviorStatus {
        val status = inner.tick(context)
        trace.record(slot, status)
        return status
    }

    override fun reset() {
        inner.reset()
    }
}

internal fun <S : Any> Snapshottable<S>.encodeState(): JsonElement =
    AiJson.data.encodeToJsonElement(stateSerializer, captureState())

/**
 * Reads each of [saved] into its own of [stateful], by key, and returns the restores to run once
 * nothing is wrong; anything that does not match goes to [problems] instead.
 */
internal fun prepareRestores(
    stateful: Map<String, Snapshottable<*>>,
    saved: Map<String, JsonElement>,
    problems: MutableList<String>,
): List<() -> Unit> {
    saved.keys.filter { it !in stateful }.forEach { problems += "$it: nothing there keeps state" }
    stateful.keys.filter { it !in saved }.forEach { problems += "$it: the snapshot has no state for it" }
    return stateful.filterKeys { it in saved }.mapNotNull { (key, target) ->
        try {
            target.prepareRestore(saved.getValue(key))
        } catch (e: SerializationException) {
            problems += "$key: the saved state does not fit: ${e.message?.lineSequence()?.first()}"
            null
        }
    }
}

internal fun <S : Any> Snapshottable<S>.prepareRestore(state: JsonElement): () -> Unit {
    val decoded = AiJson.data.decodeFromJsonElement(stateSerializer, state)
    return { restoreState(decoded) }
}
