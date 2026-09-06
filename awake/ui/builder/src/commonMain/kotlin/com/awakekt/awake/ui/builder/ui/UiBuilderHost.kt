/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.builder.ui

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.builder.model.UiNode
import com.awakekt.awake.ui.builder.state.UiBuilderContract
import com.awakekt.awake.ui.builder.state.UiBuilderStore
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

/**
 * Top-level composite view organizing Palette, Canvas/Preview, and Inspector panels.
 */
context(_: Composer)
fun UiBuilderHost(
    store: UiBuilderStore,
    id: String = "uiBuilderHost",
) {
    val state = store.state.value
    val selectedNode = state.selectedNodeId?.let { state.document.rootNode.findNode(it) }

    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
    ) {
        // Top Toolbar
        ShadcnCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnText("Awake UI Builder - ${state.document.name}", variant = ShadcnTextVariant.H3)

                Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2)) {
                    ShadcnButton(
                        label = "Undo",
                        variant = ShadcnButtonVariant.Outline,
                        enabled = state.undoStack.isNotEmpty(),
                        onClick = { store.dispatch(UiBuilderContract.Intent.Undo) },
                    )
                    ShadcnButton(
                        label = "Redo",
                        variant = ShadcnButtonVariant.Outline,
                        enabled = state.redoStack.isNotEmpty(),
                        onClick = { store.dispatch(UiBuilderContract.Intent.Redo) },
                    )
                    ShadcnButton(
                        label = "Magic Reflow",
                        variant = ShadcnButtonVariant.Secondary,
                        onClick = { store.dispatch(UiBuilderContract.Intent.ApplyMagicReflow) },
                    )
                    ShadcnButton(
                        label = if (state.isLivePreview) "Edit Mode" else "Live Preview",
                        variant = if (state.isLivePreview) ShadcnButtonVariant.Default else ShadcnButtonVariant.Outline,
                        onClick = { store.dispatch(UiBuilderContract.Intent.ToggleLivePreview) },
                    )
                }
            }
        }

        // Main Workspace Panes
        if (state.isLivePreview) {
            ShadcnCard(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                UiLivePreviewNode(node = state.document.rootNode, isEditMode = false)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            ) {
                UiComponentPalette(
                    id = "$id.palette",
                    onInsertTemplate = { template ->
                        val targetParentId = state.selectedNodeId ?: state.document.rootNode.id
                        val newNode = UiNode(
                            id = "node_${template.type.lowercase().replace('.', '_')}_${state.document.rootNode.children.size + 1}",
                            type = template.type,
                            props = template.defaultProps,
                            style = template.defaultStyle,
                        )
                        store.dispatch(
                            UiBuilderContract.Intent.InsertWidget(
                                template = newNode,
                                targetParentId = targetParentId,
                                index = 999,
                            ),
                        )
                    },
                )

                UiBuilderCanvas(
                    rootNode = state.document.rootNode,
                    selectedNodeId = state.selectedNodeId,
                    modifier = Modifier.weight(1f),
                    id = "$id.canvas",
                    onSelectNode = { store.dispatch(UiBuilderContract.Intent.SelectNode(it)) },
                )

                UiInspectorPanel(
                    selectedNode = selectedNode,
                    id = "$id.inspector",
                    onDeleteNode = {
                        state.selectedNodeId?.let { store.dispatch(UiBuilderContract.Intent.DeleteNode(it)) }
                    },
                )
            }
        }
    }
}
