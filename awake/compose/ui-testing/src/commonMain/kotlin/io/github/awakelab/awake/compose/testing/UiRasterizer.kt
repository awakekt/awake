/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.testing

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.core.graphics2d.containsPoint
import io.github.awakelab.awake.core.graphics2d.tessellateStrokeAa
import io.github.awakelab.awake.core.graphics2d.tessellateFillAa
import io.github.awakelab.awake.core.text.font.UiFont
import io.github.awakelab.awake.core.text.font.UiFontSamplingMode
import io.github.awakelab.awake.render.capture.PixelMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val GLYPH_GAMMA = 1.45f
private const val MIN_TEXEL_WIDTH = 1e-4f
private val MissingFontColor = Color(1f, 0f, 1f, 1f)

/** Supersamples every source-texel footprint without allowing pathological work per pixel. */
internal fun glyphSampleCount(sourceTexels: Float, screenPixels: Float): Int =
    ceil(sourceTexels / screenPixels.coerceAtLeast(1f)).toInt().coerceIn(1, 6)

/**
 * Software-rasterizes a [UiDrawPrimitive] list into a tightly-packed RGBA8 buffer for
 * preview/docs/snapshot review.
 */
fun List<UiDrawPrimitive>.rasterize(
    width: Int,
    height: Int,
    background: Color = Color(0.1f, 0.1f, 0.12f, 1f),
    font: UiFont? = null,
): ByteArray = rasterizeToPixelMap(width, height, background, font).pixels

/** Software-rasterizes UI primitives into the shared diagnostic pixel representation. */
fun List<UiDrawPrimitive>.rasterizeToPixelMap(
    width: Int,
    height: Int,
    background: Color = Color(0.1f, 0.1f, 0.12f, 1f),
    font: UiFont? = null,
): PixelMap {
    val pixelMap = PixelMap(width, height)
    pixelMap.fill(background)
    val meshCoverage = IntArray(width * height)
    var meshStamp = 0

    var clipX0 = 0f
    var clipY0 = 0f
    var clipX1 = width.toFloat()
    var clipY1 = height.toFloat()

    data class ClipSnapshot(val rect: FloatArray, val activePathCount: Int)

    val clipStack = ArrayDeque<ClipSnapshot>()
    val activePathClips = ArrayList<io.github.awakelab.awake.core.graphics2d.DrawPath>()

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

    fun median3(a: Float, b: Float, c: Float): Float = max(min(a, b), min(max(a, b), c))

    fun sampleGlyphAlpha(font: UiFont, glyph: UiDrawPrimitive.Glyph, u: Float, v: Float): Int {
        val coverage = when (font.samplingMode) {
            UiFontSamplingMode.CoverageAlpha -> sampleAtlasChannel(font, u, v, 3)
            UiFontSamplingMode.DistanceField -> {
                val signedDistance = median3(
                    sampleAtlasChannel(font, u, v, 0),
                    sampleAtlasChannel(font, u, v, 1),
                    sampleAtlasChannel(font, u, v, 2),
                )
                val texelWidth = max(MIN_TEXEL_WIDTH, (glyph.u1 - glyph.u0) * font.atlasWidth)
                val screenPxPerTexel = glyph.w / texelWidth
                val screenPxRange = max(0.5f * font.distanceFieldRangePx * screenPxPerTexel, 1f)
                (screenPxRange * (signedDistance - 0.5f) + 0.5f).coerceIn(0f, 1f)
            }
        }
        val gamma = if (font.samplingMode == UiFontSamplingMode.CoverageAlpha) 1f else GLYPH_GAMMA
        return (coverage.pow(1f / gamma) * 255f).toInt().coerceIn(0, 255)
    }

    fun fillGradientRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        gradient: io.github.awakelab.awake.core.graphics2d.UiLinearGradient
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
        color: Color
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
                        1.0 / pExp.toDouble()
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

    fun drawGlyph(glyph: UiDrawPrimitive.Glyph, font: UiFont) {
        val x0 = max(glyph.x, clipX0).toInt().coerceIn(0, width)
        val y0 = max(glyph.y, clipY0).toInt().coerceIn(0, height)
        val x1 = min(glyph.x + glyph.w, clipX1).toInt().coerceIn(0, width)
        val y1 = min(glyph.y + glyph.h, clipY1).toInt().coerceIn(0, height)
        val r = (glyph.color[0] * 255).toInt().coerceIn(0, 255)
        val g = (glyph.color[1] * 255).toInt().coerceIn(0, 255)
        val b = (glyph.color[2] * 255).toInt().coerceIn(0, 255)
        val tintAlpha = (glyph.color.a * 255).toInt().coerceIn(0, 255)

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
                        val u = ((sampleX - glyph.x) / glyph.w).coerceIn(0f, 0.9999f)
                        val v = ((sampleY - glyph.y) / glyph.h).coerceIn(0f, 0.9999f)
                        alphaSum += sampleGlyphAlpha(
                            font,
                            glyph,
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
        stamp: Int
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
        a: io.github.awakelab.awake.core.graphics2d.UiColoredVertex,
        b: io.github.awakelab.awake.core.graphics2d.UiColoredVertex,
        c: io.github.awakelab.awake.core.graphics2d.UiColoredVertex,
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

    fun fillColoredTriangleMesh(mesh: io.github.awakelab.awake.core.graphics2d.UiColoredTriangleMesh) {
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
        path: io.github.awakelab.awake.core.graphics2d.UiTriangleMesh,
        color: Color
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
                primitive.gradient
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
                    primitive.color
                )
            )

            is UiDrawPrimitive.StrokedPath -> fillColoredTriangleMesh(
                primitive.path.tessellateStrokeAa(primitive.stroke, primitive.color)
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
                    val scaledGlyph = if (primitive.transform == null) primitive else primitive.copy(x = x, y = y, w = w, h = h)
                    if (font != null) drawGlyph(scaledGlyph, font) else fillRect(x, y, w, h, MissingFontColor)
                }
            }

            is UiDrawPrimitive.Texture -> {
                withScaledRect(
                    primitive.x,
                    primitive.y,
                    primitive.w,
                    primitive.h,
                    primitive.transform,
                ) { x, y, w, h -> fillRect(x, y, w, h, Color(0.5f, 0.5f, 0.5f, 1f)) }
            }

            is UiDrawPrimitive.ShadowQuad -> {
                val x = primitive.x + primitive.offsetX - primitive.spread
                val y = primitive.y + primitive.offsetY - primitive.spread
                val w = primitive.w + primitive.spread * 2f
                val h = primitive.h + primitive.spread * 2f
                val gradient = primitive.gradient
                if (gradient == null) fillRect(x, y, w, h, primitive.color) else fillGradientRect(x, y, w, h, gradient)
            }

            is UiDrawPrimitive.ClipPathPush -> {
                clipStack.addLast(
                    ClipSnapshot(
                        floatArrayOf(clipX0, clipY0, clipX1, clipY1),
                        activePathClips.size
                    )
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
                        activePathClips.size
                    )
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
