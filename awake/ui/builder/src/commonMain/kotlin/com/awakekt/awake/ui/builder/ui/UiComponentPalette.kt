/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.builder.ui

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.builder.model.UiComponentRegistry
import com.awakekt.awake.ui.builder.model.UiComponentTemplate
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

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
