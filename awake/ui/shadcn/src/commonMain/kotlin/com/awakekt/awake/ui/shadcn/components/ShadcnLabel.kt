/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * A form label: `text-sm leading-none font-medium`, dimmed when its control is disabled.
 *
 * Upstream's disabled styling is `peer-disabled:opacity-50` -- the label reacts to the *control's*
 * state, not its own, which is why [enabled] is a parameter here rather than something read from an
 * interaction source. A label has no interactions of its own to track.
 */
context(_: Composer)
fun ShadcnLabel(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = shadcnTheme
    ShadcnText(
        text,
        modifier = if (enabled) modifier else modifier.alpha(DISABLED_ALPHA),
        variant = ShadcnTextVariant.Small,
        color = theme.palette.foreground,
    )
}

/** `peer-disabled:opacity-50`. */
private const val DISABLED_ALPHA = 0.5f
