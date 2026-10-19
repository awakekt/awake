/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.draw

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.graphics.RectangleShape
import com.awakekt.awake.compose.ui.graphics.Shape
import com.awakekt.awake.compose.ui.graphics.ShapeOutline
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.node.OnPlacedModifierNode
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Size2D
import com.awakekt.awake.core.math2d.dp

/**
 * Visual styling parameters for a backdrop frosted-glass effect.
 *
 * @property blurRadius Radius of the dual-filter blur applied to the backdrop behind the panel.
 * @property tint Translucent color layered on top of the blurred backdrop.
 * @property saturation Saturation multiplier applied to the backdrop before tinting (e.g. 1f for unchanged, >1f for boosted vibrancy).
 * @property noise Noise grain intensity between 0 (none) and 1 (full grain).
 * @property fallback Optional color used when backdrop blur cannot be performed. Defaults to null, which resolves to the default fallback color computed from [tint].
 */
data class BackdropGlassStyle(
    val blurRadius: Dp = 16.dp,
    val tint: Color = Color.Transparent,
    val saturation: Float = 1f,
    val noise: Float = 0f,
    val fallback: Color? = null,
) {
    /**
     * Standard frosted-glass material presets.
     */
    companion object {
        /** Ultra-thin glass with subtle blur and slight tint. */
        val UltraThin: BackdropGlassStyle = BackdropGlassStyle(
            blurRadius = 8.dp,
            tint = Color(1f, 1f, 1f, 0.05f),
        )

        /** Thin glass for lightweight overlays and sub-panels. */
        val Thin: BackdropGlassStyle = BackdropGlassStyle(
            blurRadius = 12.dp,
            tint = Color(1f, 1f, 1f, 0.10f),
        )

        /** Standard regular glass for typical dialogs, menus, and sidebars. */
        val Regular: BackdropGlassStyle = BackdropGlassStyle(
            blurRadius = 16.dp,
            tint = Color(1f, 1f, 1f, 0.15f),
        )

        /** Thick glass for emphasized surface contrast. */
        val Thick: BackdropGlassStyle = BackdropGlassStyle(
            blurRadius = 24.dp,
            tint = Color(1f, 1f, 1f, 0.25f),
        )

        /** Ultra-thick glass for heavy modal backdrops and high-contrast sheets. */
        val UltraThick: BackdropGlassStyle = BackdropGlassStyle(
            blurRadius = 32.dp,
            tint = Color(1f, 1f, 1f, 0.35f),
        )
    }
}

/**
 * State object connecting a backdrop source to one or more [backdropEffect] modifiers.
 *
 * Attach this state to a content node using [Modifier.backdropSource]. Effect panels
 * reference the same state to blur the content rendered underneath them.
 *
 * @property enabled Whether effects targeting this source should blur its content.
 * When false, effects render their fallback color or skip the blur pass.
 */
class BackdropSourceState(
    var enabled: Boolean = true,
) {
    /**
     * Tree-space bounds of the source node from the latest layout pass, or null if not yet placed.
     */
    var bounds: Rectangle? = null
        internal set

    /**
     * Whether this source node has been placed in the tree.
     */
    val isPlaced: Boolean
        get() = bounds != null
}

context(_: Composer)
/**
 * Creates and remembers a [BackdropSourceState] across recompositions.
 *
 * @param enabled Initial enabled state for the backdrop source.
 */
fun rememberBackdropSourceState(enabled: Boolean = true): BackdropSourceState =
    remember { BackdropSourceState(enabled) }.also { it.enabled = enabled }

/**
 * Marks this node as a backdrop source whose rendered content can be sampled and blurred
 * by descendant or overlapping [backdropEffect] modifiers referencing [state].
 *
 * @param state The state object coordinating between this source and its effects.
 */
fun Modifier.backdropSource(state: BackdropSourceState): Modifier =
    this then BackdropSourceElement(state)

/**
 * Blurs the backdrop behind this node's layout bounds with a frosted-glass effect.
 *
 * @param source The [BackdropSourceState] identifying what can be blurred.
 * @param style Visual styling parameters including blur radius, tint, saturation, noise, and fallback.
 * @param shape Shape outline clipping the backdrop blur effect.
 * @param enabled Whether this backdrop effect is active. When false or when [source] is disabled,
 * renders the fallback color if available.
 */
fun Modifier.backdropEffect(
    source: BackdropSourceState,
    style: BackdropGlassStyle = BackdropGlassStyle.Regular,
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
): Modifier = this then BackdropEffectElement(source, style, shape, enabled)

private class BackdropSourceElement(
    private val state: BackdropSourceState,
) : ModifierNodeElement<BackdropSourceNode>() {
    override fun create(): BackdropSourceNode = BackdropSourceNode(state)

    override fun update(node: BackdropSourceNode) {
        if (node.state !== state) {
            node.state.bounds = null
            node.state = state
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackdropSourceElement) return false
        return state == other.state
    }

    override fun hashCode(): Int = state.hashCode()

    override fun toString(): String = "backdropSource($state)"
}

private class BackdropSourceNode(
    var state: BackdropSourceState,
) : Modifier.Node(),
    OnPlacedModifierNode {
    override fun onPlaced(x: Int, y: Int, width: Int, height: Int) {
        state.bounds = Rectangle(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
    }

    override fun onDetach() {
        state.bounds = null
    }

    override fun toString(): String = "backdropSource($state)"
}

private class BackdropEffectElement(
    private val source: BackdropSourceState,
    private val style: BackdropGlassStyle,
    private val shape: Shape,
    private val enabled: Boolean,
) : ModifierNodeElement<BackdropEffectNode>() {
    override fun create(): BackdropEffectNode = BackdropEffectNode(source, style, shape, enabled)

    override fun update(node: BackdropEffectNode) {
        node.source = source
        node.style = style
        node.shape = shape
        node.enabled = enabled
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackdropEffectElement) return false
        return source == other.source && style == other.style && shape == other.shape && enabled == other.enabled
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + shape.hashCode()
        result = 31 * result + enabled.hashCode()
        return result
    }

    override fun toString(): String = "backdropEffect($source, $style, $shape, enabled=$enabled)"
}

private class BackdropEffectNode(
    var source: BackdropSourceState,
    var style: BackdropGlassStyle,
    var shape: Shape,
    var enabled: Boolean,
) : Modifier.Node(),
    DrawModifierNode {
    override fun DrawScope.draw(drawContent: () -> Unit) {
        val outline = shape.createOutline(
            Size2D(width.toFloat(), height.toFloat()),
            density,
            layoutDirection,
        )
        val cornerRadius = when (outline) {
            is ShapeOutline.Rectangle -> 0f
            is ShapeOutline.Rounded -> outline.radius
            is ShapeOutline.RoundedCorners,
            is ShapeOutline.Generic,
            -> 0f
        }
        val path = when (outline) {
            is ShapeOutline.RoundedCorners -> outline.path
            is ShapeOutline.Generic -> outline.path
            else -> null
        }

        if (enabled && source.enabled) {
            drawBackdropBlur(
                blurRadius = (style.blurRadius.value * density).coerceAtLeast(0f),
                tint = style.tint,
                saturation = style.saturation,
                noise = style.noise,
                cornerRadius = cornerRadius,
                path = path,
                fallback = style.fallback,
            )
        } else {
            val fallback = style.fallback
            if (fallback != null && fallback.a > 0f) {
                drawBackdropBlur(
                    blurRadius = 0f,
                    tint = style.tint,
                    cornerRadius = cornerRadius,
                    path = path,
                    fallback = fallback,
                )
            }
        }
        drawContent()
    }

    override fun toString(): String = "backdropEffect($source, $style, $shape, enabled=$enabled)"
}
