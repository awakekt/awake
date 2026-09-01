/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes2d

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.VertexFormats2D
import io.github.awakelab.awake.core.graphics2d.ColoredTriangleMesh
import io.github.awakelab.awake.core.graphics2d.ColoredVertex
import io.github.awakelab.awake.core.graphics2d.DrawCommand
import io.github.awakelab.awake.core.graphics2d.DrawPath
import io.github.awakelab.awake.core.graphics2d.DrawPoint
import io.github.awakelab.awake.core.graphics2d.DrawShape
import io.github.awakelab.awake.core.graphics2d.DrawTransform
import io.github.awakelab.awake.core.graphics2d.TexturedTriangleMesh
import io.github.awakelab.awake.core.graphics2d.TexturedVertex
import io.github.awakelab.awake.core.graphics2d.TriangleMesh
import io.github.awakelab.awake.core.graphics2d.bounds
import io.github.awakelab.awake.core.graphics2d.clipToConvexPaths
import io.github.awakelab.awake.core.graphics2d.convexClipContour
import io.github.awakelab.awake.core.graphics2d.strokeToFillPath
import io.github.awakelab.awake.core.graphics2d.tessellateFillAa
import io.github.awakelab.awake.core.graphics2d.toPath
import io.github.awakelab.awake.core.graphics2d.writeGlyphVertex
import io.github.awakelab.awake.core.graphics2d.writeRoundedQuadVertex
import io.github.awakelab.awake.core.graphics2d.writeVertex
import io.github.awakelab.awake.core.math2d.Dp
import io.github.awakelab.awake.core.math2d.Rectangle
import io.github.awakelab.awake.core.math2d.contains
import io.github.awakelab.awake.core.math2d.intersect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Splits a raw [DrawCommand] list into typed [StagedDrawRun]s -- maximal *contiguous* spans of
 * primitives sharing one pipeline, in original emission order.
 */
object DrawRunCoalescer {

    private enum class ClipKind {
        Rect,
        Path,
    }

    private val UNBOUNDED_SAFE_INTERIOR_RECT = Rectangle(-1e9f, -1e9f, 2e9f, 2e9f)
    private val WHITE_COLOR = Color(1f, 1f, 1f, 1f)

    /** Everything a rounded rectangle's local (0,0-origin) tessellation depends on -- radius is
     * clamped against width/height by `toPath`, so raw (unclamped) values are still a valid key:
     * two quads with the same radius/w/h clamp to the same shape regardless of position. */
    private data class RoundedRectShapeKey(val radius: Float, val width: Float, val height: Float)

    /** Corner-arc trig and AA-fringe geometry (`offsetPolygon`'s hypot/miter math) for a rounded
     * rectangle only depend on radius and size, not position -- see `DrawShape.RoundedRectangle`.
     * Baked at the origin with [WHITE_COLOR] so [placedAt] can recolor without re-tessellating.
     * Eldest-evicted past [ROUNDED_RECT_CACHE_CAPACITY]; `linkedMapOf` iteration order is
     * insertion order, and re-inserting on hit makes it least-recently-used (see
     * `SceneAssetLibrary.retainedMeshes` for the same pattern). */
    private val roundedRectMeshCache = linkedMapOf<RoundedRectShapeKey, ColoredTriangleMesh>()
    private const val ROUNDED_RECT_CACHE_CAPACITY = 256

    private fun localRoundedRectMesh(radius: Float, width: Float, height: Float): ColoredTriangleMesh {
        val key = RoundedRectShapeKey(radius, width, height)
        roundedRectMeshCache.remove(key)?.let { cached ->
            roundedRectMeshCache[key] = cached
            return cached
        }
        val mesh = DrawShape.RoundedRectangle(Dp(radius))
            .toPath(Rectangle(0f, 0f, width, height))
            .tessellateFillAa(WHITE_COLOR)
        roundedRectMeshCache[key] = mesh
        if (roundedRectMeshCache.size > ROUNDED_RECT_CACHE_CAPACITY) {
            roundedRectMeshCache.remove(roundedRectMeshCache.keys.first())
        }
        return mesh
    }

    /** Translates a [localRoundedRectMesh] into place and bakes [color] in, reproducing exactly
     * what `tessellateFillAa(color)` would have produced: fill vertices get [color], and the AA
     * fringe's outer ring -- baked transparent (alpha 0) at [WHITE_COLOR] -- gets [color] with
     * alpha forced back to 0, since `tessellateFillAa` only ever zeroes alpha, never touches RGB
     * on the transparent ring. */
    private fun ColoredTriangleMesh.placedAt(dx: Float, dy: Float, color: Color): ColoredTriangleMesh =
        ColoredTriangleMesh(
            vertices = vertices.map { v ->
                val placedColor = if (v.color.a == 0f) color.withAlpha(0f) else color
                ColoredVertex(DrawPoint(v.position.x + dx, v.position.y + dy), placedColor)
            },
            indices = indices,
        )

    /**
     * Walks [primitives] in paint order and coalesces them into [StagedDrawRun] instances.
     *
     * @param primitives The raw 2D draw commands emitted by the UI framework.
     * @param maxQuadsPerRun Mesh capacity, in quads. A span longer than this splits into
     * several runs -- still contiguous and still in order, just backed by more than one mesh.
     */
    fun coalesce(
        primitives: List<DrawCommand>,
        maxQuadsPerRun: Int = 1024,
    ): List<StagedDrawRun> {
        val runs = mutableListOf<StagedDrawRun>()
        val activePathClips = ArrayList<DrawPath>()
        val clipKindStack = ArrayDeque<ClipKind>()
        val safeInteriorRectStack = ArrayDeque<Rectangle?>()

        var index = 0
        while (index < primitives.size) {
            val runStart = index
            val first = primitives[runStart]
            index += 1
            while (index < primitives.size && primitives[index]::class == first::class) index += 1
            val slice = primitives.subList(runStart, index)
            val safeInteriorRect = safeInteriorRectStack.lastOrNull()

            when (first) {
                is DrawCommand.Quad -> {
                    @Suppress("UNCHECKED_CAST")
                    val quadSlice = slice as List<DrawCommand.Quad>
                    if (canExactClip(activePathClips)) {
                        val tessellated = quadSlice.map { quad ->
                            val raw = TriangleMesh(
                                points = listOf(
                                    DrawPoint(quad.x, quad.y),
                                    DrawPoint(quad.x + quad.w, quad.y),
                                    DrawPoint(quad.x + quad.w, quad.y + quad.h),
                                    DrawPoint(quad.x, quad.y + quad.h),
                                ),
                                indices = intArrayOf(0, 1, 2, 2, 3, 0),
                            )
                            val clipped = if (canSkipExactClip(safeInteriorRect, quad.x, quad.y, quad.w, quad.h)) {
                                raw
                            } else {
                                exactClip(raw, activePathClips)
                            }
                            clipped to quad.color
                        }
                        chunkColoredTriangleMeshes(runs, tessellated, maxQuadsPerRun)
                    } else {
                        var chunkStart = 0
                        while (chunkStart < quadSlice.size) {
                            val chunkEnd = minOf(chunkStart + maxQuadsPerRun, quadSlice.size)
                            runs += buildQuadRun(quadSlice.subList(chunkStart, chunkEnd))
                            chunkStart = chunkEnd
                        }
                    }
                }
                is DrawCommand.GradientQuad -> {
                    @Suppress("UNCHECKED_CAST")
                    val gradientSlice = slice as List<DrawCommand.GradientQuad>
                    if (canExactClip(activePathClips)) {
                        val tessellated = gradientSlice.map { quad ->
                            val raw = ColoredTriangleMesh(
                                vertices = listOf(
                                    ColoredVertex(DrawPoint(quad.x, quad.y), quad.gradient.topLeft),
                                    ColoredVertex(DrawPoint(quad.x + quad.w, quad.y), quad.gradient.topRight),
                                    ColoredVertex(DrawPoint(quad.x + quad.w, quad.y + quad.h), quad.gradient.bottomRight),
                                    ColoredVertex(DrawPoint(quad.x, quad.y + quad.h), quad.gradient.bottomLeft),
                                ),
                                indices = intArrayOf(0, 1, 2, 2, 3, 0),
                            )
                            if (canSkipExactClip(safeInteriorRect, quad.x, quad.y, quad.w, quad.h)) {
                                raw
                            } else {
                                exactClipColored(raw, activePathClips)
                            }
                        }
                        chunkColoredVertexTriangleMeshes(runs, tessellated, maxQuadsPerRun)
                    } else {
                        var chunkStart = 0
                        while (chunkStart < gradientSlice.size) {
                            val chunkEnd = minOf(chunkStart + maxQuadsPerRun, gradientSlice.size)
                            runs += buildGradientQuadRun(gradientSlice.subList(chunkStart, chunkEnd))
                            chunkStart = chunkEnd
                        }
                    }
                }
                is DrawCommand.RoundedQuad -> {
                    @Suppress("UNCHECKED_CAST")
                    val roundedSlice = slice as List<DrawCommand.RoundedQuad>
                    if (canExactClip(activePathClips)) {
                        val tessellated = roundedSlice.map { quad ->
                            val triangleMesh = localRoundedRectMesh(quad.radius, quad.w, quad.h)
                                .placedAt(quad.x, quad.y, quad.color)
                            if (canSkipExactClip(safeInteriorRect, quad.x, quad.y, quad.w, quad.h)) {
                                triangleMesh
                            } else {
                                exactClipColored(triangleMesh, activePathClips)
                            }
                        }
                        chunkColoredVertexTriangleMeshes(runs, tessellated, maxQuadsPerRun)
                    } else {
                        var chunkStart = 0
                        while (chunkStart < roundedSlice.size) {
                            val chunkEnd = minOf(chunkStart + maxQuadsPerRun, roundedSlice.size)
                            runs += buildRoundedQuadRun(roundedSlice.subList(chunkStart, chunkEnd))
                            chunkStart = chunkEnd
                        }
                    }
                }
                is DrawCommand.FilledPath -> {
                    @Suppress("UNCHECKED_CAST")
                    val pathSlice = slice as List<DrawCommand.FilledPath>
                    val tessellated = pathSlice.map { primitive ->
                        val bounds = primitive.path.bounds()
                        val triangleMesh = primitive.path.tessellateFillAa(primitive.color)
                        if (canSkipExactClip(safeInteriorRect, bounds.x, bounds.y, bounds.width, bounds.height)) {
                            triangleMesh
                        } else {
                            exactClipColored(triangleMesh, activePathClips)
                        }
                    }
                    chunkColoredVertexTriangleMeshes(runs, tessellated, maxQuadsPerRun)
                }
                is DrawCommand.StrokedPath -> {
                    @Suppress("UNCHECKED_CAST")
                    val strokedSlice = slice as List<DrawCommand.StrokedPath>
                    val tessellated = strokedSlice.map { primitive ->
                        val outlinedPath = primitive.path.strokeToFillPath(primitive.stroke)
                        val bounds = outlinedPath.bounds()
                        val triangleMesh = StrokedPathMeshCache.mesh(primitive.path, primitive.stroke, primitive.color)
                        if (canSkipExactClip(safeInteriorRect, bounds.x, bounds.y, bounds.width, bounds.height)) {
                            triangleMesh
                        } else {
                            exactClipColored(triangleMesh, activePathClips)
                        }
                    }
                    chunkColoredVertexTriangleMeshes(runs, tessellated, maxQuadsPerRun)
                }
                is DrawCommand.Mesh -> {
                    @Suppress("UNCHECKED_CAST")
                    val meshSlice = slice as List<DrawCommand.Mesh>
                    if (canExactClip(activePathClips)) {
                        // Clipping needs real coordinates, so this path materialises. Rare: it
                        // takes a path-shaped clip to be open, and a rect clip is a scissor.
                        val clipped = meshSlice.map { exactClipColored(it.placedMesh(), activePathClips) }
                        chunkColoredVertexTriangleMeshes(runs, clipped, maxQuadsPerRun)
                    } else {
                        // Placement folded into the copy the staging pass already makes: a
                        // multiply-add per vertex, and the caller's cached triangles stay untouched.
                        chunkPlacedMeshes(runs, meshSlice, maxQuadsPerRun)
                    }
                }
                is DrawCommand.Glyph -> {
                    @Suppress("UNCHECKED_CAST")
                    val glyphSlice = slice as List<DrawCommand.Glyph>
                    if (canExactClip(activePathClips)) {
                        val clipped = glyphSlice.map { glyph ->
                            val raw = texturedQuadMesh(glyph.x, glyph.y, glyph.w, glyph.h, glyph.u0, glyph.v0, glyph.u1, glyph.v1)
                            val mesh = if (canSkipExactClip(safeInteriorRect, glyph.x, glyph.y, glyph.w, glyph.h)) {
                                raw
                            } else {
                                exactClip(raw, activePathClips)
                            }
                            mesh to glyph.color
                        }
                        chunkTexturedTriangleMeshes(runs, clipped, maxQuadsPerRun)
                    } else {
                        var chunkStart = 0
                        while (chunkStart < glyphSlice.size) {
                            val chunkEnd = minOf(chunkStart + maxQuadsPerRun, glyphSlice.size)
                            runs += buildGlyphRun(glyphSlice.subList(chunkStart, chunkEnd))
                            chunkStart = chunkEnd
                        }
                    }
                }
                is DrawCommand.Texture -> {
                    @Suppress("UNCHECKED_CAST")
                    val textureSlice = slice as List<DrawCommand.Texture>
                    runs += buildTextureRun(textureSlice, activePathClips, safeInteriorRect)
                }
                is DrawCommand.ClipPathPush -> {
                    @Suppress("UNCHECKED_CAST")
                    (slice as List<DrawCommand.ClipPathPush>).forEach {
                        clipKindStack.addLast(ClipKind.Path)
                        activePathClips += it.path
                        val parentSafeInteriorRect = safeInteriorRectStack.lastOrNull() ?: UNBOUNDED_SAFE_INTERIOR_RECT
                        safeInteriorRectStack.addLast(
                            it.safeInteriorRect?.let { own -> parentSafeInteriorRect.intersect(own) },
                        )
                        runs += StagedDrawRun.ClipRun(it.boundsRect)
                    }
                }
                is DrawCommand.ClipPush -> {
                    @Suppress("UNCHECKED_CAST")
                    (slice as List<DrawCommand.ClipPush>).forEach {
                        clipKindStack.addLast(ClipKind.Rect)
                        runs += StagedDrawRun.ClipRun(it.rect)
                    }
                }
                is DrawCommand.ClipPop -> {
                    @Suppress("UNCHECKED_CAST")
                    (slice as List<DrawCommand.ClipPop>).forEach {
                        when (clipKindStack.removeLastOrNull()) {
                            ClipKind.Path -> {
                                if (activePathClips.isNotEmpty()) activePathClips.removeAt(activePathClips.lastIndex)
                                safeInteriorRectStack.removeLastOrNull()
                            }
                            ClipKind.Rect, null -> Unit
                        }
                        runs += StagedDrawRun.ClipRun(it.restoreRect)
                    }
                }
                is DrawCommand.ShadowQuad -> {
                    @Suppress("UNCHECKED_CAST")
                    val shadowSlice = slice as List<DrawCommand.ShadowQuad>
                    var chunkStart = 0
                    while (chunkStart < shadowSlice.size) {
                        val chunkEnd = minOf(chunkStart + maxQuadsPerRun, shadowSlice.size)
                        runs += buildShadowQuadRun(shadowSlice.subList(chunkStart, chunkEnd))
                        chunkStart = chunkEnd
                    }
                }
            }
        }
        return runs
    }

    fun canSkipExactClip(safeInteriorRect: Rectangle?, x: Float, y: Float, w: Float, h: Float): Boolean =
        safeInteriorRect != null && safeInteriorRect.contains(Rectangle(x, y, w, h))

    fun canExactClip(paths: List<DrawPath>): Boolean = paths.isNotEmpty() && paths.all { it.convexClipContour() != null }

    fun exactClip(mesh: TriangleMesh, activePathClips: List<DrawPath>): TriangleMesh =
        if (canExactClip(activePathClips)) mesh.clipToConvexPaths(activePathClips) else mesh

    fun exactClip(mesh: TexturedTriangleMesh, activePathClips: List<DrawPath>): TexturedTriangleMesh =
        if (canExactClip(activePathClips)) mesh.clipToConvexPaths(activePathClips) else mesh

    fun exactClipColored(mesh: ColoredTriangleMesh, activePathClips: List<DrawPath>): ColoredTriangleMesh =
        if (canExactClip(activePathClips)) mesh.clipToConvexPaths(activePathClips) else mesh

    fun buildQuadRun(quads: List<DrawCommand.Quad>): StagedDrawRun.QuadRun {
        val vertices = FloatArray(quads.size * VertexFormats2D.VERTICES_PER_QUAD * VertexFormats2D.FLOATS_PER_VERTEX)
        val indices = IntArray(quads.size * VertexFormats2D.INDICES_PER_QUAD)
        var quadIndex = 0
        while (quadIndex < quads.size) {
            val quad = quads[quadIndex]
            val vertexBase = quadIndex * VertexFormats2D.VERTICES_PER_QUAD * VertexFormats2D.FLOATS_PER_VERTEX
            writeVertex(vertices, vertexBase + 0 * VertexFormats2D.FLOATS_PER_VERTEX, quad.x, quad.y, quad.color, quad.transform)
            writeVertex(vertices, vertexBase + 1 * VertexFormats2D.FLOATS_PER_VERTEX, quad.x + quad.w, quad.y, quad.color, quad.transform)
            writeVertex(vertices, vertexBase + 2 * VertexFormats2D.FLOATS_PER_VERTEX, quad.x + quad.w, quad.y + quad.h, quad.color, quad.transform)
            writeVertex(vertices, vertexBase + 3 * VertexFormats2D.FLOATS_PER_VERTEX, quad.x, quad.y + quad.h, quad.color, quad.transform)

            val vertexOffset = quadIndex * VertexFormats2D.VERTICES_PER_QUAD
            val indexBase = quadIndex * VertexFormats2D.INDICES_PER_QUAD
            indices[indexBase] = vertexOffset
            indices[indexBase + 1] = vertexOffset + 1
            indices[indexBase + 2] = vertexOffset + 2
            indices[indexBase + 3] = vertexOffset + 2
            indices[indexBase + 4] = vertexOffset + 3
            indices[indexBase + 5] = vertexOffset
            quadIndex += 1
        }
        return StagedDrawRun.QuadRun(vertices, indices)
    }

    fun buildGradientQuadRun(quads: List<DrawCommand.GradientQuad>): StagedDrawRun.QuadRun {
        val vertices = FloatArray(quads.size * VertexFormats2D.VERTICES_PER_QUAD * VertexFormats2D.FLOATS_PER_VERTEX)
        val indices = IntArray(quads.size * VertexFormats2D.INDICES_PER_QUAD)
        var quadIndex = 0
        while (quadIndex < quads.size) {
            val quad = quads[quadIndex]
            val vertexBase = quadIndex * VertexFormats2D.VERTICES_PER_QUAD * VertexFormats2D.FLOATS_PER_VERTEX
            writeVertex(vertices, vertexBase + 0 * VertexFormats2D.FLOATS_PER_VERTEX, quad.x, quad.y, quad.gradient.topLeft)
            writeVertex(vertices, vertexBase + 1 * VertexFormats2D.FLOATS_PER_VERTEX, quad.x + quad.w, quad.y, quad.gradient.topRight)
            writeVertex(vertices, vertexBase + 2 * VertexFormats2D.FLOATS_PER_VERTEX, quad.x + quad.w, quad.y + quad.h, quad.gradient.bottomRight)
            writeVertex(vertices, vertexBase + 3 * VertexFormats2D.FLOATS_PER_VERTEX, quad.x, quad.y + quad.h, quad.gradient.bottomLeft)

            val vertexOffset = quadIndex * VertexFormats2D.VERTICES_PER_QUAD
            val indexBase = quadIndex * VertexFormats2D.INDICES_PER_QUAD
            indices[indexBase] = vertexOffset
            indices[indexBase + 1] = vertexOffset + 1
            indices[indexBase + 2] = vertexOffset + 2
            indices[indexBase + 3] = vertexOffset + 2
            indices[indexBase + 4] = vertexOffset + 3
            indices[indexBase + 5] = vertexOffset
            quadIndex += 1
        }
        return StagedDrawRun.QuadRun(vertices, indices)
    }

    fun buildRoundedQuadRun(quads: List<DrawCommand.RoundedQuad>): StagedDrawRun.RoundedQuadRun {
        val floatsPerVertex = VertexFormats2D.ROUNDED_QUAD_FLOATS_PER_VERTEX
        val vertices = FloatArray(quads.size * VertexFormats2D.VERTICES_PER_QUAD * floatsPerVertex)
        val indices = IntArray(quads.size * VertexFormats2D.INDICES_PER_QUAD)
        var quadIndex = 0
        while (quadIndex < quads.size) {
            val quad = quads[quadIndex]
            val halfW = quad.w / 2f
            val halfH = quad.h / 2f
            val radius = quad.radius.coerceAtMost(minOf(halfW, halfH))
            val vertexBase = quadIndex * VertexFormats2D.VERTICES_PER_QUAD * floatsPerVertex
            writeRoundedQuadVertex(vertices, vertexBase + 0 * floatsPerVertex, quad.x, quad.y, -halfW, -halfH, halfW, halfH, radius, quad.smoothing, quad.color, quad.transform)
            writeRoundedQuadVertex(vertices, vertexBase + 1 * floatsPerVertex, quad.x + quad.w, quad.y, halfW, -halfH, halfW, halfH, radius, quad.smoothing, quad.color, quad.transform)
            writeRoundedQuadVertex(vertices, vertexBase + 2 * floatsPerVertex, quad.x + quad.w, quad.y + quad.h, halfW, halfH, halfW, halfH, radius, quad.smoothing, quad.color, quad.transform)
            writeRoundedQuadVertex(vertices, vertexBase + 3 * floatsPerVertex, quad.x, quad.y + quad.h, -halfW, halfH, halfW, halfH, radius, quad.smoothing, quad.color, quad.transform)

            val vertexOffset = quadIndex * VertexFormats2D.VERTICES_PER_QUAD
            val indexBase = quadIndex * VertexFormats2D.INDICES_PER_QUAD
            indices[indexBase] = vertexOffset
            indices[indexBase + 1] = vertexOffset + 1
            indices[indexBase + 2] = vertexOffset + 2
            indices[indexBase + 3] = vertexOffset + 2
            indices[indexBase + 4] = vertexOffset + 3
            indices[indexBase + 5] = vertexOffset
            quadIndex += 1
        }
        return StagedDrawRun.RoundedQuadRun(vertices, indices)
    }

    fun buildShadowQuadRun(shadows: List<DrawCommand.ShadowQuad>): StagedDrawRun.RoundedQuadRun {
        val floatsPerVertex = VertexFormats2D.ROUNDED_QUAD_FLOATS_PER_VERTEX
        val vertices = FloatArray(shadows.size * VertexFormats2D.VERTICES_PER_QUAD * floatsPerVertex)
        val indices = IntArray(shadows.size * VertexFormats2D.INDICES_PER_QUAD)
        var quadIndex = 0
        while (quadIndex < shadows.size) {
            val shadow = shadows[quadIndex]
            val centerX = shadow.x + shadow.offsetX + shadow.w / 2f
            val centerY = shadow.y + shadow.offsetY + shadow.h / 2f
            val halfW = (shadow.w / 2f + shadow.spread).coerceAtLeast(0f)
            val halfH = (shadow.h / 2f + shadow.spread).coerceAtLeast(0f)
            val radius = (shadow.radius + shadow.spread).coerceIn(0f, minOf(halfW, halfH))
            val blur = shadow.blurRadius.coerceAtLeast(1f)
            val pad = blur + 1f
            val quadHalfW = halfW + pad
            val quadHalfH = halfH + pad
            val localW = quadHalfW / blur
            val localH = quadHalfH / blur
            val sdfHalfW = halfW / blur
            val sdfHalfH = halfH / blur
            val sdfRadius = radius / blur
            val left = centerX - quadHalfW
            val top = centerY - quadHalfH
            val right = centerX + quadHalfW
            val bottom = centerY + quadHalfH
            val gradient = shadow.gradient
            val topLeft = gradient?.topLeft ?: shadow.color
            val topRight = gradient?.topRight ?: shadow.color
            val bottomRight = gradient?.bottomRight ?: shadow.color
            val bottomLeft = gradient?.bottomLeft ?: shadow.color
            val vertexBase = quadIndex * VertexFormats2D.VERTICES_PER_QUAD * floatsPerVertex
            writeRoundedQuadVertex(vertices, vertexBase + 0 * floatsPerVertex, left, top, -localW, -localH, sdfHalfW, sdfHalfH, sdfRadius, 0f, topLeft)
            writeRoundedQuadVertex(vertices, vertexBase + 1 * floatsPerVertex, right, top, localW, -localH, sdfHalfW, sdfHalfH, sdfRadius, 0f, topRight)
            writeRoundedQuadVertex(vertices, vertexBase + 2 * floatsPerVertex, right, bottom, localW, localH, sdfHalfW, sdfHalfH, sdfRadius, 0f, bottomRight)
            writeRoundedQuadVertex(vertices, vertexBase + 3 * floatsPerVertex, left, bottom, -localW, localH, sdfHalfW, sdfHalfH, sdfRadius, 0f, bottomLeft)

            val vertexOffset = quadIndex * VertexFormats2D.VERTICES_PER_QUAD
            val indexBase = quadIndex * VertexFormats2D.INDICES_PER_QUAD
            indices[indexBase] = vertexOffset
            indices[indexBase + 1] = vertexOffset + 1
            indices[indexBase + 2] = vertexOffset + 2
            indices[indexBase + 3] = vertexOffset + 2
            indices[indexBase + 4] = vertexOffset + 3
            indices[indexBase + 5] = vertexOffset
            quadIndex += 1
        }
        return StagedDrawRun.RoundedQuadRun(vertices, indices)
    }

    fun buildGlyphRun(glyphs: List<DrawCommand.Glyph>): StagedDrawRun.GlyphRun {
        val glyphVertices = FloatArray(glyphs.size * VertexFormats2D.VERTICES_PER_QUAD * VertexFormats2D.GLYPH_FLOATS_PER_VERTEX)
        val glyphIndices = IntArray(glyphs.size * VertexFormats2D.INDICES_PER_QUAD)
        var glyphIndex = 0
        while (glyphIndex < glyphs.size) {
            val glyph = glyphs[glyphIndex]
            val vertexBase = glyphIndex * VertexFormats2D.VERTICES_PER_QUAD * VertexFormats2D.GLYPH_FLOATS_PER_VERTEX
            writeGlyphVertex(glyphVertices, vertexBase + 0 * VertexFormats2D.GLYPH_FLOATS_PER_VERTEX, glyph.x, glyph.y, glyph.u0, glyph.v0, glyph.color, glyph.transform)
            writeGlyphVertex(glyphVertices, vertexBase + 1 * VertexFormats2D.GLYPH_FLOATS_PER_VERTEX, glyph.x + glyph.w, glyph.y, glyph.u1, glyph.v0, glyph.color, glyph.transform)
            writeGlyphVertex(glyphVertices, vertexBase + 2 * VertexFormats2D.GLYPH_FLOATS_PER_VERTEX, glyph.x + glyph.w, glyph.y + glyph.h, glyph.u1, glyph.v1, glyph.color, glyph.transform)
            writeGlyphVertex(glyphVertices, vertexBase + 3 * VertexFormats2D.GLYPH_FLOATS_PER_VERTEX, glyph.x, glyph.y + glyph.h, glyph.u0, glyph.v1, glyph.color, glyph.transform)

            val vertexOffset = glyphIndex * VertexFormats2D.VERTICES_PER_QUAD
            val indexBase = glyphIndex * VertexFormats2D.INDICES_PER_QUAD
            glyphIndices[indexBase] = vertexOffset
            glyphIndices[indexBase + 1] = vertexOffset + 1
            glyphIndices[indexBase + 2] = vertexOffset + 2
            glyphIndices[indexBase + 3] = vertexOffset + 2
            glyphIndices[indexBase + 4] = vertexOffset + 3
            glyphIndices[indexBase + 5] = vertexOffset
            glyphIndex += 1
        }
        return StagedDrawRun.GlyphRun(glyphVertices, glyphIndices)
    }

    fun buildTextureRun(
        textures: List<DrawCommand.Texture>,
        activePathClips: List<DrawPath>,
        safeInteriorRect: Rectangle? = null,
    ): StagedDrawRun.TextureRun {
        val primitives = textures.map { primitive ->
            val raw = texturedQuadMesh(primitive.x, primitive.y, primitive.w, primitive.h)
                .rotatedAboutCenter(primitive.rotationDegrees)
            // The same skip every other primitive type takes. It was passed the rect and ignored
            // it, so a texture wholly inside the clip still paid for an exact clip against it.
            val clipped = if (
                activePathClips.isEmpty() ||
                canSkipExactClip(safeInteriorRect, primitive.x, primitive.y, primitive.w, primitive.h)
            ) {
                raw
            } else {
                exactClip(raw, activePathClips)
            }
            val (vertices, indices) = texturedGeometryBuffers(
                clipped,
                WHITE_COLOR.withAlpha(primitive.alpha),
                primitive.transform,
            )
            TexturedDrawRun(primitive.material, vertices, indices, primitive.blendMode, primitive.premultiplied)
        }
        return StagedDrawRun.TextureRun(primitives)
    }

    fun texturedQuadMesh(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        u0: Float = 0f,
        v0: Float = 0f,
        u1: Float = 1f,
        v1: Float = 1f,
    ): TexturedTriangleMesh = TexturedTriangleMesh(
        vertices = listOf(
            TexturedVertex(DrawPoint(x, y), u0, v0),
            TexturedVertex(DrawPoint(x + w, y), u1, v0),
            TexturedVertex(DrawPoint(x + w, y + h), u1, v1),
            TexturedVertex(DrawPoint(x, y + h), u0, v1),
        ),
        indices = intArrayOf(0, 1, 2, 2, 3, 0),
    )

    private fun TexturedTriangleMesh.rotatedAboutCenter(degrees: Float): TexturedTriangleMesh {
        if (degrees == 0f) return this
        val centerX = vertices.sumOf { it.position.x.toDouble() }.toFloat() / vertices.size
        val centerY = vertices.sumOf { it.position.y.toDouble() }.toFloat() / vertices.size
        val radians = degrees * (PI / 180.0).toFloat()
        val cosine = cos(radians)
        val sine = sin(radians)
        return copy(vertices = vertices.map { vertex ->
            val x = vertex.position.x - centerX
            val y = vertex.position.y - centerY
            vertex.copy(position = DrawPoint(
                x = centerX + x * cosine - y * sine,
                y = centerY + x * sine + y * cosine,
            ))
        })
    }

    fun texturedGeometryBuffers(
        mesh: TexturedTriangleMesh,
        color: Color,
        transform: DrawTransform? = null,
    ): Pair<FloatArray, IntArray> {
        val vertices = FloatArray(mesh.vertices.size * VertexFormats2D.GLYPH_FLOATS_PER_VERTEX)
        var offset = 0
        mesh.vertices.forEach { vertex ->
            writeGlyphVertex(vertices, offset, vertex.position.x, vertex.position.y, vertex.u, vertex.v, color, transform)
            offset += VertexFormats2D.GLYPH_FLOATS_PER_VERTEX
        }
        return vertices to mesh.indices
    }

    private fun chunkColoredTriangleMeshes(
        runs: MutableList<StagedDrawRun>,
        geometries: List<Pair<TriangleMesh, Color>>,
        maxQuads: Int,
    ) {
        val maxVertices = maxQuads * VertexFormats2D.VERTICES_PER_QUAD
        val maxIndices = maxQuads * VertexFormats2D.INDICES_PER_QUAD
        var chunk = mutableListOf<Pair<TriangleMesh, Color>>()
        var chunkVertices = 0
        var chunkIndices = 0

        fun flushChunk() {
            if (chunk.isEmpty()) return
            runs += stageColoredTriangleMeshesToRun(chunk)
            chunk = mutableListOf()
            chunkVertices = 0
            chunkIndices = 0
        }

        for (pair in geometries) {
            val vertexCount = pair.first.points.size
            val indexCount = pair.first.indices.size
            if (chunk.isNotEmpty() && (chunkVertices + vertexCount > maxVertices || chunkIndices + indexCount > maxIndices)) {
                flushChunk()
            }
            chunk += pair
            chunkVertices += vertexCount
            chunkIndices += indexCount
        }
        flushChunk()
    }

    private fun chunkColoredVertexTriangleMeshes(
        runs: MutableList<StagedDrawRun>,
        meshes: List<ColoredTriangleMesh>,
        maxQuads: Int,
    ) {
        val maxVertices = maxQuads * VertexFormats2D.VERTICES_PER_QUAD
        val maxIndices = maxQuads * VertexFormats2D.INDICES_PER_QUAD
        var chunk = mutableListOf<ColoredTriangleMesh>()
        var chunkVertices = 0
        var chunkIndices = 0

        fun flushChunk() {
            if (chunk.isEmpty()) return
            runs += stageColoredVertexTriangleMeshesToRun(chunk)
            chunk = mutableListOf()
            chunkVertices = 0
            chunkIndices = 0
        }

        // Not split: a single tessellated shape becomes its own run, for the reason
        // chunkPlacedMeshes gives -- the buffer grows to fit it.
        for (m in meshes) {
            val vertexCount = m.vertices.size
            val indexCount = m.indices.size
            if (chunk.isNotEmpty() && (chunkVertices + vertexCount > maxVertices || chunkIndices + indexCount > maxIndices)) {
                flushChunk()
            }
            chunk += m
            chunkVertices += vertexCount
            chunkIndices += indexCount
        }
        flushChunk()
    }

    private fun chunkTexturedTriangleMeshes(
        runs: MutableList<StagedDrawRun>,
        geometries: List<Pair<TexturedTriangleMesh, Color>>,
        maxQuads: Int,
    ) {
        val maxVertices = maxQuads * VertexFormats2D.VERTICES_PER_QUAD
        val maxIndices = maxQuads * VertexFormats2D.INDICES_PER_QUAD
        var chunk = mutableListOf<Pair<TexturedTriangleMesh, Color>>()
        var chunkVertices = 0
        var chunkIndices = 0

        fun flushChunk() {
            if (chunk.isEmpty()) return
            runs += stageTexturedTriangleMeshesToRun(chunk)
            chunk = mutableListOf()
            chunkVertices = 0
            chunkIndices = 0
        }

        for (pair in geometries) {
            val vertexCount = pair.first.vertices.size
            val indexCount = pair.first.indices.size
            if (chunk.isNotEmpty() && (chunkVertices + vertexCount > maxVertices || chunkIndices + indexCount > maxIndices)) {
                flushChunk()
            }
            chunk += pair
            chunkVertices += vertexCount
            chunkIndices += indexCount
        }
        flushChunk()
    }

    private fun stageColoredTriangleMeshesToRun(geometries: List<Pair<TriangleMesh, Color>>): StagedDrawRun.QuadRun {
        val totalVertices = geometries.sumOf { it.first.points.size }
        val totalIndices = geometries.sumOf { it.first.indices.size }
        val vertices = FloatArray(totalVertices * VertexFormats2D.FLOATS_PER_VERTEX)
        val indices = IntArray(totalIndices)
        var vertexCursor = 0
        var indexCursor = 0
        var vertexOffset = 0

        geometries.forEach { (triangleMesh, color) ->
            triangleMesh.points.forEach { point ->
                writeVertex(vertices, vertexCursor, point.x, point.y, color)
                vertexCursor += VertexFormats2D.FLOATS_PER_VERTEX
            }
            triangleMesh.indices.forEach { index ->
                indices[indexCursor] = vertexOffset + index
                indexCursor += 1
            }
            vertexOffset += triangleMesh.points.size
        }
        return StagedDrawRun.QuadRun(vertices, indices)
    }

    private fun stageColoredVertexTriangleMeshesToRun(meshes: List<ColoredTriangleMesh>): StagedDrawRun.QuadRun {
        val totalVertices = meshes.sumOf { it.vertices.size }
        val totalIndices = meshes.sumOf { it.indices.size }
        val vertices = FloatArray(totalVertices * VertexFormats2D.FLOATS_PER_VERTEX)
        val indices = IntArray(totalIndices)
        var vertexCursor = 0
        var indexCursor = 0
        var vertexOffset = 0

        meshes.forEach { triangleMesh ->
            triangleMesh.vertices.forEach { vertex ->
                writeVertex(vertices, vertexCursor, vertex.position.x, vertex.position.y, vertex.color)
                vertexCursor += VertexFormats2D.FLOATS_PER_VERTEX
            }
            triangleMesh.indices.forEach { index ->
                indices[indexCursor] = vertexOffset + index
                indexCursor += 1
            }
            vertexOffset += triangleMesh.vertices.size
        }
        return StagedDrawRun.QuadRun(vertices, indices)
    }

    private fun stageTexturedTriangleMeshesToRun(geometries: List<Pair<TexturedTriangleMesh, Color>>): StagedDrawRun.GlyphRun {
        val totalVertices = geometries.sumOf { it.first.vertices.size }
        val totalIndices = geometries.sumOf { it.first.indices.size }
        val vertices = FloatArray(totalVertices * VertexFormats2D.GLYPH_FLOATS_PER_VERTEX)
        val indices = IntArray(totalIndices)
        var vertexCursor = 0
        var indexCursor = 0
        var vertexOffset = 0

        geometries.forEach { (triangleMesh, color) ->
            triangleMesh.vertices.forEach { vertex ->
                writeGlyphVertex(vertices, vertexCursor, vertex.position.x, vertex.position.y, vertex.u, vertex.v, color)
                vertexCursor += VertexFormats2D.GLYPH_FLOATS_PER_VERTEX
            }
            triangleMesh.indices.forEach { index ->
                indices[indexCursor] = vertexOffset + index
                indexCursor += 1
            }
            vertexOffset += triangleMesh.vertices.size
        }
        return StagedDrawRun.GlyphRun(vertices, indices)
    }
}

typealias UiRunCoalescer = DrawRunCoalescer
