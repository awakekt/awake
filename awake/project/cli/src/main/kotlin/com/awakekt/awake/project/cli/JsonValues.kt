/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** This object with [value] at the field path [segments], creating the objects on the way. */
internal fun JsonObject.withField(segments: List<String>, value: JsonElement): JsonObject {
    val key = segments.first()
    val child = if (segments.size == 1) value else ((this[key] as? JsonObject) ?: JsonObject(emptyMap())).withField(segments.drop(1), value)
    return JsonObject(this + (key to child))
}

/** The value at the field path [segments], or null when there's none. */
internal fun JsonObject.fieldAt(segments: List<String>): JsonElement? =
    segments.fold(this as JsonElement?) { element, key -> (element as? JsonObject)?.get(key) }

/** [value] read as JSON when it is JSON, and as a string otherwise, so `4`, `true` and `{"x":1}` keep their types. */
internal fun parseValue(value: String): JsonElement =
    runCatching { Json.parseToJsonElement(value) }.getOrElse { JsonPrimitive(value) }

/** Whether [a] and [b] hold the same value, a whole number and its float spelling alike. */
internal fun sameValue(a: JsonElement?, b: JsonElement?): Boolean {
    if (a == b) return true
    val left = (a as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull?.toDoubleOrNull()
    val right = (b as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull?.toDoubleOrNull()
    return left != null && left == right
}

/** [element] as one line of JSON. */
internal fun compact(element: JsonElement?): String = when (element) {
    null -> "nothing"
    is JsonPrimitive -> if (element.isString) "\"${element.jsonPrimitive.content}\"" else element.content
    else -> element.toString()
}
