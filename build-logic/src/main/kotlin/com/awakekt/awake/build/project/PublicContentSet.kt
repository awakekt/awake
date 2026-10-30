/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.project

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The project manifest's path, which is always public: `loadProject` reads it before anything else. */
internal const val PUBLIC_MANIFEST = "awake.project.json"

/**
 * Which of a project's [files] (project-relative paths) make up its public sign-in set: the manifest,
 * every file a glob in [globs] matches, every scene in [scenes], and everything those files name.
 *
 * A file is named by a JSON file in the set when one of the document's strings is its path, or the
 * part of one before a `#`, as a model's mesh is named; the files it names are followed in turn, so a
 * scene's prefabs and what they name are public too. This is the rule a scene switch uses to count a
 * scene's content ahead, so the public set holds what the sign-in scene loads. A file named only
 * inside a binary file, such as a texture a model embeds by its own relative path, is not followed: a
 * glob declares it.
 *
 * Throws [IllegalArgumentException] for a scene that is not one of [files], or a glob that matches
 * nothing, so a renamed file can't silently drop out of the public set.
 */
internal fun publicContentSet(root: File, files: List<String>, globs: List<String>, scenes: List<String>): Set<String> {
    if (globs.isEmpty() && scenes.isEmpty()) return emptySet()
    val known = files.toSet()
    val missingScenes = scenes.filterNot(known::contains)
    require(missingScenes.isEmpty()) { "Public scenes that are not project content: ${missingScenes.joinToString()}" }
    val matched = globs.associateWith { glob ->
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$glob")
        files.filter { matcher.matches(Paths.get(it)) }
    }
    val empty = matched.filterValues { it.isEmpty() }.keys
    require(empty.isEmpty()) { "Public globs that match no project content: ${empty.joinToString()}" }

    val selected = LinkedHashSet<String>()
    val pending = ArrayDeque<String>()
    fun add(path: String) {
        if (path in known && selected.add(path)) pending += path
    }
    if (PUBLIC_MANIFEST in known) selected += PUBLIC_MANIFEST
    matched.values.flatten().forEach(::add)
    scenes.forEach(::add)
    while (pending.isNotEmpty()) {
        val path = pending.removeFirst()
        if (!path.endsWith(".json")) continue
        val document = runCatching { Json.parseToJsonElement(root.resolve(path).readText()) }.getOrNull() ?: continue
        strings(document) { value -> listOf(value, value.substringBefore('#')).forEach(::add) }
    }
    return selected
}

private fun strings(element: JsonElement, visit: (String) -> Unit) {
    when (element) {
        is JsonPrimitive -> if (element.isString) visit(element.content)
        is JsonArray -> element.forEach { strings(it, visit) }
        is JsonObject -> element.values.forEach { strings(it, visit) }
    }
}
