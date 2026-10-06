/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Diagnostic error produced during JSON Schema validation.
 *
 * @property path JSON path to the erroneous element (e.g. `"/nodes/0/components/1/speed"`).
 * @property message Description of why validation failed.
 */
data class JsonSchemaValidationError(
    val path: String,
    val message: String,
)

/**
 * Lightweight, dependency-free validator for JSON Schema (Draft 2020-12) documents.
 *
 * Validates arbitrary [JsonElement] data against a root schema and its `$defs`.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount")
class JsonSchemaValidator(private val rootSchema: JsonObject) {

    private val defs: Map<String, JsonElement> =
        (rootSchema["\$defs"] as? JsonObject) ?: emptyMap()

    /**
     * Validates [data] against the schema.
     *
     * @return List of validation errors (empty if valid).
     */
    fun validate(data: JsonElement): List<JsonSchemaValidationError> {
        val errors = mutableListOf<JsonSchemaValidationError>()
        validateElement(data, rootSchema, "", errors)
        return errors
    }

    private fun resolveSchema(schema: JsonElement): JsonObject? {
        if (schema !is JsonObject) return null
        val ref = schema["\$ref"]?.let { (it as? JsonPrimitive)?.content }
        if (ref != null) {
            val defPrefix = "#/\$defs/"
            if (ref.startsWith(defPrefix)) {
                val defName = ref.removePrefix(defPrefix)
                val resolved = defs[defName]
                return if (resolved != null) resolveSchema(resolved) else null
            }
            return null
        }
        return schema
    }

    private fun validateElement(
        data: JsonElement,
        rawSchema: JsonElement,
        path: String,
        errors: MutableList<JsonSchemaValidationError>,
    ) {
        val schema = resolveSchema(rawSchema) ?: return

        // 1. type checking
        val expectedType = (schema["type"] as? JsonPrimitive)?.content
        if (expectedType != null) {
            when (expectedType) {
                "null" -> if (data !is JsonNull) {
                    errors += JsonSchemaValidationError(path, "Expected null but found $data")
                    return
                }
                "boolean" -> if (data !is JsonPrimitive || data.booleanOrNull == null) {
                    errors += JsonSchemaValidationError(path, "Expected boolean but found $data")
                    return
                }
                "string" -> {
                    if (data !is JsonPrimitive || !data.isString) {
                        errors += JsonSchemaValidationError(path, "Expected string but found $data")
                        return
                    }
                    val text = data.content
                    (schema["minLength"] as? JsonPrimitive)?.longOrNull?.let { minLen ->
                        if (text.length < minLen) {
                            errors += JsonSchemaValidationError(path, "String '$text' shorter than minLength $minLen")
                        }
                    }
                    (schema["pattern"] as? JsonPrimitive)?.content?.let { pattern ->
                        if (!Regex(pattern).containsMatchIn(text)) {
                            errors += JsonSchemaValidationError(path, "String '$text' does not match pattern '$pattern'")
                        }
                    }
                }
                "integer" -> {
                    if (data !is JsonPrimitive || data.longOrNull == null) {
                        errors += JsonSchemaValidationError(path, "Expected integer but found $data")
                        return
                    }
                    validateNumericRange(data.longOrNull!!.toDouble(), schema, path, errors)
                }
                "number" -> {
                    if (data !is JsonPrimitive || data.doubleOrNull == null) {
                        errors += JsonSchemaValidationError(path, "Expected number but found $data")
                        return
                    }
                    validateNumericRange(data.doubleOrNull!!, schema, path, errors)
                }
                "array" -> {
                    if (data !is JsonArray) {
                        errors += JsonSchemaValidationError(path, "Expected array but found $data")
                        return
                    }
                    (schema["minItems"] as? JsonPrimitive)?.longOrNull?.let { minItems ->
                        if (data.size < minItems) {
                            errors += JsonSchemaValidationError(path, "Array size ${data.size} < minItems $minItems")
                        }
                    }
                    (schema["maxItems"] as? JsonPrimitive)?.longOrNull?.let { maxItems ->
                        if (data.size > maxItems) {
                            errors += JsonSchemaValidationError(path, "Array size ${data.size} > maxItems $maxItems")
                        }
                    }
                    schema["items"]?.let { itemsSchema ->
                        data.forEachIndexed { index, item ->
                            validateElement(item, itemsSchema, "$path/$index", errors)
                        }
                    }
                }
                "object" -> {
                    if (data !is JsonObject) {
                        errors += JsonSchemaValidationError(path, "Expected object but found $data")
                        return
                    }
                    validateJsonObject(data, schema, path, errors)
                }
            }
        } else if (data is JsonObject) {
            validateJsonObject(data, schema, path, errors)
        }

        // 2. const
        schema["const"]?.let { expectedConst ->
            if (data != expectedConst) {
                errors += JsonSchemaValidationError(path, "Expected constant $expectedConst but was $data")
                return
            }
        }

        // 3. enum
        (schema["enum"] as? JsonArray)?.let { enumVals ->
            if (data !in enumVals) {
                errors += JsonSchemaValidationError(path, "Value $data not in enum $enumVals")
                return
            }
        }

        // 3. anyOf
        (schema["anyOf"] as? JsonArray)?.let { anyOfList ->
            val branchErrors = mutableListOf<List<JsonSchemaValidationError>>()
            var matched = false
            for (branch in anyOfList) {
                val subErrors = mutableListOf<JsonSchemaValidationError>()
                validateElement(data, branch, path, subErrors)
                if (subErrors.isEmpty()) {
                    matched = true
                    break
                }
                branchErrors += subErrors
            }
            if (!matched) {
                errors += JsonSchemaValidationError(
                    path,
                    "Value $data did not match any allowed branch of anyOf: ${branchErrors.flatten().map { it.message }}",
                )
                return
            }
        }

        // 4. oneOf
        (schema["oneOf"] as? JsonArray)?.let { oneOfList ->
            var matchCount = 0
            for (branch in oneOfList) {
                val subErrors = mutableListOf<JsonSchemaValidationError>()
                validateElement(data, branch, path, subErrors)
                if (subErrors.isEmpty()) {
                    matchCount++
                }
            }
            if (matchCount != 1) {
                errors += JsonSchemaValidationError(path, "Value matched $matchCount branches of oneOf, expected 1")
                return
            }
        }
    }

    private fun validateNumericRange(
        num: Double,
        schema: JsonObject,
        path: String,
        errors: MutableList<JsonSchemaValidationError>,
    ) {
        (schema["minimum"] as? JsonPrimitive)?.doubleOrNull?.let { min ->
            if (num < min) {
                errors += JsonSchemaValidationError(path, "Value $num is less than minimum $min")
            }
        }
        (schema["exclusiveMinimum"] as? JsonPrimitive)?.doubleOrNull?.let { exMin ->
            if (num <= exMin) {
                errors += JsonSchemaValidationError(path, "Value $num is not strictly greater than exclusiveMinimum $exMin")
            }
        }
        (schema["maximum"] as? JsonPrimitive)?.doubleOrNull?.let { max ->
            if (num > max) {
                errors += JsonSchemaValidationError(path, "Value $num is greater than maximum $max")
            }
        }
        (schema["exclusiveMaximum"] as? JsonPrimitive)?.doubleOrNull?.let { exMax ->
            if (num >= exMax) {
                errors += JsonSchemaValidationError(path, "Value $num is not strictly less than exclusiveMaximum $exMax")
            }
        }
    }

    private fun validateJsonObject(
        data: JsonObject,
        schema: JsonObject,
        path: String,
        errors: MutableList<JsonSchemaValidationError>,
    ) {
        val properties = (schema["properties"] as? JsonObject) ?: emptyMap()
        val required = (schema["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

        for (req in required) {
            if (req !in data) {
                errors += JsonSchemaValidationError(path, "Missing required property '$req'")
            }
        }

        val additionalPropsSchema = schema["additionalProperties"]
        for ((key, value) in data) {
            val propSchema = properties[key]
            if (propSchema != null) {
                validateElement(value, propSchema, "$path/$key", errors)
            } else {
                when (additionalPropsSchema) {
                    null, is JsonObject -> {
                        if (additionalPropsSchema is JsonObject) {
                            validateElement(value, additionalPropsSchema, "$path/$key", errors)
                        }
                    }
                    else -> {
                        val allow = (additionalPropsSchema as? JsonPrimitive)?.booleanOrNull ?: true
                        if (!allow) {
                            errors += JsonSchemaValidationError(path, "Unknown property '$key' not allowed by schema")
                        }
                    }
                }
            }
        }
    }
}
