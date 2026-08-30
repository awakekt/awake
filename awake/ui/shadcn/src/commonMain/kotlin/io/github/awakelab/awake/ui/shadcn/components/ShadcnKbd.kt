/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.heightIn
import io.github.awakelab.awake.compose.foundation.layout.widthIn
import io.github.awakelab.awake.compose.foundation.style.Style
import io.github.awakelab.awake.compose.foundation.style.StyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.ShadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * A keyboard key: `h-5 min-w-5 rounded-sm bg-muted px-1 text-xs font-medium text-muted-foreground`.
 *
 * **This port fixes a live parity failure.** `kbdStatesStyleMatchesShadcn` reported a corner radius
 * of 4 against upstream's 6, because the recipe this replaces used the `xs` step where upstream says
 * `rounded-sm`. On the default scale that is 4 against 6. The test was green for a long time only
 * because the vendored reference had drifted to match; re-vendoring turned it red, and this is the
 * fix rather than a re-baseline.
 *
 * `min-w-5` with `h-5` is what keeps a single-character key square instead of collapsing to the
 * width of its glyph.
 */
context(_: Composer)
fun shadcnKbd(
    key: String,
    modifier: Modifier = Modifier,
) {
    val theme = shadcnTheme
    val style = remember(theme) { theme.kbdStyle() }
    Box(modifier.height(KbdSize).widthIn(min = KbdSize).styleable(StyleState.Default, style)) {
        ShadcnText(key, variant = ShadcnTextVariant.Xs, color = theme.palette.mutedForeground)
    }
}

private fun ShadcnThemeValues.kbdStyle(): Style = Style {
    background(palette.muted)
    // `rounded-sm`, not the `xs` step the previous recipe used -- see the parity note above.
    cornerRadius(radii.sm)
    // `px-1` -- horizontal only. Applying it to both axes made the key 16 + 2*4 = 24 tall against
    // upstream's fixed `h-5`, which is what the style oracle caught once this fixture moved.
    contentPadding(horizontal = Tw.Spacing.s1, vertical = Tw.Spacing.s0)
}

/** shadcn's `h-5` and `min-w-5`. */
private val KbdSize: Dp = 20.dp
