/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.Canvas
import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.hoverable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.graphics.vector.rememberVectorPainter
import io.github.awakelab.awake.core.math2d.Rectangle
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's checkbox: `size-4 rounded-[4px] border border-input`, filling with `bg-primary` when
 * checked and showing a check glyph in `text-primary-foreground`.
 *
 * **The check mark is a registry icon, not hand-drawn coordinates.** The recipe this replaces built
 * one from transcribed points in `ShapePainter.drawCheckmark` while the icon registry already held
 * a generated vector -- which `awake-ui-authoring` records as a live example of a "second path", and
 * which also evaded the icons skill's hand-transcription ban purely by being typed `UiPath` rather
 * than an image vector. Drawing it from the registry closes both.
 *
 * Its radius is `rounded-[4px]`, an arbitrary value rather than a scale step, so it is a literal
 * here with the class beside it -- and notably *not* the theme's `sm`, which moves with the base
 * radius while upstream's 4px does not.
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
