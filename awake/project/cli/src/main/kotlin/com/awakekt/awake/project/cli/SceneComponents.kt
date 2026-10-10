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

private const val COMPONENTS = "components"

/** The key a component object names its type under. */
internal const val COMPONENT_TYPE = "component"

/** The index of this node's one component of [type], or a failure when it has none or several. */
internal fun JsonObject.componentIndex(type: String, node: String): Int {
    val matches = array(COMPONENTS).withIndex().filter { (_, component) -> component.typeName() == type }
    return when (matches.size) {
        1 -> matches.single().index
        0 -> throw CommandFailure(
            "$node has no $type component; it has ${componentTypes().ifEmpty { listOf("none") }.joinToString(", ")}",
        )
        else -> throw CommandFailure("$node has ${matches.size} $type components; edit the scene file to tell them apart")
    }
}

/** This node with its components replaced by [change]'s result. */
internal fun JsonObject.withComponents(change: (List<JsonElement>) -> List<JsonElement>): JsonObject =
    withArray(COMPONENTS) { change(it) }

/** The type names of this node's components, in order. */
internal fun JsonObject.componentTypes(): List<String> = array(COMPONENTS).mapNotNull { it.typeName() }

internal fun JsonElement.typeName(): String? = ((this as? JsonObject)?.get(COMPONENT_TYPE) as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

/** This object with [change]'s result in place of its array at [key]. */
internal fun JsonObject.withArray(key: String, change: (List<JsonElement>) -> List<JsonElement>): JsonObject =
    JsonObject(this + (key to JsonArray(change(array(key)))))
