/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/** Shadcn toast card component. */
context(_: Composer)
fun ShadcnToast(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val theme = shadcnTheme
    Box(
        modifier
            .background(theme.palette.card, theme.radii.lg)
            .border(1f.dp, theme.palette.border, theme.radii.lg)
            .padding(Tw.Spacing.s4),
    ) {
        Column {
            if (title != null) {
                ShadcnText(title, variant = ShadcnTextVariant.Small, weight = FontWeight.SemiBold)
            }
            ShadcnText(message, variant = ShadcnTextVariant.Small, color = theme.palette.cardForeground)
        }
    }
}

/** Compatibility shim for [ShadcnToast]. */
@Deprecated(
    message = "Use PascalCase ShadcnToast instead",
    replaceWith = ReplaceWith("ShadcnToast(message, modifier, title)"),
)
context(_: Composer)
fun shadcnToast(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    ShadcnToast(message, modifier, title)
}
