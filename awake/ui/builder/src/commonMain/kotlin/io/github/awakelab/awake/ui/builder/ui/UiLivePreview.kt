/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.builder.ui

import io.github.awakelab.awake.compose.foundation.border
import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.text.TextFieldState
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.ui.builder.model.UiNode
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadge
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCheckbox
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInput
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSwitch
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

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
