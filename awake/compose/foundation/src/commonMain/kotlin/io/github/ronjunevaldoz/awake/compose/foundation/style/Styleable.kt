// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.style

import io.github.ronjunevaldoz.awake.compose.foundation.background
import io.github.ronjunevaldoz.awake.compose.foundation.border
import io.github.ronjunevaldoz.awake.compose.foundation.layout.height
import io.github.ronjunevaldoz.awake.compose.foundation.layout.padding
import io.github.ronjunevaldoz.awake.compose.foundation.layout.width
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.draw.alpha

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
fun Modifier.styleable(state: StyleState, vararg styles: Style): Modifier {
    val resolved = ResolvedStyle()
    val scope = ResolvingStyleScope(state, resolved)
    for (style in styles) {
        style.applyStyle(scope)
    }
    return this.applyResolved(resolved)
}

private fun Modifier.applyResolved(resolved: ResolvedStyle): Modifier {
    var modifier = this
    if (resolved.externalPadding.value > 0f) modifier = modifier.padding(resolved.externalPadding)
    resolved.width?.let { modifier = modifier.width(it) }
    resolved.height?.let { modifier = modifier.height(it) }
    if (resolved.alpha < 1f) modifier = modifier.alpha(resolved.alpha)
    resolved.background?.let { modifier = modifier.background(it, resolved.cornerRadius) }
    val borderColor = resolved.borderColor
    if (borderColor != null && resolved.borderWidth.value > 0f) {
        modifier = modifier.border(resolved.borderWidth, borderColor, resolved.cornerRadius)
    }
    if (resolved.contentPadding.value > 0f) modifier = modifier.padding(resolved.contentPadding)
    return modifier
}

/** The colour a styled component's text should take, or null when the style says nothing. */
fun resolveTextColor(state: StyleState, vararg styles: Style): io.github.ronjunevaldoz.awake.core.color.Color? {
    val resolved = ResolvedStyle()
    val scope = ResolvingStyleScope(state, resolved)
    for (style in styles) {
        style.applyStyle(scope)
    }
    return resolved.textColor
}
