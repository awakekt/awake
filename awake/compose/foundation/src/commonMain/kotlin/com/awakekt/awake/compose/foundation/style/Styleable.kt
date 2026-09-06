/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.style

import com.awakekt.awake.compose.foundation.BorderSides
import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.core.color.Color

/**
 * Resolves [styles] against [state] and applies the result as ordinary modifiers.
 *
 * Resolved at build time rather than through a node of its own, which is what a retained tree makes
 * possible: the state is known before the chain is assembled, so a style is just a way of writing
 * modifiers, not a new mechanism the layout engine has to understand.
 *
 * **Order is the box model, and it is not cosmetic.** External padding sits outside the background
 * so a neighbour is held off the painted edge; content padding sits inside it so a label is inset
 * from that same edge. Chain position decides which, since a draw link paints the box at its own
 * position -- so swapping the two would paint the background over the margin and butt the label
 * against the border.
 */
fun Modifier.styleable(state: StyleState, vararg styles: Style): Modifier =
    applyResolved(resolve(state, styles), BorderSides.All)

/** Resolves styles using a subset of a bordered node's perimeter. */
fun Modifier.styleable(state: StyleState, borderSides: BorderSides, vararg styles: Style): Modifier =
    applyResolved(resolve(state, styles), borderSides)

/** Runs the chain in order, so a later style wins over an earlier one on any property it sets. */
private fun resolve(state: StyleState, styles: Array<out Style>): ResolvedStyle {
    val resolved = ResolvedStyle()
    val scope = ResolvingStyleScope(state, resolved)
    for (style in styles) {
        with(style) { scope.applyStyle() }
    }
    return resolved
}

private fun Modifier.applyResolved(resolved: ResolvedStyle, borderSides: BorderSides): Modifier {
    var modifier = this
    if (resolved.externalPaddingHorizontal.value > 0f || resolved.externalPaddingVertical.value > 0f) {
        modifier = modifier.padding(
            horizontal = resolved.externalPaddingHorizontal,
            vertical = resolved.externalPaddingVertical,
        )
    }
    resolved.width?.let { modifier = modifier.width(it) }
    resolved.height?.let { modifier = modifier.height(it) }
    if (resolved.alpha < 1f) modifier = modifier.alpha(resolved.alpha)
    val shape = resolved.shape
    resolved.background?.let { color ->
        modifier = if (shape == null) modifier.background(color, resolved.cornerRadius) else modifier.background(color, shape)
    }
    val borderColor = resolved.borderColor
    if (borderColor != null && resolved.borderWidth.value > 0f) {
        modifier = if (shape == null) {
            modifier.border(resolved.borderWidth, borderColor, resolved.cornerRadius, borderSides)
        } else {
            modifier.border(resolved.borderWidth, borderColor, shape, borderSides)
        }
    }
    if (resolved.contentPaddingHorizontal.value > 0f || resolved.contentPaddingVertical.value > 0f) {
        modifier = modifier.padding(
            horizontal = resolved.contentPaddingHorizontal,
            vertical = resolved.contentPaddingVertical,
        )
    }
    return modifier.styleSemantics(resolved)
}

/**
 * Records the resolved content inset for parity diagnostics.
 *
 * Dp, not pixels: a semantics configuration is built before measurement, so there is no density here
 * to convert with. The reader scales it.
 */
private fun Modifier.styleSemantics(resolved: ResolvedStyle): Modifier = semantics {
    // Published, not inferable. A placed child's bounds say where the content landed, not where
    // the box allows it to go, so nothing downstream can reconstruct the content box from the
    // tree. `ui-core` retains the same value on its semantic node for the same reason.
    this[SemanticsProperties.ContentPadding] =
        resolved.contentPaddingHorizontal.value to resolved.contentPaddingVertical.value
    this[SemanticsProperties.BorderWidth] = resolved.borderWidth.value
    this[SemanticsProperties.CornerRadius] = resolved.cornerRadius.value
}

/** The colour a styled component's text should take, or null when the style says nothing. */
fun resolveTextColor(state: StyleState, vararg styles: Style): Color? =
    resolve(state, styles).textColor
