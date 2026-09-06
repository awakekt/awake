/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.builder.layout

import com.awakekt.awake.ui.builder.model.UiNode

/**
 * Engine that automatically cleans up and reflows layout trees into structured flex/grid containers.
 */
object MagicReflowEngine {
    /**
     * Formats loose children in a container into a clean Column/Row structure with consistent spacing.
     */
    fun reflowContainer(node: UiNode): UiNode {
        val updatedStyle = node.style.copy(
            padding = if (node.style.padding == "auto") "16.dp" else node.style.padding,
            gap = if (node.style.gap == "auto") "12.dp" else node.style.gap,
        )
        val formattedChildren = node.children.map { child ->
            if (child.children.isNotEmpty()) {
                reflowContainer(child)
            } else {
                child
            }
        }
        return node.copy(style = updatedStyle, children = formattedChildren)
    }
}
