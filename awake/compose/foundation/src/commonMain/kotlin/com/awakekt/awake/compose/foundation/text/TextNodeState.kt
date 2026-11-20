/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.theme.TextStyle

/**
 * Retained per-node layout cache and motion state for [Text] and [BasicTextField].
 *
 * Kept in composition slots rather than on a `Modifier.Node`, so a conditional modifier ahead of
 * text in the chain (such as `Modifier.scale(1f)` eliding its element as an animation passes
 * through unit scale) never resets either the layout cache or the active motion state.
 *
 * For [Text] it also keeps what the last composition built, the merged style, the measure policy, the paint element
 * and the modifier chain, and hands each back while its inputs are equal to the last ones: a node composed every frame
 * with the same text and style allocates nothing (#1813).
 */
internal class TextNodeState {
    val cache = TextLayoutCache()
    val motion = TextMotionState()

    private var ambientStyle: TextStyle? = null
    private var style: TextStyle? = null
    private var resolved: TextStyle = TextStyle.Default
    private var resolvedForFont: TextStyle? = null
    private var ambientFont: UiFont? = null
    private var font: UiFont? = null

    private var text: String? = null
    private var overflow: TextOverflow = TextOverflow.Clip
    private var softWrap = true
    private var maxLines = Int.MAX_VALUE
    private var policyFont: UiFont? = null
    private var policy: TextMeasurePolicy? = null

    private var element: TextPaintElement? = null
    private var modifier: Modifier? = null
    private var chain: Modifier = Modifier

    /** [style] merged over [ambient], as `ambient then style`, merged again only when either changed. */
    fun resolve(ambient: TextStyle, style: TextStyle): TextStyle {
        if (ambient != ambientStyle || style != this.style) {
            ambientStyle = ambient
            this.style = style
            resolved = ambient then style
        }
        return resolved
    }

    /**
     * The measure policy for [text] in [resolved], the one [Text] built last time while every input is equal, or a new
     * one, handed this node's cache and motion.
     */
    @Suppress("LongParameterList")
    fun policyFor(
        text: String,
        resolved: TextStyle,
        ambientFont: UiFont,
        overflow: TextOverflow,
        softWrap: Boolean,
        maxLines: Int,
    ): TextMeasurePolicy {
        val font = fontFor(resolved, ambientFont)
        val last = policy
        val sameStyle = last?.style === resolved && font === policyFont
        val sameText = text == this.text && overflow == this.overflow && softWrap == this.softWrap && maxLines == this.maxLines
        if (last != null && sameStyle && sameText) {
            return last
        }
        this.text = text
        policyFont = font
        this.overflow = overflow
        this.softWrap = softWrap
        this.maxLines = maxLines
        return TextMeasurePolicy(text = text, style = resolved, font = font, overflow = overflow, softWrap = softWrap, maxLines = maxLines)
            .also {
                it.cache = cache
                it.motion = motion
                policy = it
            }
    }

    /** [modifier] with [policy]'s paint element after it, the chain built last time while both are unchanged. */
    fun modifierFor(modifier: Modifier, policy: TextMeasurePolicy, color: Color): Modifier {
        val last = element
        val element = if (last != null && last.policy === policy && last.color == color) {
            last
        } else {
            TextPaintElement(policy, color, motion)
        }
        if (element !== last || modifier != this.modifier) {
            this.element = element
            this.modifier = modifier
            chain = modifier.then(element)
        }
        return chain
    }

    /** The font [resolved] draws with, resolved again only when the style or the ambient font changed. */
    private fun fontFor(resolved: TextStyle, ambientFont: UiFont): UiFont {
        val last = font
        if (last != null && resolved === this.resolvedForFont && ambientFont === this.ambientFont) return last
        this.resolvedForFont = resolved
        this.ambientFont = ambientFont
        return resolved.resolveFont(ambientFont).also { font = it }
    }
}

/** Paints a [Text] run using the node's remembered [TextMotionState]. */
internal class TextPaintElement(
    val policy: TextMeasurePolicy,
    val color: Color,
    private val motion: TextMotionState,
) : ModifierNodeElement<TextPaintNode>() {
    override fun create(): TextPaintNode = TextPaintNode()

    override fun update(node: TextPaintNode) {
        node.policy = policy
        node.color = color
        node.motion = motion
    }

    override fun toString(): String = "text()"
}

internal class TextPaintNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var policy: TextMeasurePolicy
    lateinit var color: Color
    lateinit var motion: TextMotionState

    override fun DrawScope.draw(drawContent: () -> Unit) {
        policy.runFor(density, policy.measuredFontScale).paintDecorated(this, color, motion = motion)
        drawContent()
    }

    override fun toString(): String = "text()"
}
