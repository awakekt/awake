/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Places a prefab at this node. [SceneDocument.withPrefabs] puts the prefab file's root under the
 * node, so the node's transform places it and the node's own components add to it. The node holds no
 * children of its own.
 *
 * @property path The prefab file (a [ScenePrefab] as JSON), relative to the project root.
 */
@Serializable
@SerialName("prefab_link")
data class ScenePrefabLink(val path: String) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (this@ScenePrefabLink.path.isBlank()) {
            add(SceneValidationIssue(path, "prefab_link.path must not be blank"))
        }
    }
}
