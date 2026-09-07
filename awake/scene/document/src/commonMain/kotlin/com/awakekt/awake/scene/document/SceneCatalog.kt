/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

/**
 * Entry descriptor in a scene catalog collection.
 *
 * @property id Unique catalog entry identifier.
 * @property displayName Human-readable display title.
 * @property sceneFilePath Associated scene file path.
 * @property document Associated parsed [SceneDocument].
 */
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

/**
 * Catalog manager holding available scenes in a project.
 */
class SceneCatalog {
    private val entriesById = linkedMapOf<String, SceneCatalogEntry>()

    /** Read-only list of all catalog entries. */
    val all: List<SceneCatalogEntry> get() = entriesById.values.toList()

    /** Registers a catalog [entry]. */
    fun register(entry: SceneCatalogEntry) {
        entriesById[entry.id] = entry
    }

    /** Decodes JSON string [json] and registers it under [id]. */
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

    /** Finds a catalog entry by [id]. */
    fun find(id: String): SceneCatalogEntry? = entriesById[id]

    /** Removes a catalog entry by [id]. */
    fun remove(id: String): Boolean = entriesById.remove(id) != null

    /** Clears all entries from the catalog. */
    fun clear() = entriesById.clear()
}
