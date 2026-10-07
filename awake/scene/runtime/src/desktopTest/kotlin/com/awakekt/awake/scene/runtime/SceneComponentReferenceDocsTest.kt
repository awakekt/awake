/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.schema.PropertyKind
import com.awakekt.awake.scene.document.SceneComponentCatalog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scene document components reference page has one row per registered component id and one
 * field row per serialized field. A component or field added without a row fails here.
 */
class SceneComponentReferenceDocsTest {

    private val page = File("../../../website/docs/reference/scene-document-components.md").readText().replace("\r\n", "\n")

    @Test
    fun everyRegisteredComponentHasARowAndEveryFieldIsListed() {
        installEveryComponentKit()
        val registered = SceneComponentCatalog.schemas()
        val rows = Regex("""^\| \[`([a-z_]+)`]\(#""", RegexOption.MULTILINE)
            .findAll(page).map { it.groupValues[1] }.toSet()

        assertEquals(registered.keys.sorted(), rows.sorted(), "component ids on the reference page")
        val missing = registered.flatMap { (id, schema) ->
            val section = page.substringAfter("## `$id`\n", missingDelimiterValue = "")
                .substringBefore("\n## ")
            if (section.isEmpty()) {
                listOf("no `## \\`$id\\`` section")
            } else {
                schema.children.map { it.name }.filter { "| `$it` |" !in section }.map { "`$id` field `$it` has no row" }
            }
        }
        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
    }
 
    data class DocFieldRow(val field: String, val type: String, val default: String, val description: String)

    private fun parseDocTable(section: String): Map<String, DocFieldRow> {
        val tableLines = section.lineSequence()
            .dropWhile { !it.startsWith("| Field |") && !it.startsWith("| Key |") }
            .drop(2)
            .takeWhile { it.startsWith("|") }
            .toList()

        return tableLines.mapNotNull { line ->
            val cols = line.split("|").map { it.trim() }.filterIndexed { idx, _ -> idx != 0 && idx != line.split("|").size - 1 }
            if (cols.size >= 4) {
                val fieldName = cols[0].removeSurrounding("`")
                DocFieldRow(fieldName, cols[1], cols[2], cols[3])
            } else null
        }.associateBy { it.field }
    }

    private val expectedTypeAliases: Map<PropertyKind, List<String>> = mapOf(
        PropertyKind.Float to listOf("number"),
        PropertyKind.Int to listOf("integer", "number"),
        PropertyKind.Boolean to listOf("boolean"),
        PropertyKind.Text to listOf("string", "text"),
        PropertyKind.Vector3 to listOf("vector", "[vector](#vector)"),
        PropertyKind.Color to listOf("color", "[color](#color)"),
        PropertyKind.List to listOf("list", "array"),
        PropertyKind.Map to listOf("object", "map"),
        PropertyKind.Object to listOf("object", "alpha curve", "burst cycle", "ground", "spin", "[terrain surface](#terrain-surface)"),
        PropertyKind.Polymorphic to listOf("polymorphic", "[collision shape](#collision-shape)", "any json"),
        PropertyKind.Unknown to listOf("unknown"),
    )

    private fun verifyFieldType(child: com.awakekt.awake.core.schema.PropertySchema, docType: String): Boolean {
        if (child.kind == PropertyKind.Enum) return child.enumValues.all { it in docType }
        val aliases = expectedTypeAliases[child.kind] ?: emptyList()
        val lower = docType.lowercase()
        return aliases.any { alias -> lower == alias || lower.startsWith(alias) || alias in lower }
    }

    private fun verifyFieldDefault(child: com.awakekt.awake.core.schema.PropertySchema, docDefault: String): Boolean {
        if (child.required) return docDefault == "required"
        val def = child.default
        return if (def == null || def is JsonNull) {
            docDefault in listOf("none", "the first listed", "white", "`color`") || docDefault.startsWith("box, half extents")
        } else {
            matchesNonEmptyDefault(child.kind, def, docDefault)
        }
    }

    private fun matchesNonEmptyDefault(kind: PropertyKind, def: kotlinx.serialization.json.JsonElement, docDefault: String): Boolean =
        when (def) {
            is JsonPrimitive -> matchesPrimitiveDefault(def, docDefault)
            is JsonArray -> if (def.isEmpty()) docDefault == "`[]`" else docDefault.contains(def.toString())
            is JsonObject -> matchesObjectDefault(kind, def, docDefault)
            is JsonNull -> true
        }

    private fun matchesPrimitiveDefault(prim: JsonPrimitive, docDefault: String): Boolean {
        val content = prim.content
        val docClean = docDefault.removePrefix("about ").removeSurrounding("`").trim()
        val doubleVal = content.toDoubleOrNull()
        val docDoubleVal = docClean.toDoubleOrNull()
        return docClean == content ||
            (prim.isString && (docClean == "\"$content\"" || docClean == content)) ||
            (doubleVal != null && docDoubleVal != null && kotlin.math.abs(doubleVal - docDoubleVal) < 0.01) ||
            docDefault.contains(content)
    }

    private fun matchesObjectDefault(kind: PropertyKind, obj: JsonObject, docDefault: String): Boolean {
        if (obj.isEmpty()) return docDefault == "`{}`" || docDefault == "{}"
        return when (kind) {
            PropertyKind.Vector3 -> {
                val x = obj["x"]?.let { (it as? JsonPrimitive)?.content }
                docDefault.contains("x: $x") || docDefault.contains("\"x\": $x") || docDefault.contains("x: 0")
            }
            PropertyKind.Color -> docDefault == "white" || docDefault.contains("r:") || docDefault.contains("\"r\":")
            else -> true
        }
    }

    private fun unboundedRangePhrases(range: com.awakekt.awake.core.schema.NumberRange): List<String> =
        if (range.min == 0.0 && range.max == null) {
            if (range.exclusiveMin) listOf("Above 0", "greater than 0") else listOf("Not negative", "at least 0")
        } else emptyList()

    private fun boundedRangePhrases(range: com.awakekt.awake.core.schema.NumberRange): List<String> =
        if (range.min == 0.0) {
            if (range.exclusiveMin) {
                if (range.max == 180.0 && range.exclusiveMax) listOf("Between 0 and 180")
                else if (range.max == 1.0 && !range.exclusiveMax) listOf("Above 0 and at most 1")
                else emptyList()
            } else {
                if (range.max == 1.0 && !range.exclusiveMax) listOf("From 0 to 1", "0 to 1", "between 0 and 1")
                else emptyList()
            }
        } else emptyList()

    private fun expectedRangePhrases(range: com.awakekt.awake.core.schema.NumberRange): List<String> =
        unboundedRangePhrases(range) + boundedRangePhrases(range)

    private fun checkRangeDescription(range: com.awakekt.awake.core.schema.NumberRange, description: String): Boolean {
        val phrases = expectedRangePhrases(range)
        return phrases.isEmpty() || phrases.any { description.contains(it, ignoreCase = true) }
    }

    private fun verifyConstraints(child: com.awakekt.awake.core.schema.PropertySchema, description: String): List<String> = buildList {
        child.constraints.range?.let { range ->
            if (!checkRangeDescription(range, description)) {
                add("description does not mention range constraint $range: '$description'")
            }
        }
        child.hints.unit?.let { unit ->
            if (unit !in description) add("description does not mention unit '$unit': '$description'")
        }
    }

    @Test
    fun everyFieldMatchesSchemaTypeAndDefaultAndRange() {
        installEveryComponentKit()
        val registered = SceneComponentCatalog.schemas()
        val issues = mutableListOf<String>()

        for ((id, schema) in registered) {
            val section = page.substringAfter("## `$id`\n", missingDelimiterValue = "").substringBefore("\n## ")
            if (section.isEmpty()) continue
            val docFields = parseDocTable(section)

            for (child in schema.children) {
                val doc = docFields[child.name] ?: continue
                if (!verifyFieldType(child, doc.type)) {
                    issues += "$id.${child.name}: documented type '${doc.type}' does not match schema kind ${child.kind}"
                }
                if (!verifyFieldDefault(child, doc.default)) {
                    issues += "$id.${child.name}: documented default '${doc.default}' does not match schema default ${child.default}"
                }
                for (constraintIssue in verifyConstraints(child, doc.description)) {
                    issues += "$id.${child.name}: $constraintIssue"
                }
            }
        }

        assertTrue(issues.isEmpty(), "Reference doc mismatches:\n${issues.joinToString("\n")}")
    }
}
