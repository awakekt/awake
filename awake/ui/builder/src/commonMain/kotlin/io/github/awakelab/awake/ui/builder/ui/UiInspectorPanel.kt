/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.builder.ui

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.builder.model.UiNode
import io.github.awakelab.awake.ui.builder.model.UiStyleSpec
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

/**
 * Inspector panel for viewing and editing selected node properties and styles.
 */
context(_: Composer)
fun UiInspectorPanel(
    selectedNode: UiNode?,
    id: String = "inspector",
    onUpdateProps: (Map<String, String>) -> Unit = {},
    onUpdateStyle: (UiStyleSpec) -> Unit = {},
    onDeleteNode: () -> Unit = {},
) {
    ShadcnCard(modifier = Modifier.width(240.dp).fillMaxHeight()) {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
            ShadcnText("Inspector", variant = ShadcnTextVariant.H3)
            ShadcnSeparator()

            if (selectedNode == null) {
                shadcnMuted("No node selected. Click any element on canvas to inspect.")
            } else {
                ShadcnText("Type: ${selectedNode.type}", variant = ShadcnTextVariant.Small)
                shadcnMuted("ID: ${selectedNode.id}")

                ShadcnSeparator()
                ShadcnText("Layout & Style", variant = ShadcnTextVariant.Large)
                ShadcnText("Padding: ${selectedNode.style.padding}", variant = ShadcnTextVariant.Muted)
                ShadcnText("Gap: ${selectedNode.style.gap}", variant = ShadcnTextVariant.Muted)

                ShadcnSeparator()
                ShadcnButton(
                    label = "Delete Element",
                    variant = ShadcnButtonVariant.Destructive,
                    onClick = onDeleteNode,
                )
            }
        }
    }
}
