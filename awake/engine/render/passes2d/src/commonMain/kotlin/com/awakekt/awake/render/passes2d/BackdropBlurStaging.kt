/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.BackdropBlurQuality
import com.awakekt.awake.core.graphics2d.BackdropRegion
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawTransform
import com.awakekt.awake.core.graphics2d.DualKawaseBlur
import com.awakekt.awake.core.graphics2d.bounds
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What the frame's backdrop blurs read and how hard they may work: the [quality], and the size of
 * the target the UI is drawn into, which is the backdrop.
 *
 * @property quality The renderer's current [BackdropBlurQuality]; [BackdropBlurQuality.Off] fills
 * every panel with its fallback.
 * @property width The target's width in pixels.
 * @property height The target's height in pixels.
 */
data class BackdropBlurTarget(val quality: BackdropBlurQuality, val width: Int, val height: Int)

/**
 * One blur in a frame: the [region] of the backdrop it reads and the [passes] that blur it, run
 * where the first panel sharing it is drawn. Every panel that shares it samples the same result.
 *
 * @property blur The passes' shape: halvings, tap offset and output level.
 * @property width The backdrop's width in pixels, which sizes every level.
 * @property height The backdrop's height in pixels.
 */
class BackdropBlurWork internal constructor(
    val blur: DualKawaseBlur,
    val width: Int,
    val height: Int,
    region: BackdropRegion,
) {
    /** The full-resolution pixels the blur reads: the union of its panels' regions. */
    var region: BackdropRegion = region
        private set

    /** The passes in recording order: every downsample, then every upsample. */
    var passes: List<BackdropBlurPass> = emptyList()
        private set

    internal var led = false

    internal fun include(other: BackdropRegion) {
        region = BackdropRegion(
            min(region.left, other.left),
            min(region.top, other.top),
            max(region.right, other.right),
            max(region.bottom, other.bottom),
        )
    }

    internal fun finish() {
        val result = ArrayList<BackdropBlurPass>(blur.passCount)
        for (level in 1..blur.iterations) {
            result += BackdropBlurPass.of(level - 1, level, region.atLevel(level, width, height), region.atLevel(level - 1, width, height), blur.offset)
        }
        for (level in blur.iterations - 1 downTo blur.outputLevel) {
            result += BackdropBlurPass.of(level + 1, level, region.atLevel(level, width, height), region.atLevel(level + 1, width, height), blur.offset)
        }
        passes = result
    }
}

/**
 * One down- or upsample of a [BackdropBlurWork]: draw [vertices], a full-screen triangle, into
 * level [targetLevel] scissored to [target], reading level [sourceLevel]. Level 0 is the target the
 * UI is being drawn into.
 *
 * @property sourceLevel The level read.
 * @property targetLevel The level written.
 * @property target The texels written, in [targetLevel]'s own texels.
 * @property vertices Three [VertexFormats2D.BackdropBlurPass] vertices.
 * @property indices The triangle's indices.
 */
class BackdropBlurPass internal constructor(
    val sourceLevel: Int,
    val targetLevel: Int,
    val target: BackdropRegion,
    val vertices: FloatArray,
    val indices: IntArray,
) {
    /** True when this pass reads a coarser level than it writes. */
    val isUpsample: Boolean get() = sourceLevel > targetLevel

    internal companion object {
        private val CORNERS = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        private val TRIANGLE = intArrayOf(0, 1, 2)

        fun of(sourceLevel: Int, targetLevel: Int, target: BackdropRegion, source: BackdropRegion, offset: Float): BackdropBlurPass {
            val stride = VertexFormats2D.BackdropBlurPass.strideFloats
            val vertices = FloatArray(3 * stride)
            for (corner in 0 until 3) {
                var cursor = corner * stride
                vertices[cursor++] = CORNERS[corner * 2]
                vertices[cursor++] = CORNERS[corner * 2 + 1]
                vertices[cursor++] = source.left.toFloat()
                vertices[cursor++] = source.top.toFloat()
                vertices[cursor++] = source.right.toFloat()
                vertices[cursor++] = source.bottom.toFloat()
                vertices[cursor++] = offset
                vertices[cursor++] = if (sourceLevel > targetLevel) 1f else 0f
                vertices[cursor++] = 0f
                vertices[cursor] = 0f
            }
            return BackdropBlurPass(sourceLevel, targetLevel, target, vertices, TRIANGLE)
        }
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
internal class BackdropGrouping(private val target: BackdropBlurTarget) {
    val works = ArrayList<BackdropBlurWork>()
    private var open: BackdropBlurWork? = null
    private var dirtyLeft = Float.POSITIVE_INFINITY
    private var dirtyTop = Float.POSITIVE_INFINITY
    private var dirtyRight = Float.NEGATIVE_INFINITY
    private var dirtyBottom = Float.NEGATIVE_INFINITY

    /** Whether a later panel could still share a blur, which is when painting has to be watched. */
    val watching: Boolean get() = open != null

    /**
     * The blur a panel with these bounds and [radius] samples, or null when it falls back.
     */
    fun place(left: Float, top: Float, right: Float, bottom: Float, radius: Float): BackdropBlurWork? {
        if (target.quality == BackdropBlurQuality.Off || target.width <= 0 || target.height <= 0) return null
        val blur = DualKawaseBlur.forRadius(radius, target.quality)
        val region = BackdropRegion.around(left, top, right - left, bottom - top, blur.extent, target.width, target.height)
        if (region.isEmpty) return null
        val current = open
        if (current != null && current.blur == blur && !dirtyTouches(region)) {
            current.include(region)
            return current
        }
        if (works.size >= target.quality.maxBlursPerFrame) return null
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
        if (open != null) paintBounds(primitive, this)
    }

    fun finish() {
        for (index in works.indices) works[index].finish()
    }

    private fun dirtyTouches(region: BackdropRegion): Boolean =
        dirtyRight > region.left && dirtyLeft < region.right && dirtyBottom > region.top && dirtyTop < region.bottom
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

/** Feeds [grouping] the pixels [primitive] can paint, generously: an overestimate only costs a shared blur. */
@Suppress("CyclomaticComplexMethod")
private fun paintBounds(primitive: DrawCommand, grouping: BackdropGrouping) {
    when (primitive) {
        is DrawCommand.Quad -> rect(primitive.x, primitive.y, primitive.w, primitive.h, primitive.transform, grouping)
        is DrawCommand.RoundedQuad -> rect(primitive.x, primitive.y, primitive.w, primitive.h, primitive.transform, grouping)
        is DrawCommand.Glyph -> rect(primitive.x, primitive.y, primitive.w, primitive.h, primitive.transform, grouping)
        is DrawCommand.GradientQuad -> grouping.painted(primitive.x, primitive.y, primitive.x + primitive.w, primitive.y + primitive.h)
        is DrawCommand.Texture -> {
            if (primitive.rotationDegrees == 0f) {
                rect(primitive.x, primitive.y, primitive.w, primitive.h, primitive.transform, grouping)
            } else {
                // Any turn stays inside the circle through the corners.
                val half = sqrt(primitive.w * primitive.w + primitive.h * primitive.h) / 2f
                val cx = primitive.x + primitive.w / 2f
                val cy = primitive.y + primitive.h / 2f
                rect(cx - half, cy - half, 2f * half, 2f * half, primitive.transform, grouping)
            }
        }
        is DrawCommand.ShadowQuad -> {
            val grow = primitive.spread + primitive.blurRadius + 1f
            grouping.painted(
                primitive.x + primitive.offsetX - grow,
                primitive.y + primitive.offsetY - grow,
                primitive.x + primitive.offsetX + primitive.w + grow,
                primitive.y + primitive.offsetY + primitive.h + grow,
            )
        }
        is DrawCommand.FilledPath -> {
            val bounds = primitive.path.bounds()
            grouping.painted(bounds.x - 1f, bounds.y - 1f, bounds.x + bounds.width + 1f, bounds.y + bounds.height + 1f)
        }
        is DrawCommand.StrokedPath -> {
            val bounds = primitive.path.bounds()
            // A miter can reach well past half the width; twice the width covers any sane limit.
            val grow = primitive.stroke.width.value * 2f + 1f
            grouping.painted(bounds.x - grow, bounds.y - grow, bounds.x + bounds.width + grow, bounds.y + bounds.height + grow)
        }
        is DrawCommand.Mesh -> meshBounds(primitive, grouping)
        is DrawCommand.BackdropBlur -> grouping.painted(primitive.placedLeft(), primitive.placedTop(), primitive.placedRight(), primitive.placedBottom())
        is DrawCommand.ClipPathPush, is DrawCommand.ClipPush, is DrawCommand.ClipPop -> Unit
    }
}

private fun rect(x: Float, y: Float, w: Float, h: Float, transform: DrawTransform?, grouping: BackdropGrouping) {
    val left = scaledX(x, transform)
    val right = scaledX(x + w, transform)
    val top = scaledY(y, transform)
    val bottom = scaledY(y + h, transform)
    grouping.painted(min(left, right), min(top, bottom), max(left, right), max(top, bottom))
}

private fun meshBounds(primitive: DrawCommand.Mesh, grouping: BackdropGrouping) {
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
    grouping.painted(left - 1f, top - 1f, right + 1f, bottom + 1f)
}

/** A signed-distance half size no shape reaches, so a tessellated outline's own coverage decides its edge. */
private const val UNBOUNDED_HALF_SIZE = 1e6f

/**
 * Writes the glass for a rounded rectangle: four [VertexFormats2D.BackdropComposite] vertices whose
 * signed distance draws the edge.
 */
@Suppress("LongParameterList")
internal fun backdropQuadRun(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    smoothing: Float,
    tint: Color,
    glass: BackdropGlass,
): StagedDrawRun.BackdropRun {
    val stride = VertexFormats2D.BackdropComposite.strideFloats
    val vertices = FloatArray(VertexFormats2D.VERTICES_PER_QUAD * stride)
    val halfW = (right - left) / 2f
    val halfH = (bottom - top) / 2f
    val clamped = radius.coerceIn(0f, min(halfW, halfH))
    writeBackdropVertex(vertices, 0, left, top, -halfW, -halfH, halfW, halfH, clamped, smoothing, tint, glass, glass.coverage)
    writeBackdropVertex(vertices, stride, right, top, halfW, -halfH, halfW, halfH, clamped, smoothing, tint, glass, glass.coverage)
    writeBackdropVertex(vertices, 2 * stride, right, bottom, halfW, halfH, halfW, halfH, clamped, smoothing, tint, glass, glass.coverage)
    writeBackdropVertex(vertices, 3 * stride, left, bottom, -halfW, halfH, halfW, halfH, clamped, smoothing, tint, glass, glass.coverage)
    return StagedDrawRun.BackdropRun(vertices, intArrayOf(0, 1, 2, 2, 3, 0), glass.ledWork)
}

/** Writes the glass for a tessellated outline, whose fringe alpha is the edge's coverage. */
internal fun backdropMeshRun(mesh: ColoredTriangleMesh, tint: Color, glass: BackdropGlass): StagedDrawRun.BackdropRun {
    val stride = VertexFormats2D.BackdropComposite.strideFloats
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
    return StagedDrawRun.BackdropRun(vertices, mesh.indices, glass.ledWork)
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
    val levelScale: Float get() = 1f / DualKawaseBlur.levelScale(work.blur.outputLevel)

    /** [work] when this panel runs its passes, otherwise null. */
    val ledWork: BackdropBlurWork? get() = if (leads) work else null
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
