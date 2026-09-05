/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation.style

import io.github.awakelab.awake.compose.ui.graphics.RoundedCornerShape
import io.github.awakelab.awake.compose.ui.graphics.Shape
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.color.Color

/**
 * A bundle of visual properties, applied to a [StyleScope].
 *
 * A function rather than a data class, which is what lets a style *branch* on state: the same
 * `Style` says one thing when hovered and another when not, and a caller composes styles instead of
 * assembling a struct per state.
 *
 * Convergent with Compose, not invented here: `androidx.compose.foundation.style.Style` is the same
 * idea. An earlier version of `04-styling-theme.md` claimed Compose had no equivalent; it does, and
 * it is experimental rather than absent. The one deliberate difference is the receiver below.
 */
fun interface Style {
    /**
     * A receiver rather than a parameter, so an authored style reads as the CSS it translates:
     * `Style { background(x); hovered { background(y) } }` and not `Style { s -> s.background(x);
     * s.hovered { it.background(y) } }`. Costs nothing -- SAM conversion supplies the receiver.
     */
    fun StyleScope.applyStyle()
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

    /** Uses one resolved [Shape] for the background and border of this styled node. */
    fun shape(shape: Shape)

    /**
     * Inside the background: the gap between a button's edge and its label.
     *
     * Per axis, with [vertical] defaulting to [horizontal] so a uniform inset is still one argument.
     * Tailwind almost never pads uniformly -- a badge is `px-2 py-0.5`, a tooltip `px-3 py-1.5`, a
     * button `px-4 py-2` -- and before this each of those had to pick one number and lose the other,
     * which shows on anything short and wide.
     *
     * A default rather than an overload, because two overloads apiece put this interface over
     * detekt's function limit and the pair carried no more meaning than one signature does.
     */
    fun contentPadding(horizontal: Dp, vertical: Dp = horizontal)

    /** Outside the background: the gap between this component and its neighbours. */
    fun externalPadding(horizontal: Dp, vertical: Dp = horizontal)

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
    var shape: Shape? = null
    var contentPaddingHorizontal: Dp = 0.dp
    var contentPaddingVertical: Dp = 0.dp
    var externalPaddingHorizontal: Dp = 0.dp
    var externalPaddingVertical: Dp = 0.dp
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
        resolved.shape = RoundedCornerShape(radius)
    }

    override fun shape(shape: Shape) {
        resolved.shape = shape
        // The current semantic export has one radius field, like browser `parseFloat(borderRadius)`;
        // retain the leading corner for existing geometry/style reports until per-corner semantics land.
        resolved.cornerRadius = (shape as? RoundedCornerShape)?.topStart ?: 0.dp
    }

    override fun contentPadding(horizontal: Dp, vertical: Dp) {
        resolved.contentPaddingHorizontal = horizontal
        resolved.contentPaddingVertical = vertical
    }

    override fun externalPadding(horizontal: Dp, vertical: Dp) {
        resolved.externalPaddingHorizontal = horizontal
        resolved.externalPaddingVertical = vertical
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
 * Applies [style] only when [condition] holds.
 *
 * The six rules below differ only in which flag they read, so the application itself lives here
 * once. `inline` keeps it free: a rule is a branch, not a call.
 */
private fun StyleScope.applyWhen(condition: Boolean, style: Style) {
    if (condition) with(style) { applyStyle() }
}

/**
 * Applies [style] only when the component is hovered.
 *
 * The state rules are the whole point of styles being functions. `ui-core` reached the same shape
 * through `Style.then` merges, and Compose ships the same six -- this is convergence, not invention.
 */
fun StyleScope.hovered(style: Style) = applyWhen(state.isHovered, style)

fun StyleScope.pressed(style: Style) = applyWhen(state.isPressed, style)

fun StyleScope.focused(style: Style) = applyWhen(state.isFocused, style)

/** Applies [style] when the component is **not** enabled -- the one rule that reads inverted. */
fun StyleScope.disabled(style: Style) = applyWhen(!state.isEnabled, style)

fun StyleScope.selected(style: Style) = applyWhen(state.isSelected, style)

fun StyleScope.checked(style: Style) = applyWhen(state.isChecked, style)
