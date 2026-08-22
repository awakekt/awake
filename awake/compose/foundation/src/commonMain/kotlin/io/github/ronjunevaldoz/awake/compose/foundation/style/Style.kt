// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.style

import io.github.ronjunevaldoz.awake.compose.ui.unit.Dp
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import io.github.ronjunevaldoz.awake.core.color.Color

/**
 * A bundle of visual properties, applied to a [StyleScope].
 *
 * A function rather than a data class, which is what lets a style *branch* on state: the same
 * `Style` says one thing when hovered and another when not, and a caller composes styles instead of
 * assembling a struct per state.
 *
 * Convergent with Compose, not invented here: `androidx.compose.foundation.style.Style` has the same
 * `applyStyle(scope)` shape. An earlier version of `04-styling-theme.md` claimed Compose had no
 * equivalent; it does, and it is experimental rather than absent.
 */
fun interface Style {
    fun applyStyle(scope: StyleScope)
}

/**
 * Where a [Style] writes its properties, and where it reads the state to branch on.
 *
 * Split into *content* padding and *external* padding for the reason the CSS box model does: one
 * insets the content from the background's edge, the other holds neighbours off the background
 * itself. Collapsing them into "padding" makes a bordered button impossible to express.
 */
interface StyleScope {
    /** What the component currently is. Read it to branch, or use the rules below. */
    val state: StyleState

    fun background(color: Color)

    fun border(width: Dp, color: Color)

    fun cornerRadius(radius: Dp)

    /** Inside the background: the gap between a button's edge and its label. */
    fun contentPadding(all: Dp)

    /** Outside the background: the gap between this component and its neighbours. */
    fun externalPadding(all: Dp)

    fun width(width: Dp)

    fun height(height: Dp)

    fun size(size: Dp)

    fun textColor(color: Color)

    fun alpha(alpha: Float)
}

/** Everything a chain of styles resolved to. Written by [StyleScope], read by `styleable`. */
class ResolvedStyle {
    var background: Color? = null
    var borderWidth: Dp = 0.dp
    var borderColor: Color? = null
    var cornerRadius: Dp = 0.dp
    var contentPadding: Dp = 0.dp
    var externalPadding: Dp = 0.dp
    var width: Dp? = null
    var height: Dp? = null
    var textColor: Color? = null
    var alpha: Float = 1f
}

internal class ResolvingStyleScope(
    override val state: StyleState,
    val resolved: ResolvedStyle,
) : StyleScope {
    override fun background(color: Color) {
        resolved.background = color
    }

    override fun border(width: Dp, color: Color) {
        resolved.borderWidth = width
        resolved.borderColor = color
    }

    override fun cornerRadius(radius: Dp) {
        resolved.cornerRadius = radius
    }

    override fun contentPadding(all: Dp) {
        resolved.contentPadding = all
    }

    override fun externalPadding(all: Dp) {
        resolved.externalPadding = all
    }

    override fun width(width: Dp) {
        resolved.width = width
    }

    override fun height(height: Dp) {
        resolved.height = height
    }

    override fun size(size: Dp) {
        resolved.width = size
        resolved.height = size
    }

    override fun textColor(color: Color) {
        resolved.textColor = color
    }

    override fun alpha(alpha: Float) {
        resolved.alpha = alpha
    }
}

/**
 * Applies [style] only when the component is hovered.
 *
 * The state rules are the whole point of styles being functions. `ui-core` reached the same shape
 * through `Style.then` merges, and Compose ships the same six -- this is convergence, not invention.
 */
fun StyleScope.hovered(style: Style) {
    if (state.isHovered) style.applyStyle(this)
}

fun StyleScope.pressed(style: Style) {
    if (state.isPressed) style.applyStyle(this)
}

fun StyleScope.focused(style: Style) {
    if (state.isFocused) style.applyStyle(this)
}

/** Applies [style] when the component is **not** enabled -- the one rule that reads inverted. */
fun StyleScope.disabled(style: Style) {
    if (!state.isEnabled) style.applyStyle(this)
}

fun StyleScope.selected(style: Style) {
    if (state.isSelected) style.applyStyle(this)
}

fun StyleScope.checked(style: Style) {
    if (state.isChecked) style.applyStyle(this)
}
