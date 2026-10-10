/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * What changed from [before] to [after], two scene trees, one line each: a node or component added
 * or removed, or a field's old and new value, named `node/path:component.field`. By name rather than
 * by line, since Studio saves a scene on one line.
 */
internal fun sceneChanges(before: JsonObject, after: JsonObject): List<String> {
    val old = SceneEntries(before)
    val new = SceneEntries(after)
    val removed = old.parts.filterKeys { it !in new.parts }.values.outermost()
    val added = new.parts.filterKeys { it !in old.parts }.values.outermost()
    val inside = removed + added
    return buildList {
        removed.forEach { add("- ${it.label}") }
        added.forEach { add("+ ${it.label}") }
        (old.fields.keys + new.fields.keys).distinct()
            .filterNot { key -> inside.any { it.contains(key) } }
            .forEach { key ->
                val was = old.fields[key]
                val now = new.fields[key]
                if (!sameValue(was, now)) add("$key: ${compact(was)} -> ${compact(now)}")
            }
    }
}

/** A node or a component, which an added or removed one reports once rather than field by field. */
private class ScenePart(val key: String, val label: String, private val prefixes: List<String>) {
    fun contains(field: String) = prefixes.any(field::startsWith)
}

private fun Collection<ScenePart>.outermost(): List<ScenePart> = filter { part -> none { it !== part && it.contains(part.key) } }

/** Every field of every node and component in a scene, and each node and component itself. */
private class SceneEntries(scene: JsonObject) {
    val fields = LinkedHashMap<String, JsonElement?>()
    val parts = LinkedHashMap<String, ScenePart>()

    init {
        scene.array("nodes").forEachIndexed { index, child -> (child as? JsonObject)?.let { node(it, it.segment(index)) } }
    }

    private fun node(node: JsonObject, path: String) {
        parts[path] = ScenePart(path, "node $path", listOf("$path:", "$path/"))
        node.forEach { (key, value) -> if (key != "children" && key != "components") field("$path:$key", value) }
        val types = node.componentTypes()
        node.array("components").forEachIndexed { index, component ->
            val type = component.typeName() ?: "#$index"
            val name = if (types.count { it == type } > 1) "$type#$index" else type
            val key = "$path:$name"
            parts[key] = ScenePart(key, "component $name on $path", listOf("$key."))
            (component as? JsonObject)?.forEach { (field, value) -> if (field != COMPONENT_TYPE) field("$key.$field", value) }
        }
        node.array("children").forEachIndexed { index, child -> (child as? JsonObject)?.let { node(it, "$path/${it.segment(index)}") } }
    }

    private fun field(key: String, value: JsonElement) {
        // An object's fields are listed one by one; an array, such as a terrain's samples, is one value.
        if (value is JsonObject && value.isNotEmpty()) value.forEach { (child, inner) -> field("$key.$child", inner) } else fields[key] = value
    }
}
