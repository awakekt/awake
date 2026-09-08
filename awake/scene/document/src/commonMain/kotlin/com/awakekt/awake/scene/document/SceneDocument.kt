/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.Serializable

/** Current Awake scene document schema version integer (`1`). */
const val SCENE_SCHEMA_VERSION: Int = 1

/**
 * Root serializable representation of an Awake scene document (`.scene.json`).
 *
 * @property version Scene document schema version integer.
 * @property name Optional human-readable scene title.
 * @property nodes Hierarchy of top-level scene nodes.
 * @property extensions Associated scene extension data records.
 */
@Serializable
data class SceneDocument(
    val version: Int = SCENE_SCHEMA_VERSION,
    val name: String? = null,
    val nodes: List<SceneNode> = emptyList(),
    val extensions: List<SceneExtensionRecord> = emptyList(),
)
