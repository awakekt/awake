/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * A scene as the JSON tree its codec writes, edited without mutating it. A node is named by its path,
 * as Studio and SceneValidator name it: its name, or `#index` when it has none, joined with `/` from
 * the scene's top level down through `children`.
 */

private const val NODES = "nodes"
private const val CHILDREN = "children"

/** The node at [path], as the indices down `nodes` and then `children`, or a failure naming what's there instead. */
internal fun JsonObject.findNode(path: String): List<Int> {
    val segments = path.trim('/').split('/').filter(String::isNotEmpty)
    if (segments.isEmpty()) throw UsageException("an empty node path")
    val indices = ArrayList<Int>()
    var level: JsonArray = array(NODES)
    var where = "the scene"
    segments.forEach { segment ->
        val index = level.indexOfNode(segment) ?: throw CommandFailure(
            "$where has no node '$segment'; it has ${level.nodeNames().ifEmpty { listOf("none") }.joinToString(", ")}",
        )
        indices += index
        where = segment
        level = (level[index] as JsonObject).array(CHILDREN)
    }
    return indices
}

/** This scene with the node at [indices] replaced by [change]'s result, or removed when that is null. */
internal fun JsonObject.updateNode(indices: List<Int>, change: (JsonObject) -> JsonObject?): JsonObject =
    withArray(NODES) { nodes -> nodes.updateAt(indices, change) }

/** This scene with [node] added last under the node at [parent], or at the top level when [parent] is null. */
internal fun JsonObject.insertNode(parent: List<Int>?, node: JsonObject): JsonObject =
    if (parent == null) {
        withArray(NODES) { it + node }
    } else {
        updateNode(parent) { owner -> owner.withArray(CHILDREN) { it + node } }
    }

/** The node at [indices]. */
internal fun JsonObject.nodeAt(indices: List<Int>): JsonObject {
    var node = array(NODES)[indices.first()] as JsonObject
    indices.drop(1).forEach { node = node.array(CHILDREN)[it] as JsonObject }
    return node
}

/** A node's path segment: its name, or `#index` when it has none. */
internal fun JsonObject.segment(index: Int): String =
    (this["name"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) ?: "#$index"

private fun List<JsonElement>.updateAt(indices: List<Int>, change: (JsonObject) -> JsonObject?): List<JsonElement> {
    val index = indices.first()
    val node = this[index] as JsonObject
    val updated = if (indices.size == 1) change(node) else node.withArray(CHILDREN) { it.updateAt(indices.drop(1), change) }
    return if (updated == null) filterIndexed { i, _ -> i != index } else mapIndexed { i, element -> if (i == index) updated else element }
}

private fun JsonArray.indexOfNode(segment: String): Int? =
    indices.firstOrNull { (this[it] as? JsonObject)?.segment(it) == segment }

private fun JsonArray.nodeNames(): List<String> = mapIndexedNotNull { index, node -> (node as? JsonObject)?.segment(index) }
