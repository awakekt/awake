/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.animation.animateFloat
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.RowScope
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.style.rememberStyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's input group: a container wrapping an input and its inline affixes (prefixes, suffixes,
 * buttons, or icons) inside one unified bordered control.
 *
 * Parity reference: `registry/new-york-v4/ui/input-group.tsx`.
 *
 * Focus state is managed on the outer group: focusing the inner text field animates the group's
 * outer `border-ring` and `ring-[3px] ring-ring/50`, preventing nested or disjoint focus rings.
 */
context(_: Composer)
fun ShadcnInputGroup(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    prefix: (
        context(Composer)
        RowScope.() -> Unit
    )? = null,
    suffix: (
        context(Composer)
        RowScope.() -> Unit
    )? = null,
    focusRingMode: ShadcnFocusRingMode = ShadcnFocusRingMode.DrawWithContent,
    onClick: (() -> Unit)? = null,
) {
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }
    val styleState = rememberStyleState(interaction, enabled = enabled)
    val focusRingAlpha = animateFloat(if (styleState.isFocused) 1f else 0f)
    val box = remember(theme, focusRingMode) {
        theme.fieldStyle(0.dp, focusRingMode)
    }

    Row(
        modifier = modifier.fillMaxWidth().height(ShadcnInputHeight)
            .focusRing(
                alpha = if (focusRingMode == ShadcnFocusRingMode.DrawWithContent) focusRingAlpha else 0f,
                width = FieldFocusRingWidth,
                color = theme.palette.ring.withAlpha(0.5f),
                cornerRadius = theme.radii.md,
            )
            .styleable(styleState, box),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix != null) {
            Box(Modifier.padding(start = Tw.Spacing.s3, end = Tw.Spacing.s1_5)) {
                prefix()
            }
        }
        BasicTextField(
            state = state,
            modifier = Modifier.weight(1f)
                .padding(
                    start = if (prefix == null) Tw.Spacing.s3 else 0.dp,
                    end = if (suffix == null) Tw.Spacing.s3 else 0.dp,
                    top = Tw.Spacing.s1,
                    bottom = Tw.Spacing.s1,
                ),
            style = TextStyle(size = Tw.Text.sm, lineHeight = 20f.sp, color = theme.palette.foreground),
            interactionSource = interaction,
            placeholder = placeholder,
            placeholderColor = theme.palette.mutedForeground,
            singleLine = true,
            onClick = onClick,
        )
        if (suffix != null) {
            Box(Modifier.padding(start = Tw.Spacing.s1_5, end = Tw.Spacing.s3)) {
                suffix()
            }
        }
    }
}
