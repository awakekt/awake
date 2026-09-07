/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Reusable prefab asset template containing a root [SceneNode].
 *
 * @property guid Unique prefab asset GUID identifier.
 * @property name Optional prefab display name.
 * @property root Root template scene node hierarchy.
 */
@Serializable
data class ScenePrefab(
    val guid: String,
    val name: String? = null,
    val root: SceneNode,
) {
    /** Serializes this prefab to JSON format. */
    fun toJson(json: Json = PrefabJson): String = json.encodeToString(this)

    /** Companion static factory methods for prefabs. */
    companion object {
        /** Default JSON serializer for prefab assets. */
        val PrefabJson: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Deserializes a [ScenePrefab] from JSON string [jsonText]. */
        fun fromJson(
            jsonText: String,
            json: Json = PrefabJson,
        ): ScenePrefab = json.decodeFromString(serializer(), jsonText)
    }
}
