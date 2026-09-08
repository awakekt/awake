/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Serializable link component referencing an external prefab asset.
 *
 * @property prefabGuid Prefab asset unique identifier GUID.
 * @property isRoot Whether this node acts as the root of the prefab hierarchy.
 */
@Serializable
@SerialName("prefab_link")
data class ScenePrefabLink(
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("prefab_guid", "prefabGuid")
    val prefabGuid: String,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("is_root", "isRoot")
    val isRoot: Boolean = true,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (prefabGuid.isBlank()) {
            add(SceneValidationIssue(path, "prefabLink.prefabGuid must not be blank"))
        }
    }
}
