/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.strings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A string table loaded from a flat or nested JSON object, as an ordered map from key to value.
 *
 * Nested objects flatten into dotted paths, so `{"menu": {"start": "Start"}}` and
 * `{"menu.start": "Start"}` load the same entry. Entries keep the file's order.
 */
class StringTable private constructor(val entries: Map<KeyPath, String>) {
    val size: Int get() = entries.size

    companion object {
        /**
         * Loads [json]. Every problem is collected and reported in one failure: a value that is not a
         * string, a key that is not valid under [strict], and a key reached twice (a flat and a nested
         * spelling of the same path). A repeated member inside one JSON object is not seen, because the
         * JSON parser keeps the last one.
         */
        fun parse(json: String, strict: Boolean = false): StringTable {
            val root = try {
                Json.parseToJsonElement(json)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("The string table is not valid JSON: ${e.message}", e)
            }
            require(root is JsonObject) { "The string table must be a JSON object at its top level." }
            val entries = LinkedHashMap<KeyPath, String>()
            val problems = mutableListOf<String>()
            flatten(root, "", strict, entries, problems)
            require(problems.isEmpty()) {
                "The string table has ${problems.size} problem(s):\n" + problems.joinToString("\n") { "  - $it" }
            }
            return StringTable(entries)
        }

        private fun flatten(
            node: JsonObject,
            prefix: String,
            strict: Boolean,
            into: MutableMap<KeyPath, String>,
            problems: MutableList<String>,
        ) {
            for ((name, value) in node) {
                val key = prefix + name
                when {
                    value is JsonObject && value.isEmpty() -> problems += "'$key' is an empty group."
                    value is JsonObject -> flatten(value, "$key.", strict, into, problems)
                    value is JsonPrimitive && value.isString -> add(key, value.content, strict, into, problems)
                    else -> problems += "'$key' must be a string, not ${describe(value)}."
                }
            }
        }

        private fun add(
            key: String,
            value: String,
            strict: Boolean,
            into: MutableMap<KeyPath, String>,
            problems: MutableList<String>,
        ) {
            val path = try {
                KeyPath.parse(key, strict)
            } catch (e: IllegalArgumentException) {
                problems += e.message.orEmpty()
                return
            }
            if (path in into) problems += "Duplicate key '$key'." else into[path] = value
        }

        private fun describe(element: JsonElement): String = when (element) {
            is JsonNull -> "null"
            is JsonPrimitive -> "a number or boolean"
            is JsonObject -> "an object"
            else -> "an array"
        }
    }
}
