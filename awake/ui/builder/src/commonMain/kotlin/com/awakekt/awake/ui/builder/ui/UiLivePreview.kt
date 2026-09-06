/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.builder.ui

import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.ui.builder.model.UiNode
import com.awakekt.awake.ui.shadcn.components.ShadcnBadge
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnCheckbox
import com.awakekt.awake.ui.shadcn.components.ShadcnInput
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnSwitch
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * Renders an interactive live preview of a [UiNode] layout tree using `shadcn` recipes.
 */
context(_: Composer)
fun UiLivePreviewNode(
    node: UiNode,
    selectedNodeId: String? = null,
    isEditMode: Boolean = false,
    onSelectNode: (String) -> Unit = {},
) {
    val theme = shadcnTheme
    val isSelected = isEditMode && selectedNodeId == node.id
    val modifier = if (isSelected) {
        Modifier.border(2f.dp, theme.palette.ring, theme.radii.sm)
            .clickable { onSelectNode(node.id) }
    } else if (isEditMode) {
        Modifier.clickable { onSelectNode(node.id) }
    } else {
        Modifier
    }

    Box(modifier = modifier) {
        when (node.type) {
            "Container.Column", "Container.Card" -> {
                ShadcnCard {
                    Column {
                        node.children.forEach { child ->
                            UiLivePreviewNode(child, selectedNodeId, isEditMode, onSelectNode)
                        }
                    }
                }
            }

            "Container.Row" -> {
                ShadcnCard {
                    Row {
                        node.children.forEach { child ->
                            UiLivePreviewNode(child, selectedNodeId, isEditMode, onSelectNode)
                        }
                    }
                }
            }

            "Widget.ShadcnButton" -> {
                val label = node.props["label"] ?: "Button"
                ShadcnButton(label = label)
            }

            "Widget.ShadcnBadge" -> {
                val label = node.props["label"] ?: "Badge"
                ShadcnBadge(label = label)
            }

            "Widget.ShadcnInput" -> {
                val placeholder = node.props["placeholder"] ?: "Enter text..."
                val tfState = remember { TextFieldState(placeholder) }
                ShadcnInput(state = tfState, placeholder = placeholder)
            }

            "Widget.ShadcnSwitch" -> {
                ShadcnSwitch(checked = false, onCheckedChange = {})
            }

            "Widget.ShadcnCheckbox" -> {
                ShadcnCheckbox(checked = false, onCheckedChange = {})
            }

            "Widget.ShadcnText" -> {
                val text = node.props["text"] ?: ""
                ShadcnText(text = text)
            }

            "Widget.ShadcnSeparator" -> {
                ShadcnSeparator()
            }

            else -> {
                ShadcnText(text = "[${node.type}]")
            }
        }
    }
}
