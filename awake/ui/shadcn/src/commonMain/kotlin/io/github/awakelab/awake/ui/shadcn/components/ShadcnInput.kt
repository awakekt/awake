/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.animation.animateFloat
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.style.rememberStyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.foundation.text.BasicTextField
import io.github.awakelab.awake.compose.foundation.text.TextFieldState
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.core.math2d.sp
import io.github.awakelab.awake.core.text.theme.TextStyle
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's single-line input: `h-9 w-full rounded-md border border-input bg-transparent px-3 py-1`.
 *
 * The type is `text-base` with a `md:text-sm` breakpoint -- 16 shrinking to 14 on a wider viewport.
 * There is no breakpoint system here, so this takes `text-sm`, matching what the component looks
 * like on the desktop widths every parity capture is taken at.
 */
context(_: Composer)
fun ShadcnInput(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    focusRingMode: ShadcnFocusRingMode = ShadcnFocusRingMode.DrawWithContent,
    onClick: (() -> Unit)? = null,
) {
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }
    val styleState = rememberStyleState(interaction, enabled = enabled)
    val focusRingAlpha = animateFloat(if (styleState.isFocused) 1f else 0f)
    val box = remember(theme, focusRingMode) {
        theme.fieldStyle(Tw.Spacing.s1, focusRingMode)
    }

    BasicTextField(
        state = state,
        modifier = modifier.fillMaxWidth().height(ShadcnInputHeight)
            .focusRing(
                alpha = if (focusRingMode == ShadcnFocusRingMode.DrawWithContent) focusRingAlpha else 0f,
                width = FieldFocusRingWidth,
                color = theme.palette.ring.withAlpha(0.5f),
                cornerRadius = theme.radii.md,
            )
            .styleable(styleState, box),
        style = TextStyle(size = Tw.Text.sm, lineHeight = 20f.sp, color = theme.palette.foreground),
        interactionSource = interaction,
        placeholder = placeholder,
        placeholderColor = theme.palette.mutedForeground,
        singleLine = true,
        onClick = onClick,
    )
}
