/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.scene

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * One scene component as its spec file declares it, the one place its fields are written.
 *
 * The generator makes the scene document class ([sceneClass], saved as [serialName]) and the binding
 * that copies every field by name between it and the runtime [component], plus the field table of
 * the component's reference page. The runtime component stays hand-written, with the same field
 * names and types: the generated binding names each one, so a field it lacks fails to compile
 * instead of being dropped when a scene is saved.
 *
 * @property packageName Package of the generated classes, and of the runtime component.
 * @property sourcePath The spec file, named in what the generator reports.
 * @property serialName The component's name in a scene document, such as `canvas_element`.
 * @property sceneClass The generated document class, such as `SceneCanvasElement`.
 * @property component The hand-written runtime component, such as `CanvasElement`.
 * @property binding The generated binding object, such as `CanvasElementBinding`.
 * @property construction How the binding builds a [component] from a document.
 * @property validate A hand-written `SceneClass.validate…(path)` extension the document's
 *   `validate` returns, or null for a component with nothing to check beyond its types.
 * @property doc KDoc of the document class.
 * @property imports Fully qualified names the field types and defaults use from other packages.
 * @property fields Every saved field, in the order the document lists them.
 */
data class SceneComponentSpec(
    val packageName: String,
    val sourcePath: String,
    val serialName: String,
    val sceneClass: String,
    val component: String,
    val binding: String,
    val construction: Construction,
    val validate: String?,
    val doc: String,
    val imports: List<String>,
    val fields: List<SceneFieldSpec>,
) {
    /** How the binding builds a runtime component from a document. */
    enum class Construction {
        /** `Component()`, then each field set on it: a component of `var`s with defaults. */
        Properties,

        /** `Component(field = …, …)`: a component whose fields are constructor parameters. */
        Constructor,
    }

    companion object {
        /** Reads a spec from [text], reporting a problem against [sourcePath]. */
        fun parse(text: String, sourcePath: String): SceneComponentSpec {
            val spec = parseJson(text, sourcePath)
            val fields = spec.array("fields", sourcePath).map { SceneFieldSpec.parse(it.jsonObject, sourcePath) }
            require(fields.isNotEmpty()) { "$sourcePath: a component needs at least one field" }
            val repeated = fields.groupBy { it.name }.filterValues { it.size > 1 }.keys
            require(repeated.isEmpty()) { "$sourcePath: fields listed twice: ${repeated.joinToString()}" }
            val construction = spec.optionalString("construction") ?: "properties"
            return SceneComponentSpec(
                packageName = spec.string("package", sourcePath),
                sourcePath = sourcePath,
                serialName = spec.string("serialName", sourcePath),
                sceneClass = spec.string("sceneClass", sourcePath),
                component = spec.string("component", sourcePath),
                binding = spec.string("binding", sourcePath),
                construction = Construction.entries.firstOrNull { it.name.equals(construction, ignoreCase = true) }
                    ?: throw IllegalArgumentException("$sourcePath: construction is properties or constructor, not $construction"),
                validate = spec.optionalString("validate"),
                doc = spec.optionalString("doc") ?: "The saved form of a [${spec.string("component", sourcePath)}].",
                imports = spec.optionalArray("imports").map { (it as JsonPrimitive).content },
                fields = fields,
            )
        }
    }
}

/**
 * One saved field.
 *
 * @property name The field's name, the same in the document, the runtime component and the scene file.
 * @property type Its Kotlin type, as written in source: `Float`, `CanvasAnchor?`, `List<SceneVec3>`.
 * @property default Its default, as Kotlin source: `0f`, `CanvasAnchor.TopLeft`, `null`.
 * @property doc What it does, for the reference page's table and the document's KDoc.
 * @property range The bounds a `@PropertyRange` gives it, which the hand-written `validate` enforces.
 * @property docType The reference table's Type cell, where it can't be told from [type] (an enum's values).
 * @property docDefault The reference table's Default cell, where it can't be told from [default].
 */
data class SceneFieldSpec(
    val name: String,
    val type: String,
    val default: String,
    val doc: String,
    val range: FieldRange?,
    val docType: String?,
    val docDefault: String?,
) {
    companion object {
        internal fun parse(field: JsonObject, sourcePath: String): SceneFieldSpec {
            val name = field.string("name", sourcePath)
            val where = "$sourcePath, field $name"
            return SceneFieldSpec(
                name = name,
                type = field.string("type", where),
                default = field.string("default", where),
                doc = field.string("doc", where),
                range = (field["range"] as? JsonObject)?.let { FieldRange.parse(it, where) },
                docType = field.optionalString("docType"),
                docDefault = field.optionalString("docDefault"),
            )
        }
    }
}

/** The bounds of a numeric field, as `@PropertyRange` takes them. */
data class FieldRange(
    val min: Double?,
    val max: Double?,
    val exclusiveMin: Boolean,
    val exclusiveMax: Boolean,
) {
    companion object {
        internal fun parse(range: JsonObject, where: String): FieldRange {
            fun number(key: String) = range[key]?.let {
                requireNotNull((it as? JsonPrimitive)?.doubleOrNull) { "$where: range.$key is a number" }
            }
            fun flag(key: String) = range[key]?.let {
                requireNotNull((it as? JsonPrimitive)?.booleanOrNull) { "$where: range.$key is true or false" }
            } ?: false
            val known = setOf("min", "max", "exclusiveMin", "exclusiveMax")
            require(range.keys.all { it in known }) { "$where: range takes ${known.joinToString()}" }
            return FieldRange(number("min"), number("max"), flag("exclusiveMin"), flag("exclusiveMax"))
        }
    }
}

private fun parseJson(text: String, sourcePath: String): JsonObject =
    runCatching { Json.parseToJsonElement(text).jsonObject }
        .getOrElse { throw IllegalArgumentException("$sourcePath is not a JSON object: ${it.message}", it) }

private fun JsonObject.string(key: String, where: String): String =
    requireNotNull((this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content) { "$where: \"$key\" is a string, and is required" }

private fun JsonObject.optionalString(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.array(key: String, where: String): List<JsonElement> =
    requireNotNull(this[key] as? JsonArray) { "$where: \"$key\" is a list, and is required" }

private fun JsonObject.optionalArray(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()
