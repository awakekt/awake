/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.animation.animateFloat
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.style.rememberStyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * `ShadcnInput`: Single-line text input control field.
 *
 * **Tailwind Reference**: `h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors`.
 *
 * Use cases:
 * - Search inputs, login fields, user name entries, form text inputs.
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnInput(state = usernameState, placeholder = "m@example.com")
 * ```
 *
 * @param state State holder [TextFieldState] managing input text and selection.
 * @param modifier Custom layout modifier.
 * @param enabled Whether the input field is interactive.
 * @param placeholder Optional placeholder hint text when empty.
 * @param focusRingMode Focus indicator ring drawing mode.
 * @param onClick Optional click handler.
 *
 * Keywords: input, textfield, text input, single line input, search field, form input.
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
