/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.core.text.font.FontWeight
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/** Shadcn toast component. */
context(_: Composer)
fun shadcnToast(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val theme = shadcnTheme
    Box(
        modifier
            .background(theme.palette.card, theme.radii.lg)
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
