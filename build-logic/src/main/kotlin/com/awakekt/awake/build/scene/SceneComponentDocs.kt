/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.scene

/**
 * A component's field table on its reference page, made from its spec: one row per field, with its
 * type, default and what it does.
 *
 * The page keeps everything else (its lede, the components list, the guides); only the table under
 * the component's ``## `serialName` `` heading is the spec's.
 */
object SceneComponentDocs {
    /** The field table for [spec], one line per row, as the reference page shows it. */
    fun table(spec: SceneComponentSpec): String = buildString {
        appendLine("| Field | Type | Default | What it does |")
        appendLine("| --- | --- | --- | --- |")
        spec.fields.forEach { field ->
            appendLine("| `${field.name}` | ${typeCell(field, spec)} | ${defaultCell(field, spec)} | ${field.doc} |")
        }
    }

    /**
     * [page] with [spec]'s table in place of the one under its heading. Throws when the page has no
     * section for the component, or the section no table: those are written by hand.
     */
    fun replaceTable(page: String, spec: SceneComponentSpec): String {
        val lines = page.lines()
        val heading = lines.indexOfFirst { it.trim() == "## `${spec.serialName}`" }
        require(heading >= 0) { "the reference page has no ## `${spec.serialName}` section for ${spec.sourcePath}" }
        val sectionEnd = (heading + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        val start = (heading + 1 until sectionEnd).firstOrNull { lines[it].startsWith("| Field |") }
        requireNotNull(start) { "## `${spec.serialName}` on the reference page has no | Field | table for ${spec.sourcePath} to fill" }
        var end = start
        while (end < sectionEnd && lines[end].startsWith("|")) end++
        return (lines.subList(0, start) + table(spec).trimEnd('\n').lines() + lines.subList(end, lines.size)).joinToString("\n")
    }

    private fun typeCell(field: SceneFieldSpec, spec: SceneComponentSpec): String = field.docType ?: when (field.type.removeSuffix("?").substringBefore('<')) {
        "Float", "Double" -> "number"
        "Int", "Long" -> "integer"
        "Boolean" -> "boolean"
        "String" -> "string"
        "SceneVec3" -> "vector"
        "SceneColor" -> "color"
        "List", "Set" -> "list"
        "Map" -> "object"
        else -> throw IllegalArgumentException(
            "${spec.sourcePath}, field ${field.name}: give a docType for ${field.type}, such as its values for an enum",
        )
    }

    private fun defaultCell(field: SceneFieldSpec, spec: SceneComponentSpec): String = field.docDefault ?: run {
        val default = field.default.trim()
        when {
            default == "null" -> "none"
            default == "true" || default == "false" -> "`$default`"
            FLOAT.matches(default) -> "`${default.removeSuffix("f").removeSuffix("F").removeSuffix(".0")}`"
            INTEGER.matches(default) -> "`${default.removeSuffix("L")}`"
            default.startsWith("\"") && default.endsWith("\"") -> "`$default`"
            else -> throw IllegalArgumentException(
                "${spec.sourcePath}, field ${field.name}: give a docDefault for its default $default",
            )
        }
    }

    private val FLOAT = Regex("^-?\\d+(\\.\\d+)?[fF]$")
    private val INTEGER = Regex("^-?\\d+L?$")
}
