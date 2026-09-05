/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.builder.ui

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.builder.model.UiComponentRegistry
import io.github.awakelab.awake.ui.builder.model.UiComponentTemplate
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant

/**
 * Palette panel listing available component templates for drag and drop or insertion.
 */
context(_: Composer)
fun UiComponentPalette(
    id: String = "palette",
    onInsertTemplate: (UiComponentTemplate) -> Unit = {},
) {
    ShadcnCard(modifier = Modifier.width(220.dp).fillMaxHeight()) {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
            ShadcnText("Palette", variant = ShadcnTextVariant.H3)

            UiComponentRegistry.templates.forEach { template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInsertTemplate(template) },
                    horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShadcnText(template.displayName, variant = ShadcnTextVariant.Small)
                    ShadcnButton(
                        label = "+",
                        variant = ShadcnButtonVariant.Outline,
                        size = ShadcnButtonSizeVariant.Sm,
                        onClick = { onInsertTemplate(template) },
                    )
                }
            }
        }
    }
}
