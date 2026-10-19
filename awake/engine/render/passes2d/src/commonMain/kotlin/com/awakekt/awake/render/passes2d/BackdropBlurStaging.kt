/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawTransform
import com.awakekt.awake.core.graphics2d.bounds
import com.awakekt.awake.render.effects.BackdropRegion
import com.awakekt.awake.render.effects.BlurChain
import com.awakekt.awake.render.imagepass.ImagePassTargetContext
import com.awakekt.awake.render.pipeline.EffectQuality
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One blur in a frame: the [region] of the backdrop it reads and the [blur] parameters
 * (halvings, tap offset and output level). Every panel that shares it samples the same result.
 *
 * @property blur The passes' shape: halvings, tap offset and output level.
 * @property width The backdrop's width in pixels, which sizes every level.
 * @property height The backdrop's height in pixels.
 * @param initialRegion The bounding region of the first panel opening this blur.
 */
class BackdropBlurWork internal constructor(
    val blur: BlurChain,
    val width: Int,
    val height: Int,
    initialRegion: BackdropRegion,
) {
    /** The full-resolution pixels the blur reads: the union of its panels' regions. */
    var region: BackdropRegion = initialRegion
        private set

    internal var led = false

    internal fun include(other: BackdropRegion) {
        region = BackdropRegion(
            left = min(region.left, other.left),
            top = min(region.top, other.top),
            right = max(region.right, other.right),
            bottom = max(region.bottom, other.bottom),
        )
    }
}

/**
 * Decides, panel by panel in paint order, which blur each [DrawCommand.BackdropBlur] samples.
 *
 * A panel shares the open blur when it asks for the same blur and nothing painted since that blur's
 * first panel touches the pixels it reads: then that blur, run before any of it, already saw
 * exactly this panel's backdrop. Otherwise it opens a new blur, while the quality's per-frame limit
 * allows; past it the panel falls back to its tint. A new blur closes the old one, so blurs never
 * interleave and one set of level textures serves the whole frame.
 */
internal class BackdropGrouping(private val target: ImagePassTargetContext) {
    val works = ArrayList<BackdropBlurWork>()
    private var open: BackdropBlurWork? = null
    private var dirtyLeft = Float.POSITIVE_INFINITY
    private var dirtyTop = Float.POSITIVE_INFINITY
    private var dirtyRight = Float.NEGATIVE_INFINITY
    private var dirtyBottom = Float.NEGATIVE_INFINITY

    /** Whether a later panel could still share a blur, which is when painting has to be watched. */
    val watching: Boolean get() = open != null

    private val isTargetEligible: Boolean
        get() = target.effectQuality != EffectQuality.Off &&
            target.snapshotSupported &&
            target.width > 0 &&
            target.height > 0

    /**
     * The blur a panel with these bounds and [radius] samples, or null when it falls back.
     */
    @Suppress("ReturnCount")
    fun place(left: Float, top: Float, right: Float, bottom: Float, radius: Float): BackdropBlurWork? {
        if (!isTargetEligible) {
            return null
        }
        val blur = BlurChain.forRadius(radius, target.effectQuality) ?: return null
        val region = BackdropRegion.around(left, top, right - left, bottom - top, blur.extent, target.width, target.height)
        if (region.isEmpty) return null
        val current = open
        if (current != null && current.blur == blur && !dirtyTouches(region)) {
            current.include(region)
            return current
        }
        if (works.size >= BlurChain.maxBlursPerFrame(target.effectQuality)) return null
        val work = BackdropBlurWork(blur, target.width, target.height, region)
        works += work
        open = work
        dirtyLeft = Float.POSITIVE_INFINITY
        dirtyTop = Float.POSITIVE_INFINITY
        dirtyRight = Float.NEGATIVE_INFINITY
        dirtyBottom = Float.NEGATIVE_INFINITY
        return work
    }

    /** Records that the pixels in these bounds may have changed since the open blur ran. */
    fun painted(left: Float, top: Float, right: Float, bottom: Float) {
        if (open == null || right <= left || bottom <= top) return
        dirtyLeft = min(dirtyLeft, left)
        dirtyTop = min(dirtyTop, top)
        dirtyRight = max(dirtyRight, right)
        dirtyBottom = max(dirtyBottom, bottom)
    }

    /** [painted] for whatever [primitive] can cover. */
    fun painted(primitive: DrawCommand) {
        if (open != null) paintBounds(primitive)
    }

    private fun dirtyTouches(region: BackdropRegion): Boolean =
        dirtyRight > region.left && dirtyLeft < region.right && dirtyBottom > region.top && dirtyTop < region.bottom

    @Suppress("CyclomaticComplexMethod")
    private fun paintBounds(primitive: DrawCommand) {
        when (primitive) {
            is DrawCommand.Quad -> rect(primitive.x, primitive.y, primitive.w, primitive.h, primitive.transform)
            is DrawCommand.RoundedQuad -> rect(primitive.x, primitive.y, primitive.w, primitive.h, primitive.transform)
            is DrawCommand.Glyph -> rect(primitive.x, primitive.y, primitive.w, primitive.h, primitive.transform)
            is DrawCommand.GradientQuad -> painted(primitive.x, primitive.y, primitive.x + primitive.w, primitive.y + primitive.h)
            is DrawCommand.Texture -> {
                if (primitive.rotationDegrees == 0f) {
                    rect(primitive.x, primitive.y, primitive.w, primitive.h, primitive.transform)
                } else {
                    val half = sqrt(primitive.w * primitive.w + primitive.h * primitive.h) / 2f
                    val cx = primitive.x + primitive.w / 2f
                    val cy = primitive.y + primitive.h / 2f
                    rect(cx - half, cy - half, 2f * half, 2f * half, primitive.transform)
                }
            }
            is DrawCommand.ShadowQuad -> {
                val grow = primitive.spread + primitive.blurRadius + 1f
                painted(
                    primitive.x + primitive.offsetX - grow,
                    primitive.y + primitive.offsetY - grow,
                    primitive.x + primitive.offsetX + primitive.w + grow,
                    primitive.y + primitive.offsetY + primitive.h + grow,
                )
            }
            is DrawCommand.FilledPath -> {
                val bounds = primitive.path.bounds()
                painted(bounds.x - 1f, bounds.y - 1f, bounds.x + bounds.width + 1f, bounds.y + bounds.height + 1f)
            }
            is DrawCommand.StrokedPath -> {
                val bounds = primitive.path.bounds()
                val grow = primitive.stroke.width.value * 2f + 1f
                painted(bounds.x - grow, bounds.y - grow, bounds.x + bounds.width + grow, bounds.y + bounds.height + grow)
            }
            is DrawCommand.Mesh -> meshBounds(primitive)
            is DrawCommand.BackdropBlur -> painted(primitive.placedLeft(), primitive.placedTop(), primitive.placedRight(), primitive.placedBottom())
            is DrawCommand.ClipPathPush, is DrawCommand.ClipPush, is DrawCommand.ClipPop -> Unit
        }
    }

    private fun rect(x: Float, y: Float, w: Float, h: Float, transform: DrawTransform?) {
        val left = scaledX(x, transform)
        val right = scaledX(x + w, transform)
        val top = scaledY(y, transform)
        val bottom = scaledY(y + h, transform)
        painted(min(left, right), min(top, bottom), max(left, right), max(top, bottom))
    }

    private fun meshBounds(primitive: DrawCommand.Mesh) {
        val radians = primitive.rotationDegrees * PI.toFloat() / 180f
        val cosine = cos(radians)
        val sine = sin(radians)
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        val vertices = primitive.mesh.vertices
        for (index in vertices.indices) {
            val point = vertices[index].position
            val dx = point.x - primitive.pivotX
            val dy = point.y - primitive.pivotY
            val x = (primitive.pivotX + dx * cosine - dy * sine) * primitive.scaleX + primitive.offsetX
            val y = (primitive.pivotY + dx * sine + dy * cosine) * primitive.scaleY + primitive.offsetY
            left = min(left, x)
            top = min(top, y)
            right = max(right, x)
            bottom = max(bottom, y)
        }
        painted(left - 1f, top - 1f, right + 1f, bottom + 1f)
    }

    companion object {
        /**
         * Pre-scans [primitives] in paint order to resolve the shared [BackdropBlurWork] for each
         * [DrawCommand.BackdropBlur], expanding blur regions to cover all non-interleaved sharing panels.
         */
        fun analyze(
            primitives: List<DrawCommand>,
            target: ImagePassTargetContext,
        ): Array<BackdropBlurWork?> {
            val grouping = BackdropGrouping(target)
            val result = arrayOfNulls<BackdropBlurWork>(primitives.size)
            for (index in primitives.indices) {
                when (val primitive = primitives[index]) {
                    is DrawCommand.BackdropBlur -> {
                        val left = primitive.placedLeft()
                        val top = primitive.placedTop()
                        val right = primitive.placedRight()
                        val bottom = primitive.placedBottom()
                        if (right > left && bottom > top && primitive.alpha > 0f) {
                            val work = grouping.place(left, top, right, bottom, primitive.blurRadius)
                            result[index] = work
                            grouping.painted(left, top, right, bottom)
                        }
                    }
                    else -> grouping.painted(primitive)
                }
            }
            return result
        }
    }
}

/**
 * The per-panel glass settings every vertex carries.
 *
 * @property coverage The panel's alpha.
 * @property saturation The saturation applied to the backdrop.
 * @property noise The grain strength.
 * @property work The blur the panel samples; it leads it when it is the first to use it.
 * @property leads Whether this panel runs [work]'s passes.
 */
internal class BackdropGlass(
    val coverage: Float,
    val saturation: Float,
    val noise: Float,
    val work: BackdropBlurWork,
    val leads: Boolean,
) {
    /** The scale from full-resolution pixels to the output level's texels. */
    val levelScale: Float get() = 1f / BlurChain.levelScale(work.blur.outputLevel)
}

/** A signed-distance half size no shape reaches, so a tessellated outline's own coverage decides its edge. */
private const val UNBOUNDED_HALF_SIZE = 1e6f

/**
 * Writes the glass for a rounded rectangle: four composite vertices whose signed distance draws the edge.
 */
@Suppress("LongParameterList")
internal fun backdropQuadVertices(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    smoothing: Float,
    tint: Color,
    glass: BackdropGlass,
): Pair<FloatArray, ShortArray> {
    val stride = 16
    val vertices = FloatArray(4 * stride)
    val halfW = (right - left) / 2f
    val halfH = (bottom - top) / 2f
    val clamped = radius.coerceIn(0f, min(halfW, halfH))
    writeBackdropVertex(vertices, 0, left, top, -halfW, -halfH, halfW, halfH, clamped, smoothing, tint, glass, glass.coverage)
    writeBackdropVertex(vertices, stride, right, top, halfW, -halfH, halfW, halfH, clamped, smoothing, tint, glass, glass.coverage)
    writeBackdropVertex(vertices, 2 * stride, right, bottom, halfW, halfH, halfW, halfH, clamped, smoothing, tint, glass, glass.coverage)
    writeBackdropVertex(vertices, 3 * stride, left, bottom, -halfW, halfH, halfW, halfH, clamped, smoothing, tint, glass, glass.coverage)
    return vertices to shortArrayOf(0, 1, 2, 2, 3, 0)
}

/** Writes the glass for a tessellated outline, whose fringe alpha is the edge's coverage. */
internal fun backdropMeshVertices(
    mesh: ColoredTriangleMesh,
    tint: Color,
    glass: BackdropGlass,
): Pair<FloatArray, ShortArray> {
    val stride = 16
    val vertices = FloatArray(mesh.vertices.size * stride)
    for (index in mesh.vertices.indices) {
        val vertex = mesh.vertices[index]
        writeBackdropVertex(
            vertices,
            index * stride,
            vertex.position.x,
            vertex.position.y,
            0f,
            0f,
            UNBOUNDED_HALF_SIZE,
            UNBOUNDED_HALF_SIZE,
            0f,
            0f,
            tint,
            glass,
            vertex.color.a * glass.coverage,
        )
    }
    val indices = ShortArray(mesh.indices.size) { i -> mesh.indices[i].toShort() }
    return vertices to indices
}

@Suppress("LongParameterList")
private fun writeBackdropVertex(
    out: FloatArray,
    offset: Int,
    x: Float,
    y: Float,
    localX: Float,
    localY: Float,
    halfW: Float,
    halfH: Float,
    radius: Float,
    smoothing: Float,
    tint: Color,
    glass: BackdropGlass,
    coverage: Float,
) {
    var cursor = offset
    out[cursor++] = x
    out[cursor++] = y
    out[cursor++] = localX
    out[cursor++] = localY
    out[cursor++] = halfW
    out[cursor++] = halfH
    out[cursor++] = radius
    out[cursor++] = smoothing
    out[cursor++] = tint.r
    out[cursor++] = tint.g
    out[cursor++] = tint.b
    out[cursor++] = tint.a
    out[cursor++] = coverage
    out[cursor++] = glass.saturation
    out[cursor++] = abs(glass.noise)
    out[cursor] = glass.levelScale
}

/** The four shape edges a [DrawCommand.BackdropBlur] covers once its transform is applied. */
internal fun DrawCommand.BackdropBlur.placedLeft(): Float = scaledX(x, transform)

internal fun DrawCommand.BackdropBlur.placedTop(): Float = scaledY(y, transform)

internal fun DrawCommand.BackdropBlur.placedRight(): Float = scaledX(x + w, transform)

internal fun DrawCommand.BackdropBlur.placedBottom(): Float = scaledY(y + h, transform)

private fun scaledX(x: Float, transform: DrawTransform?): Float =
    if (transform == null) x else transform.pivotX + (x - transform.pivotX) * transform.scaleX

private fun scaledY(y: Float, transform: DrawTransform?): Float =
    if (transform == null) y else transform.pivotY + (y - transform.pivotY) * transform.scaleY
