/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.json.Json

/**
 * This document with each [ScenePrefabLink] node's prefab under it: the prefab's root becomes the
 * link node's only child. [read] returns a prefab file's JSON by its project-relative path; each file
 * is read and expanded once and shared by every node that links it. A prefab may link others.
 *
 * Returns this document unchanged when nothing links a prefab.
 *
 * @throws IllegalArgumentException when a link node has children of its own, or a prefab links
 * itself, directly or through others.
 */
suspend fun SceneDocument.withPrefabs(read: suspend (path: String) -> String): SceneDocument =
    withPrefabs(ScenePrefab.PrefabJson, read)

/**
 * [withPrefabs], decoding each prefab with [json]: a scoped component registry's `sceneJson()`, so a
 * prefab decodes only the components its project registers.
 */
suspend fun SceneDocument.withPrefabs(json: Json, read: suspend (path: String) -> String): SceneDocument {
    if (nodes.none(SceneNode::linksAPrefab)) return this
    val expanded = HashMap<String, SceneNode>()

    suspend fun expand(node: SceneNode, opening: List<String>): SceneNode {
        val link = node.components.firstNotNullOfOrNull { it as? ScenePrefabLink }
            ?: return node.copy(children = node.children.map { expand(it, opening) })
        require(node.children.isEmpty()) {
            "${node.name ?: link.path} links a prefab and has children of its own; put them in the prefab or beside it"
        }
        require(link.path !in opening) { "Prefab ${link.path} links itself: ${(opening + link.path).joinToString(" > ")}" }
        val root = expanded.getOrPut(link.path) { expand(ScenePrefab.fromJson(read(link.path), json).root, opening + link.path) }
        return node.copy(children = listOf(root))
    }

    return copy(nodes = nodes.map { expand(it, emptyList()) })
}

private fun SceneNode.linksAPrefab(): Boolean =
    components.any { it is ScenePrefabLink } || children.any(SceneNode::linksAPrefab)
