/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.builder.ui

import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.ui.builder.model.UiNode
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant

/**
 * Edit mode canvas displaying the UI document structure with selection and drop target highlights.
 */
context(_: Composer)
fun UiBuilderCanvas(
    rootNode: UiNode,
    selectedNodeId: String?,
    modifier: Modifier = Modifier,
    id: String = "canvas",
    onSelectNode: (String) -> Unit = {},
) {
    ShadcnCard(modifier = modifier.fillMaxHeight()) {
        Column {
            ShadcnText("Canvas (Edit Mode)", variant = ShadcnTextVariant.H3)
            ShadcnSeparator()
            UiLivePreviewNode(
                node = rootNode,
                selectedNodeId = selectedNodeId,
                isEditMode = true,
                onSelectNode = onSelectNode,
            )
        }
    }
}
