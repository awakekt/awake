/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.animation.animateFloat
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.heightIn
import io.github.awakelab.awake.compose.foundation.style.rememberStyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.foundation.text.BasicTextField
import io.github.awakelab.awake.compose.foundation.text.TextFieldState
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.math2d.sp
import io.github.awakelab.awake.core.text.theme.TextStyle
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's multi-line field: `min-h-16 w-full rounded-md border border-input bg-transparent px-3 py-2`.
 *
 * **`min-h-16`, not `h-16`** -- upstream pairs it with `field-sizing-content`, so the box starts two
 * lines tall and grows with what is typed. `heightIn(min = )` is that rule; a fixed height would cap
 * a long note at two lines with no scroll.
 *
 * Shares its box with [ShadcnInput] -- see [fieldStyle] -- since the only differences upstream are
 * the height rule and the vertical pad.
 */
context(_: Composer)
fun ShadcnTextarea(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }
    val styleState = rememberStyleState(interaction, enabled = enabled)
    val focusRingAlpha = animateFloat(if (styleState.isFocused) 1f else 0f)
    val box = remember(theme) { theme.fieldStyle(verticalPadding = Tw.Spacing.s2) }

    BasicTextField(
        state = state,
        modifier = modifier.fillMaxWidth().heightIn(min = TextareaMinHeight)
            .focusRing(
                alpha = focusRingAlpha,
                width = FieldFocusRingWidth,
                color = theme.palette.ring.withAlpha(0.5f),
                cornerRadius = theme.radii.md,
            )
            .styleable(styleState, box),
        style = TextStyle(size = Tw.Text.sm, lineHeight = 20f.sp, color = theme.palette.foreground),
        interactionSource = interaction,
    )
}

/** shadcn's `min-h-16`. */
private val TextareaMinHeight: Dp = 64.dp
