/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ScenePrefab(
    val guid: String,
    val name: String? = null,
    val root: SceneNode,
) {
    fun toJson(json: Json = PrefabJson): String = json.encodeToString(this)

    companion object {
        val PrefabJson: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(
            jsonText: String,
            json: Json = PrefabJson,
        ): ScenePrefab = json.decodeFromString(serializer(), jsonText)
    }
}
