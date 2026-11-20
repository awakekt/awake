/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.platform.LocalFont
import com.awakekt.awake.compose.ui.platform.LocalFrameClock
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.theme.TextStyle

// PascalCase composables: Compose's own convention, and this is public API surface -- the same
// reason Constraints.Infinity keeps its casing. See 11-refinements.md rule 1.

private object TextNodeType
private object TextStateGroup

context(_: Composer)
/**
 * A run of text, sized from the font's own metrics.
 *
 * [style] merges over the inherited [LocalTextStyle] rather than replacing it, so setting a weight
 * does not silently drop an ancestor's size.
 *
 * Painted with the same run geometry the measure policy sizes with -- caret and glyphs computed by
 * different arithmetic drift a pixel per character, and the drift only shows at the end of a long
 * line where nobody is looking.
 *
 * Unchanged text is not re-shaped: the node's remembered [TextNodeState] keeps the last measurement,
 * glyph run, and motion state across frames, surviving modifier chain changes ahead of the text node.
 * Nor is it rebuilt: a composition that passes the same text, style and parameters as the last one
 * gets the last one's merged style, measure policy and modifier chain back, and allocates nothing.
 */
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    rememberGroup(TextStateGroup) {
        val state = remember { TextNodeState() }
        // Each of these is the last composition's own while its inputs are equal, so an unchanged node allocates
        // nothing here (#1813).
        val resolved = state.resolve(LocalTextStyle.current, style)
        val policy = state.policyFor(text, resolved, LocalFont.current, overflow, softWrap, maxLines)
        // Read for its frame number, which nothing observes: the motion's holds count frames (#1764).
        state.motion.clock = LocalFrameClock.current
        Layout(
            nodeType = TextNodeType,
            modifier = state.modifierFor(modifier, policy, resolved.color ?: DefaultTextColor),
            measurePolicy = policy,
            content = null,
        )
    }
}

/** Used when nothing in the chain provided one. Mid grey reads on both light and dark. */
internal val DefaultTextColor: Color = Color(0.9f, 0.9f, 0.92f, 1f)

/** The font a run measures and draws with: the style's own, or [ambient] (`LocalFont.current`), with [TextStyle.numerals] applied. */
internal fun TextStyle.resolveFont(ambient: UiFont): UiFont = (font ?: ambient).withNumerals(numerals)
