/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.builder.model

import kotlinx.serialization.Serializable

/**
 * Root document container for a UI builder layout file (.uilayout.json).
 */
@Serializable
data class UiLayoutDocument(
    val id: String,
    val name: String = "NewLayout",
    val rootNode: UiNode,
    val version: Int = 1,
) {
    companion object {
        fun createEmpty(name: String = "NewLayout"): UiLayoutDocument {
            val root = UiNode(
                id = "root",
                type = "Container.Column",
                style = UiStyleSpec(width = "fill", height = "fill", padding = "16.dp", gap = "8.dp"),
            )
            return UiLayoutDocument(id = "doc_root", name = name, rootNode = root)
        }
    }
}
