/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerPlaceholder
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.DrawPoint
import com.awakekt.awake.core.graphics2d.GlyphDecoration
import com.awakekt.awake.core.graphics2d.TextureRegion
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.containsPoint
import com.awakekt.awake.core.graphics2d.tessellateFillAa
import com.awakekt.awake.core.graphics2d.tessellateStrokeAa
import com.awakekt.awake.core.graphics2d.translatedBy
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import com.awakekt.awake.render.capture.PixelMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private val MissingFontColor = Color(1f, 0f, 1f, 1f)

/** The smallest uv span a glyph quad counts as, so a degenerate one cannot divide by zero. */
private const val MIN_UV_SPAN = 1e-6f

/**
 * The glyph shader's screen-pixel range for a distance-field glyph: how many screen pixels the
 * atlas's [rangeTexels]-texel distance spread covers, at least 1. The CPU copy of `unitRange`,
 * `screenTexSize` and `screenPxRange` in `AslUiShaders.UiGlyphShader`.
 *
 * The shader takes `0.5 * dot(range / atlasSize, 1 / fwidth(uv))`: the mean of the horizontal
 * and vertical texel-to-pixel scales, times the range. For the axis-aligned quad a glyph is,
 * `fwidth(uv)` is the quad's uv span over its on-screen size, which is what this is given.
 * Taking half the horizontal scale alone, as the raster did before, halved the range and drew
 * every distance-field edge twice as wide as the GPU (#1057).
 */
@Suppress("LongParameterList")
internal fun distanceFieldScreenPxRange(
    rangeTexels: Float,
    atlasWidth: Int,
    atlasHeight: Int,
    uvWidth: Float,
    uvHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
): Float {
    val screenTexWidth = screenWidth / max(abs(uvWidth), MIN_UV_SPAN)
    val screenTexHeight = screenHeight / max(abs(uvHeight), MIN_UV_SPAN)
    return max(0.5f * (rangeTexels / atlasWidth * screenTexWidth + rangeTexels / atlasHeight * screenTexHeight), 1f)
}

/**
 * The glyph shader's distance-field coverage for one bilinear atlas sample ([r], [g], [b]): the
 * median of the three channels, mapped through [screenPxRange] to a one-pixel ramp centred on the
 * 0.5 edge, the share of the pixel a straight outline covers. The CPU copy of `signedDistance` and
 * `fieldCoverage` in `AslUiShaders.UiGlyphShader`; [textCoverageAlpha] then shapes it, as it shapes
 * an exact-size glyph's coverage.
 */
internal fun distanceFieldCoverage(r: Float, g: Float, b: Float, screenPxRange: Float): Float {
    val signedDistance = max(min(r, g), min(max(r, g), b))
    return (screenPxRange * (signedDistance - 0.5f) + 0.5f).coerceIn(0f, 1f)
}

/**
 * The text coverage rule: the alpha a glyph pixel draws with, for [coverage], the share of the pixel the
 * outline covers, in [text]'s colour. One rule for exact-size pixel glyphs and the distance field, so text
 * keeps its weight across the pixel-glyph limit, and the same rule on every backend. It is Skia's mask
 * gamma with the constants Chromium sets on Linux, [TEXT_GAMMA] and [TEXT_CONTRAST]:
 *
 * 1. the text's luma, from its colour linearised with [TEXT_GAMMA];
 * 2. the ground taken to be the text's inverse, `1 - luma`: the rule cannot see the real ground;
 * 3. the coverage boosted by [TEXT_CONTRAST] times the ground's linear luma, `c + (1 - c) * k * c`, so dark
 *    text on a light ground gains a little and light text on a dark ground none;
 * 4. text and ground mixed by that coverage in light linearised with [TEXT_GAMMA], and the alpha returned
 *    that gives the same colour when blended in the target's own encoding.
 *
 * Mid-grey text, whose assumed ground is itself, takes the boosted coverage. Measured against Chromium in
 * both polarities by `TextPolarityWeightTest`. The CPU copy of the rule in `AslUiShaders.UiGlyphShader`,
 * held to it by `TextCoverageRuleTwinTest`.
 */
internal fun textCoverageAlpha(coverage: Float, text: Color): Float = TextCoverageRule(text).alpha(coverage)

/** [textCoverageAlpha] for one text colour, its colour-only terms worked out once for every pixel of a glyph. */
internal class TextCoverageRule(text: Color) {
    private val textLinear = 0.2126f * text.r.coerceAtLeast(0f).pow(TEXT_GAMMA) +
        0.7152f * text.g.coerceAtLeast(0f).pow(TEXT_GAMMA) +
        0.0722f * text.b.coerceAtLeast(0f).pow(TEXT_GAMMA)
    private val textLuma = textLinear.pow(1f / TEXT_GAMMA)
    private val groundLuma = 1f - textLuma
    private val groundLinear = groundLuma.pow(TEXT_GAMMA)
    private val sameLuma = abs(textLuma - groundLuma) < TEXT_SAME_LUMA

    /** The alpha [coverage] draws with. */
    fun alpha(coverage: Float): Float {
        val contrasted = coverage + (1f - coverage) * TEXT_CONTRAST * groundLinear * coverage
        if (sameLuma) return contrasted
        val mixed = (textLinear * contrasted + groundLinear * (1f - contrasted)).coerceIn(0f, 1f).pow(1f / TEXT_GAMMA)
        return ((mixed - groundLuma) / (textLuma - groundLuma)).coerceIn(0f, 1f)
    }
}

/** The gamma the text coverage rule mixes in; the shader's `TEXT_GAMMA`, 1.2. */
private const val TEXT_GAMMA = 1.2f

/** The text coverage rule's contrast over a white ground; the shader's `TEXT_CONTRAST`, 0.2. */
private const val TEXT_CONTRAST = 0.2f

/** How close the text's luma and its assumed ground's may be before the rule keeps the boosted coverage; the shader's 1/256. */
private const val TEXT_SAME_LUMA = 1f / 256f

/**
 * Supersamples every source-texel footprint without allowing pathological work per pixel.
 *
 * A footprint a rounding error over a whole number of texels counts as that number: an exact-size glyph
 * drawn 1:1 has UVs that give 1.0000001 texels a pixel, and two samples there would straddle texel
 * centres and blur the glyph it was rasterized to keep sharp.
 */
internal fun glyphSampleCount(sourceTexels: Float, screenPixels: Float): Int =
    ceil(sourceTexels / screenPixels.coerceAtLeast(1f) - FOOTPRINT_ROUNDING).toInt().coerceIn(1, 6)

/** How far over a whole number of texels a footprint may be and still count as that number. */
private const val FOOTPRINT_ROUNDING = 1e-3f

/** The straight-alpha colour [image] samples at fraction ([u], [v]) of [region], tinted and scaled by [alpha]. */
@Suppress("LongParameterList")
private fun sampleImage(
    image: ImageBitmap,
    region: TextureRegion,
    tint: Color,
    alpha: Float,
    u: Float,
    v: Float,
    premultiplied: Boolean = false,
): Color {
    val sx = ((region.u0 + (region.u1 - region.u0) * u) * image.width).toInt().coerceIn(0, image.width - 1)
    val sy = ((region.v0 + (region.v1 - region.v0) * v) * image.height).toInt().coerceIn(0, image.height - 1)
    val rgba = image.pixel(sx, sy)
    val rawR = ((rgba ushr 24) and 0xFF) / 255f
    val rawG = ((rgba ushr 16) and 0xFF) / 255f
    val rawB = ((rgba ushr 8) and 0xFF) / 255f
    val rawA = (rgba and 0xFF) / 255f
    return when {
        premultiplied && rawA <= 0f -> Color.Transparent
        premultiplied -> Color(
            (rawR / rawA).coerceIn(0f, 1f) * tint.r,
            (rawG / rawA).coerceIn(0f, 1f) * tint.g,
            (rawB / rawA).coerceIn(0f, 1f) * tint.b,
            (rawA * alpha * tint.a).coerceIn(0f, 1f),
        )
        else -> Color(
            rawR * tint.r,
            rawG * tint.g,
            rawB * tint.b,
            (rawA * alpha * tint.a).coerceIn(0f, 1f),
        )
    }
}

/**
 * Where destination pixel ([px], [py]) samples from in a [w] by [h] rect, rotating *backwards* by
 * [cosine]/[sine] about the rect's own centre ([centerX], [centerY]) -- the inverse of the rotation
 * a renderer applies forwards to the quad's corners (`DrawCommand.Texture.rotationDegrees`'s own
 * doc, and `DrawRunCoalescer.rotatedAboutCenter` for the real backends).
 */
@Suppress("LongParameterList")
private fun unrotatedSample(px: Int, py: Int, centerX: Float, centerY: Float, cosine: Float, sine: Float, w: Float, h: Float): DrawPoint {
    val dx = px + 0.5f - centerX
    val dy = py + 0.5f - centerY
    return DrawPoint(dx * cosine - dy * sine + w / 2f, dx * sine + dy * cosine + h / 2f)
}

/** Whether this point falls inside a rect of [w] by [h] starting at its own origin. */
private fun DrawPoint.isWithin(w: Float, h: Float): Boolean {
    if (x < 0f || x >= w) return false
    return y >= 0f && y < h
}

/**
 * Flattens each of [layers]' own primitives into its own [ImageBitmap], keyed by its
 * [GraphicsLayerFrame.id], so a `GraphicsLayerPlaceholder` material resolves to real pixels. Kept
 * apart from [rasterizeToPixelMap] rather than inlined into it, the way its other helpers are: one
 * function doing one thing, read on its own.
 */
private fun resolveLayerImages(layers: List<GraphicsLayerFrame>, font: UiFont?): Map<Int, ImageBitmap> {
    if (layers.isEmpty()) return emptyMap()
    return layers.associate { layer ->
        // The target the GPU compositor allocates: the node's box with the layer's insets on each side,
        // which its composite quad covers, and the content moved in by them.
        val layerWidth = (layer.width + layer.effectInsetX * 2).coerceAtLeast(1)
        val layerHeight = (layer.height + layer.effectInsetY * 2).coerceAtLeast(1)
        val content = if (layer.effectInsetX == 0 && layer.effectInsetY == 0) {
            layer.primitives
        } else {
            layer.primitives.map { it.translatedBy(layer.effectInsetX.toFloat(), layer.effectInsetY.toFloat()) }
        }
        // Not recursed with layers again: a layer's own content does not nest another layer here.
        val flattened = content.rasterizeToPixelMap(layerWidth, layerHeight, Color(0f, 0f, 0f, 0f), font)
        layer.id to ImageBitmap(layerWidth, layerHeight, flattened.pixels)
    }
}

/**
 * Software-rasterizes a [UiDrawPrimitive] list into a tightly-packed RGBA8 buffer for
 * preview/docs/snapshot review.
 */
fun List<UiDrawPrimitive>.rasterize(
    width: Int,
    height: Int,
    background: Color = Color(0.1f, 0.1f, 0.12f, 1f),
    font: UiFont? = null,
    layers: List<GraphicsLayerFrame> = emptyList(),
): ByteArray = rasterizeToPixelMap(width, height, background, font, layers).pixels

/**
 * Software-rasterizes UI primitives into the shared diagnostic pixel representation.
 *
 * [layers] flattens a `Modifier.graphicsLayer` subtree into its own [ImageBitmap] first, the same
 * composite step a renderer-backed [com.awakekt.awake.engine.compose.GraphicsLayerCompositor] does
 * against a real render target, so a layer's `GraphicsLayerPlaceholder` material resolves to real
 * pixels instead of this rasterizer's plain grey "unknown material" fallback. A layer nested inside
 * another is not resolved, and blend modes that need the destination (`Screen`, `Overlay`) are not
 * composited here; only the common straight paint-and-rotate case this test tool exists for is.
 */
// Detekt's length/complexity count for this one function jumps from clean to the triple digits the
// moment its parameter list reaches five, reproduced with a fifth parameter that is not even read
// anywhere in the body; the function's own complexity has not changed. Suppressed rather than
// reshaped around a measurement that does not track what it is meant to.
@Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
fun List<UiDrawPrimitive>.rasterizeToPixelMap(
    width: Int,
    height: Int,
    background: Color = Color(0.1f, 0.1f, 0.12f, 1f),
    font: UiFont? = null,
    layers: List<GraphicsLayerFrame> = emptyList(),
): PixelMap {
    val pixelMap = PixelMap(width, height)
    pixelMap.fill(background)
    val layerImages = resolveLayerImages(layers, font)
    val meshCoverage = IntArray(width * height)
    var meshStamp = 0

    var clipX0 = 0f
    var clipY0 = 0f
    var clipX1 = width.toFloat()
    var clipY1 = height.toFloat()

    data class ClipSnapshot(val rect: FloatArray, val activePathCount: Int)

    val clipStack = ArrayDeque<ClipSnapshot>()
    val activePathClips = ArrayList<com.awakekt.awake.core.graphics2d.DrawPath>()

    fun passesPathClips(x: Float, y: Float): Boolean =
        activePathClips.all { it.containsPoint(x, y) }

    fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Color) {
        val x0 = max(x, clipX0).toInt().coerceIn(0, width)
        val y0 = max(y, clipY0).toInt().coerceIn(0, height)
        val x1 = min(x + w, clipX1).toInt().coerceIn(0, width)
        val y1 = min(y + h, clipY1).toInt().coerceIn(0, height)
        var py = y0
        while (py < y1) {
            var px = x0
            while (px < x1) {
                if (passesPathClips(px + 0.5f, py + 0.5f)) pixelMap.blend(px, py, color)
                px += 1
            }
            py += 1
        }
    }

    /**
     * Fills [x], [y], [w], [h] with [region] of [image], times [tint] and [alpha]. It samples the
     * nearest pixel whatever the draw's filter: a test rasterizer, not the GPU's sampler.
     */
    fun blendWithMode(px: Int, py: Int, color: Color, blendMode: BlendMode) {
        if (blendMode != BlendMode.Plus) {
            pixelMap.blend(px, py, color)
        } else if (color.a > 0f) {
            val dst = pixelMap.sample(px, py)
            val dstA = dst.a / 255f
            val outPremulR = ((dst.r / 255f) * dstA + color.r * color.a).coerceIn(0f, 1f)
            val outPremulG = ((dst.g / 255f) * dstA + color.g * color.a).coerceIn(0f, 1f)
            val outPremulB = ((dst.b / 255f) * dstA + color.b * color.a).coerceIn(0f, 1f)
            val outA = (dstA + color.a).coerceIn(0f, 1f)
            if (outA > 0f) {
                pixelMap.set(
                    px,
                    py,
                    Color(
                        (outPremulR / outA).coerceIn(0f, 1f),
                        (outPremulG / outA).coerceIn(0f, 1f),
                        (outPremulB / outA).coerceIn(0f, 1f),
                        outA,
                    ),
                )
            }
        }
    }

    @Suppress("LongParameterList")
    fun fillImage(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        image: ImageBitmap,
        alpha: Float,
        region: TextureRegion = TextureRegion.Whole,
        tint: Color = Color.White,
        premultiplied: Boolean = false,
        blendMode: BlendMode = BlendMode.SourceOver,
    ) {
        if (w <= 0f || h <= 0f) return
        val x0 = max(x, clipX0).toInt().coerceIn(0, width)
        val y0 = max(y, clipY0).toInt().coerceIn(0, height)
        val x1 = min(x + w, clipX1).toInt().coerceIn(0, width)
        val y1 = min(y + h, clipY1).toInt().coerceIn(0, height)
        for (py in y0 until y1) {
            for (px in x0 until x1) {
                if (!passesPathClips(px + 0.5f, py + 0.5f)) continue
                val sampled = sampleImage(image, region, tint, alpha, (px + 0.5f - x) / w, (py + 0.5f - y) / h, premultiplied)
                blendWithMode(px, py, sampled, blendMode)
            }
        }
    }

    /**
     * Like [fillImage], but [rotationDegrees] clockwise about the destination rect's own centre.
     *
     * Scattering source pixels forward by the rotation would leave gaps between them at any angle
     * that is not a multiple of 90 degrees, so this walks destination pixels and inverse-rotates
     * each one into source space instead; see [unrotatedSample].
     */
    @Suppress("LongParameterList")
    fun fillRotatedImage(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rotationDegrees: Float,
        image: ImageBitmap,
        alpha: Float,
        region: TextureRegion = TextureRegion.Whole,
        tint: Color = Color.White,
        premultiplied: Boolean = false,
        blendMode: BlendMode = BlendMode.SourceOver,
    ) {
        if (rotationDegrees == 0f) {
            fillImage(x, y, w, h, image, alpha, region, tint, premultiplied, blendMode)
            return
        }
        if (w <= 0f || h <= 0f) return
        val centerX = x + w / 2f
        val centerY = y + h / 2f
        val radians = -rotationDegrees * PI.toFloat() / 180f
        val cosine = cos(radians)
        val sine = sin(radians)
        // The rotated rect's own bounding box can only ever be this far from its centre.
        val half = hypot(w, h) / 2f
        val x0 = max(centerX - half, clipX0).toInt().coerceIn(0, width)
        val y0 = max(centerY - half, clipY0).toInt().coerceIn(0, height)
        val x1 = min(centerX + half, clipX1).toInt().coerceIn(0, width)
        val y1 = min(centerY + half, clipY1).toInt().coerceIn(0, height)
        for (py in y0 until y1) {
            for (px in x0 until x1) {
                val local = unrotatedSample(px, py, centerX, centerY, cosine, sine, w, h)
                if (!local.isWithin(w, h) || !passesPathClips(px + 0.5f, py + 0.5f)) continue
                val sampled = sampleImage(image, region, tint, alpha, local.x / w, local.y / h, premultiplied)
                blendWithMode(px, py, sampled, blendMode)
            }
        }
    }

    /**
     * Samples an offscreen layer target bilinearly in premultiplied space, matching the linear
     * render-target sampler Vulkan and WebGPU bind in `GraphicsLayerCompositor`, then converts back to
     * straight alpha for [PixelMap.blend]. At integer 1:1 coordinates `(tx, ty)` land on exact texel
     * centres and return the single source pixel unchanged.
     */
    fun sampleLayerImage(image: ImageBitmap, region: TextureRegion, modulatedTint: Color, u: Float, v: Float): Color {
        val su = (region.u0 + (region.u1 - region.u0) * u).coerceIn(0f, 1f)
        val sv = (region.v0 + (region.v1 - region.v0) * v).coerceIn(0f, 1f)
        val tx = (su * image.width - 0.5f).coerceIn(0f, (image.width - 1).toFloat())
        val ty = (sv * image.height - 0.5f).coerceIn(0f, (image.height - 1).toFloat())
        val x0 = floor(tx).toInt().coerceIn(0, image.width - 1)
        val y0 = floor(ty).toInt().coerceIn(0, image.height - 1)
        val x1 = (x0 + 1).coerceIn(0, image.width - 1)
        val y1 = (y0 + 1).coerceIn(0, image.height - 1)
        val fx = tx - x0
        val fy = ty - y0
        val w00 = (1f - fx) * (1f - fy)
        val w10 = fx * (1f - fy)
        val w01 = (1f - fx) * fy
        val w11 = fx * fy

        var pr = 0f
        var pg = 0f
        var pb = 0f
        var pa = 0f

        fun accumPremul(px: Int, py: Int, weight: Float) {
            if (weight <= 0f) return
            val rgba = image.pixel(px, py)
            val a = (rgba and 0xFF) / 255f
            if (a <= 0f) return
            val wa = weight * a
            pr += ((rgba ushr 24) and 0xFF) / 255f * wa
            pg += ((rgba ushr 16) and 0xFF) / 255f * wa
            pb += ((rgba ushr 8) and 0xFF) / 255f * wa
            pa += wa
        }

        accumPremul(x0, y0, w00)
        accumPremul(x1, y0, w10)
        accumPremul(x0, y1, w01)
        accumPremul(x1, y1, w11)
        if (pa <= 0f) return Color.Transparent
        return Color(
            (pr / pa) * modulatedTint.r,
            (pg / pa) * modulatedTint.g,
            (pb / pa) * modulatedTint.b,
            pa * modulatedTint.a,
        )
    }

    @Suppress("LongParameterList")
    fun fillRotatedLayerImage(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rotationDegrees: Float,
        image: ImageBitmap,
        alpha: Float,
        region: TextureRegion = TextureRegion.Whole,
        tint: Color = Color.White,
        blendMode: BlendMode = BlendMode.SourceOver,
    ) {
        if (w <= 0f || h <= 0f) return
        if (rotationDegrees != 0f) {
            fillRotatedImage(x, y, w, h, rotationDegrees, image, alpha, region, tint, premultiplied = false, blendMode = blendMode)
            return
        }
        val modulatedTint = Color(tint.r, tint.g, tint.b, (tint.a * alpha).coerceIn(0f, 1f))
        val x0 = max(ceil(x - 0.5f).toInt(), ceil(clipX0 - 0.5f).toInt()).coerceIn(0, width)
        val y0 = max(ceil(y - 0.5f).toInt(), ceil(clipY0 - 0.5f).toInt()).coerceIn(0, height)
        val x1 = min(ceil(x + w - 0.5f).toInt(), ceil(clipX1 - 0.5f).toInt()).coerceIn(0, width)
        val y1 = min(ceil(y + h - 0.5f).toInt(), ceil(clipY1 - 0.5f).toInt()).coerceIn(0, height)
        for (py in y0 until y1) {
            val v = (py + 0.5f - y) / h
            for (px in x0 until x1) {
                if (!passesPathClips(px + 0.5f, py + 0.5f)) continue
                blendWithMode(px, py, sampleLayerImage(image, region, modulatedTint, (px + 0.5f - x) / w, v), blendMode)
            }
        }
    }

    fun sampleAtlasChannel(font: UiFont, u: Float, v: Float, channel: Int): Float {
        val atlasWidth = font.atlasWidth
        val atlasHeight = font.atlasHeight
        val atlasPixels = font.atlasPixelsRgba
        val x = (u.coerceIn(0f, 1f) * atlasWidth - 0.5f).coerceIn(0f, (atlasWidth - 1).toFloat())
        val y = (v.coerceIn(0f, 1f) * atlasHeight - 0.5f).coerceIn(0f, (atlasHeight - 1).toFloat())
        val x0 = x.toInt().coerceIn(0, atlasWidth - 1)
        val y0 = y.toInt().coerceIn(0, atlasHeight - 1)
        val x1 = (x0 + 1).coerceIn(0, atlasWidth - 1)
        val y1 = (y0 + 1).coerceIn(0, atlasHeight - 1)
        val tx = x - x0
        val ty = y - y0

        fun channelAt(px: Int, py: Int, channel: Int): Float =
            (atlasPixels[(py * atlasWidth + px) * 4 + channel].toInt() and 0xFF) / 255f

        val top = channelAt(x0, y0, channel) * (1f - tx) + channelAt(x1, y0, channel) * tx
        val bottom = channelAt(x0, y1, channel) * (1f - tx) + channelAt(x1, y1, channel) * tx
        return top * (1f - ty) + bottom * ty
    }

    fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge0 == edge1) {
            return if (value < edge0) 0f else 1f
        }
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * A coverage-alpha atlas's alpha at ([u], [v]), 0-255, shaped by [rule] when it has one; distance-field
     * glyphs take [drawDistanceFieldGlyph].
     */
    fun sampleGlyphAlpha(font: UiFont, rule: TextCoverageRule?, u: Float, v: Float): Int {
        val coverage = sampleAtlasChannel(font, u, v, 3)
        if (rule != null) {
            return (rule.alpha(coverage) * 255f).toInt().coerceIn(0, 255)
        }
        return (coverage * 255f).toInt().coerceIn(0, 255)
    }

    fun fillGradientRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        gradient: com.awakekt.awake.core.graphics2d.UiLinearGradient,
    ) {
        val x0 = max(x, clipX0).toInt().coerceIn(0, width)
        val y0 = max(y, clipY0).toInt().coerceIn(0, height)
        val x1 = min(x + w, clipX1).toInt().coerceIn(0, width)
        val y1 = min(y + h, clipY1).toInt().coerceIn(0, height)
        var py = y0
        while (py < y1) {
            var px = x0
            while (px < x1) {
                val sampleX = ((px + 0.5f - x) / w).coerceIn(0f, 1f)
                val sampleY = ((py + 0.5f - y) / h).coerceIn(0f, 1f)
                if (!passesPathClips(px + 0.5f, py + 0.5f)) {
                    px += 1
                    continue
                }
                fun lerpCol(start: Color, end: Color, fraction: Float): Color = Color(
                    r = start.r + (end.r - start.r) * fraction,
                    g = start.g + (end.g - start.g) * fraction,
                    b = start.b + (end.b - start.b) * fraction,
                    a = start.a + (end.a - start.a) * fraction,
                )

                val top = lerpCol(gradient.topLeft, gradient.topRight, sampleX)
                val bottom = lerpCol(gradient.bottomLeft, gradient.bottomRight, sampleX)
                pixelMap.blend(px, py, lerpCol(top, bottom, sampleY))
                px += 1
            }
            py += 1
        }
    }

    fun fillRoundedQuad(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rawRadius: Float,
        smoothing: Float,
        color: Color,
    ) {
        val x0 = max(x, clipX0).toInt().coerceIn(0, width)
        val y0 = max(y, clipY0).toInt().coerceIn(0, height)
        val x1 = min(x + w, clipX1).toInt().coerceIn(0, width)
        val y1 = min(y + h, clipY1).toInt().coerceIn(0, height)
        val halfW = w / 2f
        val halfH = h / 2f
        val radius = rawRadius.coerceAtMost(minOf(halfW, halfH))
        val centerX = x + halfW
        val centerY = y + halfH
        val pExp = 2.0f + 4.0f * smoothing.coerceIn(0f, 1f)

        val cr = (color.r * 255).toInt().coerceIn(0, 255)
        val cg = (color.g * 255).toInt().coerceIn(0, 255)
        val cb = (color.b * 255).toInt().coerceIn(0, 255)
        val ca = color.a

        var py = y0
        while (py < y1) {
            var px = x0
            while (px < x1) {
                val sampleX = px + 0.5f
                val sampleY = py + 0.5f
                if (!passesPathClips(sampleX, sampleY)) {
                    px += 1
                    continue
                }
                val localX = kotlin.math.abs(sampleX - centerX)
                val localY = kotlin.math.abs(sampleY - centerY)
                val qx = localX - (halfW - radius)
                val qy = localY - (halfH - radius)

                val dist = if (smoothing > 0f && qx > 0f && qy > 0f) {
                    (qx.toDouble().pow(pExp.toDouble()) + qy.toDouble().pow(pExp.toDouble())).pow(
                        1.0 / pExp.toDouble(),
                    ).toFloat() - radius
                } else {
                    val maxQx = max(qx, 0f)
                    val maxQy = max(qy, 0f)
                    kotlin.math.sqrt(maxQx * maxQx + maxQy * maxQy) + min(max(qx, qy), 0f) - radius
                }

                if (dist <= 1.0f) {
                    val alphaFactor = (1.0f - smoothstep(-1.0f, 1.0f, dist)) * ca
                    pixelMap.blend(px, py, cr.toFloat(), cg.toFloat(), cb.toFloat(), alphaFactor)
                }
                px += 1
            }
            py += 1
        }
    }

    // Mirrors buildShadowQuadRun's rounded-box distance, spread, blur floor and padded footprint.
    fun fillShadowQuad(shadow: UiDrawPrimitive.ShadowQuad) {
        val halfW = (shadow.w / 2f + shadow.spread).coerceAtLeast(0f)
        val halfH = (shadow.h / 2f + shadow.spread).coerceAtLeast(0f)
        val radius = (shadow.radius + shadow.spread).coerceIn(0f, min(halfW, halfH))
        val blur = shadow.blurRadius.coerceAtLeast(1f)
        val pad = blur + 1f
        val centerX = shadow.x + shadow.offsetX + shadow.w / 2f
        val centerY = shadow.y + shadow.offsetY + shadow.h / 2f
        val left = centerX - halfW - pad
        val top = centerY - halfH - pad
        val x0 = max(left, clipX0).toInt().coerceIn(0, width)
        val y0 = max(top, clipY0).toInt().coerceIn(0, height)
        val x1 = ceil(min(centerX + halfW + pad, clipX1)).toInt().coerceIn(0, width)
        val y1 = ceil(min(centerY + halfH + pad, clipY1)).toInt().coerceIn(0, height)
        fun distance(x: Float, y: Float): Float {
            val qx = abs(x - centerX) - halfW + radius
            val qy = abs(y - centerY) - halfH + radius
            return hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - radius
        }
        for (py in y0 until y1) {
            for (px in x0 until x1) {
                val x = px + 0.5f
                val y = py + 0.5f
                val dist = distance(x, y)
                // Derivatives use adjacent fragments in a 2x2 quad, like shader fwidth.
                val dx = abs(distance((px - (px and 1)) + 1.5f, y) - distance((px - (px and 1)) + 0.5f, y))
                val dy = abs(distance(x, (py - (py and 1)) + 1.5f) - distance(x, (py - (py and 1)) + 0.5f))
                val aa = max(dx + dy, blur * 0.5f)
                val coverage = 1f - smoothstep(-aa, aa, dist)
                if (coverage <= 0f || !passesPathClips(x, y)) continue
                val gradient = shadow.gradient
                val color = if (gradient == null) {
                    shadow.color
                } else {
                    val u = ((x - left) / (2f * (halfW + pad))).coerceIn(0f, 1f)
                    val v = ((y - top) / (2f * (halfH + pad))).coerceIn(0f, 1f)
                    // GPU vertex colours interpolate across TL/TR/BR and TL/BR/BL triangles.
                    val a = gradient.topLeft
                    val b = if (u >= v) gradient.topRight else gradient.bottomLeft
                    val c = gradient.bottomRight
                    val wa = 1f - max(u, v)
                    val wb = abs(u - v)
                    val wc = min(u, v)
                    Color(
                        a.r * wa + b.r * wb + c.r * wc,
                        a.g * wa + b.g * wb + c.g * wc,
                        a.b * wa + b.b * wb + c.b * wc,
                        a.a * wa + b.a * wb + c.a * wc,
                    )
                }
                pixelMap.blend(px, py, color.withAlpha(color.a * coverage))
            }
        }
    }

    fun drawColorGlyph(glyph: UiDrawPrimitive.Glyph, font: UiFont) {
        val x0 = max(glyph.x, clipX0).toInt().coerceIn(0, width)
        val y0 = max(glyph.y, clipY0).toInt().coerceIn(0, height)
        val x1 = min(glyph.x + glyph.w, clipX1).toInt().coerceIn(0, width)
        val y1 = min(glyph.y + glyph.h, clipY1).toInt().coerceIn(0, height)
        val sourceTexelsX = (glyph.u1 - glyph.u0) * font.atlasWidth
        val sourceTexelsY = (glyph.v1 - glyph.v0) * font.atlasHeight
        val samplesX = glyphSampleCount(sourceTexelsX, glyph.w)
        val samplesY = glyphSampleCount(sourceTexelsY, glyph.h)
        val sampleCount = (samplesX * samplesY).toFloat()

        var py = y0
        while (py < y1) {
            var px = x0
            while (px < x1) {
                if (!passesPathClips(px + 0.5f, py + 0.5f)) {
                    px += 1
                    continue
                }
                var rSum = 0f
                var gSum = 0f
                var bSum = 0f
                var aSum = 0f
                for (sy in 0 until samplesY) {
                    for (sx in 0 until samplesX) {
                        val sampleX = px + (sx + 0.5f) / samplesX
                        val sampleY = py + (sy + 0.5f) / samplesY
                        val u = ((sampleX - glyph.x) / glyph.w).coerceIn(0f, 0.9999f)
                        val v = ((sampleY - glyph.y) / glyph.h).coerceIn(0f, 0.9999f)
                        val su = glyph.u0 + (glyph.u1 - glyph.u0) * u
                        val sv = glyph.v0 + (glyph.v1 - glyph.v0) * v
                        rSum += sampleAtlasChannel(font, su, sv, 0)
                        gSum += sampleAtlasChannel(font, su, sv, 1)
                        bSum += sampleAtlasChannel(font, su, sv, 2)
                        aSum += sampleAtlasChannel(font, su, sv, 3)
                    }
                }
                val alpha = (aSum / sampleCount) * glyph.color.a
                if (alpha > 0f) {
                    pixelMap.blend(
                        px,
                        py,
                        (rSum / sampleCount) * 255f,
                        (gSum / sampleCount) * 255f,
                        (bSum / sampleCount) * 255f,
                        alpha,
                    )
                }
                px += 1
            }
            py += 1
        }
    }

    /**
     * A distance-field glyph, drawn the way `AslUiShaders.UiGlyphShader` draws it: one bilinear,
     * clamp-to-edge atlas sample at each covered pixel's centre (the pixels whose centres the quad
     * covers, as the GPU rasterizes it), [distanceFieldCoverage] at [distanceFieldScreenPxRange],
     * shaped by [textCoverageAlpha], and the tint's alpha times that, kept in float until the blend.
     *
     * No supersampling: the shader takes one sample a pixel, and a distance field needs none to
     * stay sharp. Averaging several coverage samples a pixel, as the coverage-atlas path does,
     * blurred every distance-field glyph drawn smaller than its atlas cell, which no backend does.
     */
    fun drawDistanceFieldGlyph(glyph: UiDrawPrimitive.Glyph, font: UiFont) {
        val uvWidth = glyph.u1 - glyph.u0
        val uvHeight = glyph.v1 - glyph.v0
        val screenPxRange = distanceFieldScreenPxRange(
            font.distanceFieldRangePx,
            font.atlasWidth,
            font.atlasHeight,
            uvWidth,
            uvHeight,
            glyph.w,
            glyph.h,
        )
        val x0 = max(ceil(glyph.x - 0.5f).toInt(), ceil(clipX0 - 0.5f).toInt()).coerceIn(0, width)
        val y0 = max(ceil(glyph.y - 0.5f).toInt(), ceil(clipY0 - 0.5f).toInt()).coerceIn(0, height)
        val x1 = min(ceil(glyph.x + glyph.w - 0.5f).toInt(), ceil(clipX1 - 0.5f).toInt()).coerceIn(0, width)
        val y1 = min(ceil(glyph.y + glyph.h - 0.5f).toInt(), ceil(clipY1 - 0.5f).toInt()).coerceIn(0, height)
        val r = glyph.color.r * 255f
        val g = glyph.color.g * 255f
        val b = glyph.color.b * 255f
        val tintAlpha = glyph.color.a
        val rule = TextCoverageRule(glyph.color)
        var py = y0
        while (py < y1) {
            val v = glyph.v0 + (py + 0.5f - glyph.y) / glyph.h * uvHeight
            var px = x0
            while (px < x1) {
                if (passesPathClips(px + 0.5f, py + 0.5f)) {
                    val u = glyph.u0 + (px + 0.5f - glyph.x) / glyph.w * uvWidth
                    val coverage = distanceFieldCoverage(
                        sampleAtlasChannel(font, u, v, 0),
                        sampleAtlasChannel(font, u, v, 1),
                        sampleAtlasChannel(font, u, v, 2),
                        screenPxRange,
                    )
                    pixelMap.blend(px, py, r, g, b, rule.alpha(coverage) * tintAlpha)
                }
                px += 1
            }
            py += 1
        }
    }

    /**
     * A decorated glyph (#1061), drawn the way `AslDecoratedGlyphShader` draws it: its quad grown to the decoration's
     * reach, each covered pixel's centre shaded by [DecoratedGlyphShade], kept in float until the blend.
     */
    fun drawDecoratedGlyph(glyph: UiDrawPrimitive.Glyph, decoration: GlyphDecoration, font: UiFont) {
        val bounds = FloatArray(4)
        decoration.drawnBounds(glyph.x, glyph.y, glyph.w, glyph.h, bounds)
        val shade = DecoratedGlyphShade(glyph, decoration, font) { u, v, channel -> sampleAtlasChannel(font, u, v, channel) }
        val x0 = max(ceil(bounds[0] - 0.5f).toInt(), clipX0.toInt()).coerceIn(0, width)
        val y0 = max(ceil(bounds[1] - 0.5f).toInt(), clipY0.toInt()).coerceIn(0, height)
        val x1 = min(ceil(bounds[2] - 0.5f).toInt(), clipX1.toInt()).coerceIn(0, width)
        val y1 = min(ceil(bounds[3] - 0.5f).toInt(), clipY1.toInt()).coerceIn(0, height)
        val rgba = FloatArray(4)
        for (py in y0 until y1) {
            for (px in x0 until x1) {
                if (!passesPathClips(px + 0.5f, py + 0.5f)) continue
                shade.shade(px + 0.5f, py + 0.5f, rgba)
                if (rgba[3] > 0f) pixelMap.blend(px, py, rgba[0] * 255f, rgba[1] * 255f, rgba[2] * 255f, rgba[3])
            }
        }
    }

    fun drawGlyph(glyph: UiDrawPrimitive.Glyph, font: UiFont) {
        if (font.samplingMode == UiFontSamplingMode.ColorRgba) {
            drawColorGlyph(glyph, font)
            return
        }
        if (font.samplingMode == UiFontSamplingMode.DistanceField) {
            drawDistanceFieldGlyph(glyph, font)
            return
        }
        val x0 = max(ceil(glyph.x - 0.5f).toInt(), ceil(clipX0 - 0.5f).toInt()).coerceIn(0, width)
        val y0 = max(ceil(glyph.y - 0.5f).toInt(), ceil(clipY0 - 0.5f).toInt()).coerceIn(0, height)
        val x1 = min(ceil(glyph.x + glyph.w - 0.5f).toInt(), ceil(clipX1 - 0.5f).toInt()).coerceIn(0, width)
        val y1 = min(ceil(glyph.y + glyph.h - 0.5f).toInt(), ceil(clipY1 - 0.5f).toInt()).coerceIn(0, height)
        val r = (glyph.color[0] * 255).toInt().coerceIn(0, 255)
        val g = (glyph.color[1] * 255).toInt().coerceIn(0, 255)
        val b = (glyph.color[2] * 255).toInt().coerceIn(0, 255)
        val tintAlpha = (glyph.color.a * 255).toInt().coerceIn(0, 255)
        // Exact coverage takes the text coverage rule; coverage already shaped, as a bitmap font's, draws as it is.
        val rule = if (font.linearCoverage) TextCoverageRule(glyph.color) else null

        val sourceTexelsX = (glyph.u1 - glyph.u0) * font.atlasWidth
        val sourceTexelsY = (glyph.v1 - glyph.v0) * font.atlasHeight
        // Cover every source-texel footprint. Truncating here made a 1.9x footprint use one
        // sample, so glyphs changed apparent weight with fractional position and looked blurrier
        // in headless proofs than in the GPU's derivative-aware shader.
        val samplesX = glyphSampleCount(sourceTexelsX, glyph.w)
        val samplesY = glyphSampleCount(sourceTexelsY, glyph.h)

        var py = y0
        while (py < y1) {
            var px = x0
            while (px < x1) {
                if (!passesPathClips(px + 0.5f, py + 0.5f)) {
                    px += 1
                    continue
                }
                var alphaSum = 0
                for (sy in 0 until samplesY) {
                    for (sx in 0 until samplesX) {
                        val sampleX = px + (sx + 0.5f) / samplesX
                        val sampleY = py + (sy + 0.5f) / samplesY
                        val rawU = (sampleX - glyph.x) / glyph.w
                        val rawV = (sampleY - glyph.y) / glyph.h
                        val u = if (samplesX == 1) rawU else rawU.coerceIn(0f, 0.9999f)
                        val v = if (samplesY == 1) rawV else rawV.coerceIn(0f, 0.9999f)
                        alphaSum += sampleGlyphAlpha(
                            font,
                            rule,
                            glyph.u0 + (glyph.u1 - glyph.u0) * u,
                            glyph.v0 + (glyph.v1 - glyph.v0) * v,
                        )
                    }
                }
                val sourceAlpha = alphaSum / (samplesX * samplesY)
                if (sourceAlpha == 0) {
                    px += 1
                    continue
                }
                val alpha = (sourceAlpha * tintAlpha) / 255
                pixelMap.blend(px, py, r.toFloat(), g.toFloat(), b.toFloat(), alpha / 255f)
                px += 1
            }
            py += 1
        }
    }

    fun fillTriangle(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float,
        color: Color,
        stamp: Int,
    ) {
        rasterizeTrianglePixels(
            ax, ay, bx, by, cx, cy,
            clipX0, clipY0, clipX1, clipY1, width, height,
            ::passesPathClips,
        ) { px, py, _, _, _, _ ->
            val cell = py * width + px
            if (meshCoverage[cell] != stamp) {
                meshCoverage[cell] = stamp
                pixelMap.blend(px, py, color)
            }
        }
    }

    fun fillColoredTriangle(
        a: com.awakekt.awake.core.graphics2d.UiColoredVertex,
        b: com.awakekt.awake.core.graphics2d.UiColoredVertex,
        c: com.awakekt.awake.core.graphics2d.UiColoredVertex,
        stamp: Int,
    ) {
        val ax = a.position.x
        val ay = a.position.y
        val bx = b.position.x
        val by = b.position.y
        val cx = c.position.x
        val cy = c.position.y
        rasterizeTrianglePixels(
            ax, ay, bx, by, cx, cy,
            clipX0, clipY0, clipX1, clipY1, width, height,
            ::passesPathClips,
        ) { px, py, w0, w1, w2, area ->
            val cell = py * width + px
            if (meshCoverage[cell] != stamp) {
                meshCoverage[cell] = stamp
                val la = w1 / area
                val lb = w2 / area
                val lc = w0 / area
                val r = (a.color.r * la + b.color.r * lb + c.color.r * lc) * 255f
                val g = (a.color.g * la + b.color.g * lb + c.color.g * lc) * 255f
                val bl = (a.color.b * la + b.color.b * lb + c.color.b * lc) * 255f
                val alpha = (a.color.a * la + b.color.a * lb + c.color.a * lc).coerceIn(0f, 1f)
                pixelMap.blend(px, py, r, g, bl, alpha)
            }
        }
    }

    fun fillColoredTriangleMesh(mesh: com.awakekt.awake.core.graphics2d.UiColoredTriangleMesh) {
        meshStamp += 1
        val stamp = meshStamp
        var index = 0
        while (index + 2 < mesh.indices.size) {
            fillColoredTriangle(
                mesh.vertices[mesh.indices[index]],
                mesh.vertices[mesh.indices[index + 1]],
                mesh.vertices[mesh.indices[index + 2]],
                stamp,
            )
            index += 3
        }
    }

    fun fillTriangleMesh(
        path: com.awakekt.awake.core.graphics2d.UiTriangleMesh,
        color: Color,
    ) {
        meshStamp += 1
        var index = 0
        while (index + 2 < path.indices.size) {
            val a = path.points[path.indices[index]]
            val b = path.points[path.indices[index + 1]]
            val c = path.points[path.indices[index + 2]]
            fillTriangle(a.x, a.y, b.x, b.y, c.x, c.y, color, meshStamp)
            index += 3
        }
    }

    for (primitive in this) {
        when (primitive) {
            is UiDrawPrimitive.Quad -> {
                withScaledRect(
                    primitive.x,
                    primitive.y,
                    primitive.w,
                    primitive.h,
                    primitive.transform,
                ) { x, y, w, h -> fillRect(x, y, w, h, primitive.color) }
            }

            is UiDrawPrimitive.GradientQuad -> fillGradientRect(
                primitive.x,
                primitive.y,
                primitive.w,
                primitive.h,
                primitive.gradient,
            )

            is UiDrawPrimitive.RoundedQuad -> {
                withScaledRect(
                    primitive.x,
                    primitive.y,
                    primitive.w,
                    primitive.h,
                    primitive.transform,
                ) { x, y, w, h ->
                    val scale = min(primitive.transform?.scaleX ?: 1f, primitive.transform?.scaleY ?: 1f)
                    fillRoundedQuad(x, y, w, h, primitive.radius * scale, primitive.smoothing, primitive.color)
                }
            }

            is UiDrawPrimitive.FilledPath -> fillColoredTriangleMesh(
                primitive.path.tessellateFillAa(
                    primitive.color,
                ),
            )

            is UiDrawPrimitive.StrokedPath -> fillColoredTriangleMesh(
                primitive.path.tessellateStrokeAa(primitive.stroke, primitive.color),
            )

            is UiDrawPrimitive.Mesh -> fillColoredTriangleMesh(primitive.placedMesh())

            is UiDrawPrimitive.Glyph -> {
                withScaledRect(
                    primitive.x,
                    primitive.y,
                    primitive.w,
                    primitive.h,
                    primitive.transform,
                ) { x, y, w, h ->
                    val transform = primitive.transform
                    val scaledGlyph = if (transform == null) {
                        primitive
                    } else {
                        // Placed from the glyph's own corner, the decoration only scales with it.
                        val decoration = primitive.decoration?.scaled(transform.scaleX, transform.scaleY)
                        primitive.copy(x = x, y = y, w = w, h = h, decoration = decoration)
                    }
                    val glyphFont = (primitive.font ?: font)?.pageFont(primitive.page)
                    val decoration = scaledGlyph.decoration
                    when {
                        glyphFont == null -> fillRect(x, y, w, h, MissingFontColor)
                        decoration != null && glyphFont.effectFieldRangePx > 0f &&
                            glyphFont.samplingMode == UiFontSamplingMode.DistanceField -> drawDecoratedGlyph(scaledGlyph, decoration, glyphFont)
                        else -> drawGlyph(scaledGlyph, glyphFont)
                    }
                }
            }

            is UiDrawPrimitive.Texture -> {
                withScaledRect(
                    primitive.x,
                    primitive.y,
                    primitive.w,
                    primitive.h,
                    primitive.transform,
                ) { x, y, w, h ->
                    val image = primitive.material as? ImageBitmap
                    val layerImage = if (image == null) {
                        (primitive.material as? GraphicsLayerPlaceholder)?.let { layerImages[it.id] }
                    } else {
                        null
                    }
                    when {
                        image != null -> fillRotatedImage(
                            x,
                            y,
                            w,
                            h,
                            primitive.rotationDegrees,
                            image,
                            primitive.alpha,
                            primitive.region,
                            primitive.tint,
                            primitive.premultiplied,
                            primitive.blendMode,
                        )
                        layerImage != null -> fillRotatedLayerImage(
                            x,
                            y,
                            w,
                            h,
                            primitive.rotationDegrees,
                            layerImage,
                            primitive.alpha,
                            primitive.region,
                            primitive.tint,
                            primitive.blendMode,
                        )
                        else -> fillRect(x, y, w, h, Color(0.5f, 0.5f, 0.5f, 1f))
                    }
                }
            }

            is UiDrawPrimitive.ShadowQuad -> fillShadowQuad(primitive)

            is UiDrawPrimitive.BackdropBlur -> {
                val effectiveColor = if (primitive.fallback.a > 0f) {
                    primitive.fallback.withAlpha(primitive.fallback.a * primitive.alpha.coerceAtMost(1f))
                } else {
                    primitive.tint.withAlpha(primitive.tint.a * primitive.alpha.coerceAtMost(1f))
                }
                if (effectiveColor.a > 0f) {
                    val path = primitive.path
                    if (path != null) {
                        fillColoredTriangleMesh(path.tessellateFillAa(effectiveColor))
                    } else {
                        withScaledRect(
                            primitive.x,
                            primitive.y,
                            primitive.w,
                            primitive.h,
                            primitive.transform,
                        ) { x, y, w, h ->
                            val scale = min(primitive.transform?.scaleX ?: 1f, primitive.transform?.scaleY ?: 1f)
                            if (primitive.cornerRadius > 0f) {
                                fillRoundedQuad(x, y, w, h, primitive.cornerRadius * scale, primitive.smoothing, effectiveColor)
                            } else {
                                fillRect(x, y, w, h, effectiveColor)
                            }
                        }
                    }
                }
            }

            is UiDrawPrimitive.ClipPathPush -> {
                clipStack.addLast(
                    ClipSnapshot(
                        floatArrayOf(clipX0, clipY0, clipX1, clipY1),
                        activePathClips.size,
                    ),
                )
                clipX0 = max(clipX0, primitive.boundsRect.x)
                clipY0 = max(clipY0, primitive.boundsRect.y)
                clipX1 = min(clipX1, primitive.boundsRect.x + primitive.boundsRect.width)
                clipY1 = min(clipY1, primitive.boundsRect.y + primitive.boundsRect.height)
                activePathClips += primitive.path
            }

            is UiDrawPrimitive.ClipPush -> {
                clipStack.addLast(
                    ClipSnapshot(
                        floatArrayOf(clipX0, clipY0, clipX1, clipY1),
                        activePathClips.size,
                    ),
                )
                clipX0 = max(clipX0, primitive.rect.x)
                clipY0 = max(clipY0, primitive.rect.y)
                clipX1 = min(clipX1, primitive.rect.x + primitive.rect.width)
                clipY1 = min(clipY1, primitive.rect.y + primitive.rect.height)
            }

            is UiDrawPrimitive.ClipPop -> {
                clipStack.removeLastOrNull()?.let { restored ->
                    clipX0 = restored.rect[0]
                    clipY0 = restored.rect[1]
                    clipX1 = restored.rect[2]
                    clipY1 = restored.rect[3]
                    while (activePathClips.size > restored.activePathCount) {
                        activePathClips.removeAt(activePathClips.lastIndex)
                    }
                }
            }
        }
    }
    return pixelMap
}
