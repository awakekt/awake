/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.document

data class SceneCatalogEntry(
    val id: String,
    val displayName: String,
    val sceneFilePath: String? = null,
    val document: SceneDocument? = null,
) {
    init {
        require(id.isNotBlank()) { "Scene catalog entry id must not be blank." }
        require(displayName.isNotBlank()) { "Scene catalog entry displayName must not be blank." }
    }
}

class SceneCatalog {
    private val entriesById = linkedMapOf<String, SceneCatalogEntry>()

    val all: List<SceneCatalogEntry> get() = entriesById.values.toList()

    fun register(entry: SceneCatalogEntry) {
        entriesById[entry.id] = entry
    }

    fun registerJson(id: String, displayName: String, json: String): SceneCatalogEntry {
        val doc = SceneLoader.decode(json)
        val entry = SceneCatalogEntry(
            id = id,
            displayName = doc.name ?: displayName,
            sceneFilePath = "assets/scenes/$id.scene.json",
            document = doc,
        )
        register(entry)
        return entry
    }

    fun find(id: String): SceneCatalogEntry? = entriesById[id]

    fun remove(id: String): Boolean = entriesById.remove(id) != null

    fun clear() = entriesById.clear()
}
