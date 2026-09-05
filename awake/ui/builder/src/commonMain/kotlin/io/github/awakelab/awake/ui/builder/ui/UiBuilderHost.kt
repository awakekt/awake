/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.builder.ui

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.builder.model.UiNode
import io.github.awakelab.awake.ui.builder.state.UiBuilderContract
import io.github.awakelab.awake.ui.builder.state.UiBuilderStore
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant

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
