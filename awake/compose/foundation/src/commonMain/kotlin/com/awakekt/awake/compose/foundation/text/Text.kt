/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.platform.LocalFont
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextStyle

// PascalCase composables: Compose's own convention, and this is public API surface -- the same
// reason Constraints.Infinity keeps its casing. See 11-refinements.md rule 1.

private object TextNodeType

/**
 * A run of text, sized from the font's own metrics.
 *
 * [style] merges over the inherited [LocalTextStyle] rather than replacing it, so setting a weight
 * does not silently drop an ancestor's size.
 *
 * Painted through `drawBehind`, using the same run geometry the measure policy sizes with -- caret
 * and glyphs computed by different arithmetic drift a pixel per character, and the drift only shows
 * at the end of a long line where nobody is looking.
 */
context(_: Composer)
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
) {
    val resolved = LocalTextStyle.current then style
    val font = LocalFont.current
    val policy = TextMeasurePolicy(text, resolved, font)
    Layout(
        nodeType = TextNodeType,
        modifier = modifier.drawBehind {
            policy.runFor(density, 1f).paint(this, resolved.color ?: DefaultTextColor)
        },
        measurePolicy = policy,
    )
}

/** Used when nothing in the chain provided one. Mid grey reads on both light and dark. */
internal val DefaultTextColor: Color = Color(0.9f, 0.9f, 0.92f, 1f)
