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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * `awake scene list` and `awake scene show`: a project's scenes, and one scene's nodes and components,
 * written to [out] for a person or, with [json], for a program.
 */
internal class SceneViews(private val out: Appendable, private val project: ProjectScenes, private val json: Boolean) {
    fun list() {
        val scenes = project.files().map { file -> file to runCatching { project.read(file) }.getOrNull() }
        if (json) {
            val array = buildJsonArray {
                scenes.forEach { (file, document) ->
                    add(
                        buildJsonObject {
                            put("file", project.relative(file))
                            put("name", document?.name)
                            put("nodes", document?.let { countNodes(project.tree(it)) })
                            put("readable", document != null)
                        },
                    )
                }
            }
            out.appendLine(PRETTY_JSON.encodeToString(JsonArray.serializer(), array))
        } else {
            scenes.forEach { (file, document) ->
                val detail = document?.let { "${it.name ?: "(unnamed)"}  ${countNodes(project.tree(it))} nodes" } ?: "can't be read as a scene"
                out.appendLine("${project.relative(file)}  $detail")
            }
        }
    }

    fun show(file: File) {
        val tree = project.tree(project.read(file))
        if (json) {
            out.appendLine(PRETTY_JSON.encodeToString(JsonObject.serializer(), tree))
        } else {
            out.appendLine("${(tree["name"] as? JsonPrimitive)?.content ?: "(unnamed)"}  ${project.relative(file)}")
            printNodes(tree.array("nodes"), depth = 1)
        }
    }

    private fun printNodes(nodes: List<JsonElement>, depth: Int) {
        nodes.forEachIndexed { index, element ->
            val node = element as? JsonObject ?: return@forEachIndexed
            out.appendLine("${INDENT.repeat(depth)}${node.segment(index)}")
            node.array("components").forEach { component ->
                val fields = (component as? JsonObject)?.filterKeys { it != COMPONENT_TYPE }.orEmpty()
                out.appendLine("${INDENT.repeat(depth + 1)}${component.typeName()} ${JsonObject(fields)}")
            }
            printNodes(node.array("children"), depth + 1)
        }
    }

    private fun countNodes(tree: JsonObject): Int {
        fun count(nodes: List<JsonElement>): Int =
            nodes.sumOf { 1 + count((it as? JsonObject)?.array("children").orEmpty()) }
        return count(tree.array("nodes"))
    }

    private companion object {
        const val INDENT = "  "
    }
}
