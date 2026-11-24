/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.graphics2d.GlyphDecoration
import com.awakekt.awake.core.graphics2d.TextureRegion
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.UiLinearGradient
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextOutline
import com.awakekt.awake.core.text.theme.TextShadow
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Steady-state [TextMotionState.resolvePaint], [GlyphEffects.draw], and transparent
 * [GlyphDecoration.dimmedBy] calls must allocate nothing per frame (#1742, #1512).
 */
class TextMotionAndEffectsAllocationTest {
    private var sink: Any? = null

    @Test
    fun textMotionStateSteadyAndWholePixelFramesAllocateNothing() {
        val state = TextMotionState()
        var wholeX = 0f

        fun exercise() {
            sink = state.resolvePaint(isScopeScaled = false, isScopeAligned = true, offsetX = wholeX, offsetY = 12f)
            wholeX += 1f
            sink = state.resolvePaint(isScopeScaled = false, isScopeAligned = false, offsetX = wholeX, offsetY = 12f)
            sink = state.resolvePaint(isScopeScaled = true, isScopeAligned = false, offsetX = wholeX, offsetY = 12f)
        }

        repeat(WARMUP) { exercise() }
        val reading = readAllocation(unitsPerWindow = ITERATIONS) {
            repeat(ITERATIONS) { exercise() }
        }
        assertAllocatesNothing(reading, "TextMotionState.resolvePaint steady/scaled/whole-pixel frames")
    }

    @Test
    fun glyphEffectsSteadyLookupAllocatesNothing() {
        val font = UiFonts.default()
        val style = TextStyle(
            outline = TextOutline(Color.Black, 1.5f),
            shadow = TextShadow(Color(0f, 0f, 0f, 0.7f), offsetX = 1f, offsetY = 2f, blur = 1f),
        )
        val effects = assertNotNull(GlyphEffects.of(style, density = 1f))
        val sample = "Player 12345"
        val scope = CapturingDrawScope()

        fun drawAll() {
            for (i in 0 until sample.length) {
                val code = sample[i].code
                val glyphFont = font.fontFor(code, FontWeight.Normal)
                val uv = font.glyphFor(code, FontWeight.Normal) ?: continue
                effects.draw(scope, font, glyphFont, code, FontWeight.Normal, uv, i * 10f, 0f, 16f, Color.White)
            }
        }

        repeat(WARMUP) { drawAll() }
        val reading = readAllocation(unitsPerWindow = ITERATIONS) {
            repeat(ITERATIONS) { drawAll() }
        }
        assertAllocatesNothing(reading, "GlyphEffects.draw steady ASCII lookup")
        sink = scope.lastDecoration
    }

    @Test
    fun transparentGlyphDecorationDimmedByAllocatesNothing() {
        val transparent = GlyphDecoration(
            fieldOffsetX = 0f,
            fieldOffsetY = 0f,
            fieldW = 10f,
            fieldH = 10f,
            fieldU0 = 0f,
            fieldV0 = 0f,
            fieldU1 = 1f,
            fieldV1 = 1f,
            outlineColor = Color.Transparent,
            outlineWidth = 1f,
            shadowColor = Color.Transparent,
            shadowOffsetX = 1f,
            shadowOffsetY = 1f,
            shadowBlur = 1f,
        )
        assertSame(transparent, transparent.dimmedBy(0.5f))
        val reading = readAllocation(unitsPerWindow = ITERATIONS) {
            repeat(ITERATIONS) { sink = transparent.dimmedBy(0.5f) }
        }
        assertAllocatesNothing(reading, "GlyphDecoration.dimmedBy with transparent effects")
    }

    @Suppress("TooManyFunctions")
    private class CapturingDrawScope : DrawScope {
        var lastDecoration: GlyphDecoration? = null
        override val width: Int = 200
        override val height: Int = 40
        override val density: Float = 1f
        override fun drawRect(x: Float, y: Float, width: Float, height: Float, color: Color) = Unit
        override fun drawRoundedRect(x: Float, y: Float, width: Float, height: Float, color: Color, radius: Float) = Unit
        override fun drawRoundedRectBorder(x: Float, y: Float, width: Float, height: Float, color: Color, radius: Float, strokeWidth: Float) = Unit
        override fun drawShadow(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            color: Color,
            radius: Float,
            offsetX: Float,
            offsetY: Float,
            blurRadius: Float,
            spread: Float,
            gradient: UiLinearGradient?,
        ) = Unit
        override fun drawBackdropBlur(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            blurRadius: Float,
            tint: Color,
            saturation: Float,
            noise: Float,
            cornerRadius: Float,
            smoothing: Float,
            path: DrawPath?,
            fallback: Color?,
        ) = Unit
        override fun drawTexture(
            material: Any,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            region: TextureRegion,
            tint: Color,
            filterQuality: FilterQuality,
            premultiplied: Boolean,
        ) = Unit
        override fun drawTexture(
            material: Any,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            region: TextureRegion,
            tint: Color,
            filterQuality: FilterQuality,
            premultiplied: Boolean,
            rotationDegrees: Float,
            blendMode: com.awakekt.awake.core.graphics2d.BlendMode,
        ) = Unit
        override fun drawTextureRepeated(
            material: Any,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            region: TextureRegion,
            repeatX: Float,
            repeatY: Float,
            tint: Color,
            filterQuality: FilterQuality,
        ) = Unit
        override fun drawGlyph(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            u0: Float,
            v0: Float,
            u1: Float,
            v1: Float,
            color: Color,
            font: UiFont?,
            page: Int,
        ) = Unit
        override fun drawDecoratedGlyph(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            u0: Float,
            v0: Float,
            u1: Float,
            v1: Float,
            color: Color,
            font: UiFont?,
            page: Int,
            decoration: GlyphDecoration,
        ) {
            lastDecoration = decoration
        }
        override fun drawPath(path: DrawPath, color: Color) = Unit
        override fun drawMesh(mesh: ColoredTriangleMesh) = Unit
        override fun drawStrokedPath(path: DrawPath, stroke: DrawStroke, color: Color) = Unit
        override fun clipped(inset: Float, block: () -> Unit) = block()
        override fun clippedWithMargin(left: Float, top: Float, right: Float, bottom: Float, block: () -> Unit) = block()
        override fun clippedPath(path: DrawPath, safeInteriorRect: Rectangle?, block: () -> Unit) = block()
        override fun unclipped(block: () -> Unit) = block()
        override fun withAlpha(alpha: Float, block: () -> Unit) = block()
        override fun withScale(scaleX: Float, scaleY: Float, block: () -> Unit) = block()
        override fun translate(left: Float, top: Float, block: () -> Unit) = block()
        override fun emit(primitive: UiDrawPrimitive) = Unit
    }

    private companion object {
        const val WARMUP = 200
        const val ITERATIONS = 500
    }
}
