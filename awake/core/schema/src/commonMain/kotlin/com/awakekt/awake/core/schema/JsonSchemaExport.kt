/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("MatchingDeclarationName")

package com.awakekt.awake.core.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Options configuring how [PropertySchema] is converted into a standard JSON Schema (draft 2020-12).
 *
 * @property vector3Ref URI or `$defs` reference for Vector3 properties (e.g. `"#/$defs/vec3"`), or null to inline.
 * @property colorRef URI or `$defs` reference for Color properties (e.g. `"#/$defs/color"`), or null to inline.
 * @property includeHints Whether to emit non-standard or documentation hints (`description` from tooltip).
 */
data class JsonSchemaExportOptions(
    val vector3Ref: String? = null,
    val colorRef: String? = null,
    val includeHints: Boolean = true,
)

/**
 * Helper to build a [JsonObject] whose entries are sorted alphabetically by key.
 */
internal fun sortedJsonObject(entries: Map<String, JsonElement>): JsonObject {
    val sortedMap = LinkedHashMap<String, JsonElement>()
    entries.keys.sorted().forEach { key ->
        sortedMap[key] = entries.getValue(key)
    }
    return JsonObject(sortedMap)
}

/**
 * Converts this [PropertySchema] into a JSON Schema (draft 2020-12) representation as a [JsonObject].
 *
 * All keys in object properties and sub-schemas are sorted deterministically so that output is stable
 * and diffs cleanly.
 *
 * @param options Export options controlling reference mapping and metadata hints.
 * @return JSON Schema object fragment representing this property schema.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
fun PropertySchema.toJsonSchema(options: JsonSchemaExportOptions = JsonSchemaExportOptions()): JsonObject {
    val schemaMap = LinkedHashMap<String, JsonElement>()

    fun applyTypeAndConstraints() {
        when (kind) {
            PropertyKind.Float -> {
                schemaMap["type"] = JsonPrimitive("number")
                applyRangeConstraints(schemaMap, constraints.range)
            }
            PropertyKind.Int -> {
                schemaMap["type"] = JsonPrimitive("integer")
                applyRangeConstraints(schemaMap, constraints.range)
            }
            PropertyKind.Boolean -> {
                schemaMap["type"] = JsonPrimitive("boolean")
            }
            PropertyKind.Text -> {
                schemaMap["type"] = JsonPrimitive("string")
            }
            PropertyKind.Enum -> {
                schemaMap["type"] = JsonPrimitive("string")
                if (enumValues.isNotEmpty()) {
                    schemaMap["enum"] = buildJsonArray {
                        enumValues.forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
            PropertyKind.List -> {
                schemaMap["type"] = JsonPrimitive("array")
                element?.let { elemSchema ->
                    schemaMap["items"] = elemSchema.toJsonSchema(options)
                }
            }
            PropertyKind.Map -> {
                schemaMap["type"] = JsonPrimitive("object")
                element?.let { elemSchema ->
                    schemaMap["additionalProperties"] = elemSchema.toJsonSchema(options)
                }
            }
            PropertyKind.Vector3 -> {
                if (options.vector3Ref != null) {
                    schemaMap["\$ref"] = JsonPrimitive(options.vector3Ref)
                } else {
                    schemaMap["type"] = JsonPrimitive("object")
                    val props = mapOf<String, JsonElement>(
                        "x" to buildJsonObject { put("type", "number") },
                        "y" to buildJsonObject { put("type", "number") },
                        "z" to buildJsonObject { put("type", "number") },
                    )
                    schemaMap["properties"] = sortedJsonObject(props)
                    schemaMap["required"] = buildJsonArray {
                        add(JsonPrimitive("x"))
                        add(JsonPrimitive("y"))
                        add(JsonPrimitive("z"))
                    }
                    schemaMap["additionalProperties"] = JsonPrimitive(false)
                }
            }
            PropertyKind.Color -> {
                if (options.colorRef != null) {
                    schemaMap["\$ref"] = JsonPrimitive(options.colorRef)
                } else {
                    schemaMap["type"] = JsonPrimitive("object")
                    val props = mapOf<String, JsonElement>(
                        "a" to buildJsonObject { put("type", "number") },
                        "b" to buildJsonObject { put("type", "number") },
                        "g" to buildJsonObject { put("type", "number") },
                        "r" to buildJsonObject { put("type", "number") },
                    )
                    schemaMap["properties"] = sortedJsonObject(props)
                    schemaMap["required"] = buildJsonArray {
                        add(JsonPrimitive("a"))
                        add(JsonPrimitive("b"))
                        add(JsonPrimitive("g"))
                        add(JsonPrimitive("r"))
                    }
                    schemaMap["additionalProperties"] = JsonPrimitive(false)
                }
            }
            PropertyKind.Object -> {
                schemaMap["type"] = JsonPrimitive("object")
                if (children.isNotEmpty()) {
                    val props = LinkedHashMap<String, JsonElement>()
                    val requiredList = mutableListOf<String>()
                    children.sortedBy { it.name }.forEach { child ->
                        props[child.name] = child.toJsonSchema(options)
                        if (child.required) {
                            requiredList += child.name
                        }
                    }
                    schemaMap["properties"] = sortedJsonObject(props)
                    if (requiredList.isNotEmpty()) {
                        schemaMap["required"] = buildJsonArray {
                            requiredList.sorted().forEach { add(JsonPrimitive(it)) }
                        }
                    }
                }
                schemaMap["additionalProperties"] = JsonPrimitive(false)
            }
            PropertyKind.Polymorphic, PropertyKind.Unknown -> {
                // Open JSON payload (accepts any value / object)
            }
        }
    }

    if (nullable) {
        val nonNullSchema = copy(nullable = false).toJsonSchema(options)
        schemaMap["anyOf"] = buildJsonArray {
            add(nonNullSchema)
            add(buildJsonObject { put("type", "null") })
        }
    } else {
        applyTypeAndConstraints()
    }

    if (options.includeHints) {
        hints.tooltip?.let { schemaMap["description"] = JsonPrimitive(it) }
    }

    default?.takeIf { it !is JsonNull }?.let {
        schemaMap["default"] = it
    }

    return sortedJsonObject(schemaMap)
}

private fun applyRangeConstraints(target: MutableMap<String, JsonElement>, range: NumberRange?) {
    if (range == null) return
    range.min?.let { minVal ->
        if (range.exclusiveMin) {
            target["exclusiveMinimum"] = JsonPrimitive(minVal)
        } else {
            target["minimum"] = JsonPrimitive(minVal)
        }
    }
    range.max?.let { maxVal ->
        if (range.exclusiveMax) {
            target["exclusiveMaximum"] = JsonPrimitive(maxVal)
        } else {
            target["maximum"] = JsonPrimitive(maxVal)
        }
    }
}
