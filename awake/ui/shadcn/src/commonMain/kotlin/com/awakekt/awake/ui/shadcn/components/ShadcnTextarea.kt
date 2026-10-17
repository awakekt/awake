/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.animation.animateFloat
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.heightIn
import com.awakekt.awake.compose.foundation.style.rememberStyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.isCompact
import com.awakekt.awake.ui.shadcn.theme.shadcnDensity
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's multi-line field: `min-h-16 w-full rounded-md border border-input bg-transparent px-3 py-2`.
 *
 * **`min-h-16`, not `h-16`** -- upstream pairs it with `field-sizing-content`, so the box starts two
 * lines tall and grows with what is typed. `heightIn(min = )` is that rule; a fixed height would cap
 * a long note at two lines with no scroll.
 *
 * Shares its box with [ShadcnInput] -- see [fieldStyle] -- since the only differences upstream are
 * the height rule and the vertical pad.
 *
 * Under `LocalShadcnDensity` `Compact` the box starts one line shorter: `min-h-12` with `px-2 py-1`.
 */
context(_: Composer)
fun ShadcnTextarea(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    rememberGroup(ShadcnTextareaGroup) {
        val theme = shadcnTheme
        val interaction = remember { InteractionSource() }
        val styleState = rememberStyleState(interaction, enabled = enabled)
        val focusRingAlpha = animateFloat(if (styleState.isFocused) 1f else 0f)
        val density = shadcnDensity
        val box = remember(theme, density) {
            theme.fieldStyle(verticalPadding = density.textareaPaddingY(), horizontalPadding = density.fieldPaddingX())
        }

        BasicTextField(
            state = state,
            modifier = modifier.fillMaxWidth().heightIn(min = if (density.isCompact) CompactTextareaMinHeight else TextareaMinHeight)
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
}

/** shadcn's `min-h-16`. */
private val TextareaMinHeight: Dp = 64.dp

/** `min-h-12`, the compact step. */
private val CompactTextareaMinHeight: Dp = 48.dp

/** Keys this component's `remember` slots as one group in its caller's node. */
private object ShadcnTextareaGroup
