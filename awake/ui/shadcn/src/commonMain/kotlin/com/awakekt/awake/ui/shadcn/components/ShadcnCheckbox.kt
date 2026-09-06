/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.Canvas
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.vector.rememberVectorPainter
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * `ShadcnCheckbox`: Selection toggle control for boolean choices.
 *
 * **Tailwind Reference**: `size-4 rounded-[4px] border border-primary bg-primary text-primary-foreground shadow-sm`.
 *
 * Use cases:
 * - Terms of service agreement, multi-selection list options, form checkboxes.
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnCheckbox(checked = isAgreed, onCheckedChange = { isAgreed = it })
 * ```
 *
 * @param checked Whether the checkbox is currently checked.
 * @param modifier Custom layout modifier.
 * @param enabled Whether the checkbox is interactive.
 * @param onCheckedChange Callback triggered when toggling the checked state.
 *
 * Keywords: checkbox, check, boolean input, selection toggle, form checkbox.
 */
context(_: Composer)
fun ShadcnCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }
    val checkPainter = rememberVectorPainter(ShadcnIcons.check)
    val next = checked

    Canvas(
        modifier
            .size(ShadcnCheckboxBoxSize)
            .hoverable(interaction, enabled = enabled)
            .clickable(interaction) { if (enabled) onCheckedChange(!checked) },
    ) {
        val radius = ShadcnCheckboxBoxRadius.value * density
        // `border border-input`, on both states -- `data-[state=checked]:border-primary` only
        // recolours it. Without this an unchecked box is `bg-background` on the page's own
        // background and therefore invisible, which is exactly how it first rendered.
        val border = ShadcnCheckboxBorderWidth.value * density
        withAlpha(if (enabled) 1f else ShadcnCheckboxDisabledAlpha) {
            drawRoundedRect(
                width = width.toFloat(),
                height = height.toFloat(),
                color = if (next) theme.palette.primary else theme.palette.input,
                radius = radius,
            )
        }
        if (next) {
            withAlpha(if (enabled) 1f else ShadcnCheckboxDisabledAlpha) {
                drawRoundedRect(
                    x = border,
                    y = border,
                    width = width - border * 2f,
                    height = height - border * 2f,
                    color = theme.palette.primary,
                    radius = (radius - border).coerceAtLeast(0f),
                )
                val inset = ShadcnCheckboxCheckInset.value * density
                checkPainter.draw(
                    this,
                    Rectangle(inset, inset, width - inset * 2f, height - inset * 2f),
                    theme.palette.primaryForeground,
                )
            }
        } else {
            drawRoundedRect(
                x = border,
                y = border,
                width = width - border * 2f,
                height = height - border * 2f,
                color = theme.palette.background,
                radius = (radius - border).coerceAtLeast(0f),
            )
        }
    }
}
