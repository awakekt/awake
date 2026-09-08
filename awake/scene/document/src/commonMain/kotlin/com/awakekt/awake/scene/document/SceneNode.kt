/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.Serializable

/**
 * Serializable node in a scene hierarchy.
 *
 * @property name Optional node identifier name.
 * @property transform Local 3D transform spatial orientation.
 * @property components Attached serializable components.
 * @property children Nested child nodes in the hierarchy.
 * @property prefabGuid Associated prefab GUID if instantiated from a prefab.
 * @property overrides Property overrides applied over the linked prefab.
 */
@Serializable
data class SceneNode(
    val name: String? = null,
    val transform: SceneTransform = SceneTransform(),
    val components: List<SceneComponent> = emptyList(),
    val children: List<SceneNode> = emptyList(),
    val prefabGuid: String? = null,
    val overrides: List<ScenePropertyOverride> = emptyList(),
)

/**
 * Property override key-value pair for prefab instances.
 *
 * @property targetPath Property path identifier (e.g. `components[0].color`).
 * @property value Serialized string value to override.
 */
@Serializable
data class ScenePropertyOverride(
    val targetPath: String,
    val value: String,
)
