/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.scene.document.SceneValidator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Edits one scene file through the scene's own codec. An edit is made twice: to the scene as the codec
 * reads it, which decodes back into a scene to check every field's type and validate it, and to the
 * file as written, which is what's saved, so a hand-written scene keeps its names, its layout and the
 * fields it leaves at their defaults. An edit that leaves the scene unreadable, sets a field the
 * component doesn't have, or adds a validation error is refused and the file left alone.
 *
 * @param project The project the scene is in.
 * @param file The scene file.
 * @param dryRun Report what would change without writing it.
 */
internal class SceneEdits(private val project: ProjectScenes, private val file: File, private val dryRun: Boolean) {
    private val before = project.read(file)
    private val tree = project.tree(before)
    private val source = project.source(file)
    private val name = project.relative(file)

    /** Sets [assignment], `component.field=value` (or `transform.…`, `name`), on the node at [node]. */
    fun set(node: String, assignment: String): SceneEdit {
        val target = assignment.substringBefore('=', missingDelimiterValue = "")
        if (target.isEmpty()) throw UsageException("expected component.field=value, got '$assignment'")
        val value = parseValue(assignment.substringAfter('='))
        val field = fieldAt(node, target)
        return commit(
            edit = { scene -> field.set(scene, value) },
            inSource = field::setLike,
            verify = { after -> if (!sameValue(field.get(after), value)) throw CommandFailure("$node has no field $target to set") },
        )
    }

    /** Adds a node called [name], last under [parent], or at the scene's top level. */
    fun addNode(name: String, parent: String?): SceneEdit {
        val node = JsonObject(mapOf("name" to JsonPrimitive(name)))
        val under = parent?.let(tree::findNode)
        return commit({ scene -> scene.insertNode(under, node) })
    }

    /** Removes the node at [node], with its children. */
    fun removeNode(node: String): SceneEdit {
        val indices = tree.findNode(node)
        return commit({ scene -> scene.updateNode(indices) { null } })
    }

    /** Adds a component of [type] to the node at [node], with [fields], a JSON object, set on it. */
    fun addComponent(node: String, type: String, fields: String?): SceneEdit {
        val given = fields?.let { parseValue(it) as? JsonObject ?: throw UsageException("component fields must be a JSON object") }
        val component = JsonObject(mapOf(COMPONENT_TYPE to JsonPrimitive(type)) + given.orEmpty())
        val indices = tree.findNode(node)
        return commit({ scene -> scene.updateNode(indices) { owner -> owner.withComponents { it + component } } })
    }

    /** Removes the node at [node]'s component of [type]. */
    fun removeComponent(node: String, type: String): SceneEdit {
        val indices = tree.findNode(node)
        val index = tree.nodeAt(indices).componentIndex(type, node)
        return commit({ scene -> scene.updateNode(indices) { owner -> owner.withComponents { list -> list.filterIndexed { i, _ -> i != index } } } })
    }

    /** The field [target] names on the node at [node]: one of the node's own, or `component.field`. */
    private fun fieldAt(node: String, target: String): FieldAt {
        val segments = target.split('.')
        val indices = tree.findNode(node)
        return when {
            segments.first() in NODE_FIELDS -> FieldAt(indices, component = null, segments)
            segments.size < 2 -> throw UsageException("expected component.field=value, got '$target'")
            else -> FieldAt(indices, tree.nodeAt(indices).componentIndex(segments.first(), node), segments.drop(1))
        }
    }

    /**
     * Makes [edit] on the scene as the codec reads it, checks the result, and writes the edit made to the
     * file as written, by [inSource] given the edited scene the codec reads. [verify] sees that scene,
     * which drops a field the component doesn't have. When the file spells a field another way than the
     * codec, so the edit means something else there, the file is written as the codec writes it instead.
     */
    private fun commit(
        edit: (JsonObject) -> JsonObject,
        inSource: (source: JsonObject, expected: JsonObject) -> JsonObject = { source, _ -> edit(source) },
        verify: (JsonObject) -> Unit = {},
    ): SceneEdit {
        val after = decode(edit(tree))
        val expected = project.tree(after)
        verify(expected)
        val existing = SceneValidator.validate(before).toSet()
        val introduced = SceneValidator.validate(after).filterNot { it in existing }
        if (introduced.isNotEmpty()) {
            throw CommandFailure("$name would not validate:\n" + introduced.joinToString("\n") { "  ${it.path}: ${it.message}" })
        }
        val patched = inSource(source, expected)
        val inPlace = runCatching { project.tree(project.decode(patched)) == expected }.getOrDefault(false)
        val changes = sceneChanges(tree, expected)
        if (!dryRun && changes.isNotEmpty()) project.write(file, if (inPlace) patched else expected)
        return SceneEdit(changes, rewritten = changes.isNotEmpty() && !inPlace)
    }

    private fun decode(edited: JsonObject) = try {
        project.decode(edited)
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        // Any decode failure is a value of the wrong type or shape for its field.
        throw CommandFailure("$name would not be a valid scene: ${error.message}", error)
    }

    private companion object {
        val NODE_FIELDS = setOf("name", "transform")
    }
}

/**
 * The field at [field] on the node at [indices], or on that node's component at [component].
 */
private class FieldAt(val indices: List<Int>, val component: Int?, val field: List<String>) {
    fun get(scene: JsonObject): JsonElement? = holder(scene)?.fieldAt(field)

    fun set(scene: JsonObject, value: JsonElement): JsonObject = withHolder(scene) { it.withField(field, value) }

    /**
     * [source], the file as written, with the field set as [expected], the edited scene the codec reads,
     * spells it. A field new to an object that holds others may be one the file spells another way,
     * such as a colour's `x` for `r`, so that object is written as the codec spells it, leaving nothing
     * behind that reads differently from what was set.
     */
    fun setLike(source: JsonObject, expected: JsonObject): JsonObject {
        val canonical = holder(expected) ?: return source
        val parentPath = field.dropLast(1)
        return withHolder(source) { held ->
            val parent = if (parentPath.isEmpty()) held else held.fieldAt(parentPath) as? JsonObject
            // A node's own fields have one spelling, so only its transform's objects and components are rewritten.
            val mayHideAnother = parent != null && field.last() !in parent && parent.keys.any { it != COMPONENT_TYPE } &&
                (component != null || parentPath.isNotEmpty())
            when {
                !mayHideAnother -> held.withField(field, canonical.fieldAt(field) ?: return@withHolder held)
                parentPath.isEmpty() -> canonical
                else -> held.withField(parentPath, canonical.fieldAt(parentPath) ?: return@withHolder held)
            }
        }
    }

    private fun holder(scene: JsonObject): JsonObject? {
        val node = scene.nodeAt(indices)
        return if (component == null) node else node.array("components").getOrNull(component) as? JsonObject
    }

    private fun withHolder(scene: JsonObject, change: (JsonObject) -> JsonObject): JsonObject = scene.updateNode(indices) { node ->
        if (component == null) {
            change(node)
        } else {
            node.withComponents { list -> list.mapIndexed { i, c -> if (i == component) change(c as JsonObject) else c } }
        }
    }
}

/**
 * What an edit changed, a line each, and whether the whole file was written as the codec writes it
 * because the edit couldn't be made to the file as written.
 */
internal class SceneEdit(val changes: List<String>, val rewritten: Boolean)
