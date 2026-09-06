/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.builder.ui

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.ui.builder.model.UiNode
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

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
