/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslFunctionHandle
import com.awakekt.awake.asset.shaderdsl.AslLayoutHandles
import com.awakekt.awake.asset.shaderdsl.AslShaderBuilder
import com.awakekt.awake.asset.shaderdsl.AslType
import com.awakekt.awake.asset.shaderdsl.F32
import com.awakekt.awake.asset.shaderdsl.and
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.ge
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.le
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.texture2dArray
import com.awakekt.awake.asset.shaderdsl.textureDimensions
import com.awakekt.awake.asset.shaderdsl.textureSampleArrayLevel
import com.awakekt.awake.asset.shaderdsl.textureSampleLevel
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toF32
import com.awakekt.awake.asset.shaderdsl.toU32
import com.awakekt.awake.asset.shaderdsl.unaryMinus
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

/** Appends page geometry to the existing lit/cascade uniform ABI. */
object PagedTerrainUniformLayout {
    /** xy = index minimum in local space, z = cell size, w = intervals per height page. */
    val PageGrid = UniformField("pageGrid", GpuDataShape.Vec4)

    /** xy = cell counts, zw = origin's phase modulo twice the coarsest ring spacing. */
    val PageDimensions = UniformField("pageDimensions", GpuDataShape.Vec4)

    /** Uniform layout shared by paged lit and depth shaders. */
    @Suppress("SpreadOperator") // One-time ABI composition, never a frame-path copy.
    val Layout = UniformLayout(*TerrainUniformLayout.Layout.fields, PageGrid, PageDimensions)

    /** What a paged terrain feature's own uniform block holds beside the pass's frame block. */
    @Suppress("SpreadOperator")
    val Draw = UniformLayout(*TerrainUniformLayout.Draw.fields, PageGrid, PageDimensions)
}

/**
 * Page lookups shared by heights and surface shaders. Coordinates are relative to index minimum.
 * @property grid Local footprint origin, cell size and fine intervals.
 * @property dimensions Footprint cell counts and origin phase.
 * @property table Cell-to-layer indirection texture.
 * @property sampler Base-level sampler for pages and coarse images.
 */
class TerrainPageSampling internal constructor(val grid: AslExpr, val dimensions: AslExpr, val table: AslExpr, val sampler: AslExpr) {
    /** Resolves a cell through the page table and samples its resident image or coarse fallback. */
    @Suppress("LongParameterList") // Sampling options describe endpoint and packed-control layouts.
    fun sample(texture: AslExpr, fallback: AslExpr, relative: AslExpr, endpoints: Boolean = true, parts: Int = 1, part: Int = 0, coarseLinear: Boolean = false): AslExpr {
        val cell = clamp(floor(relative / grid.z), vec2(0f.lit, 0f.lit), vec2(dimensions.x - 1f.lit, dimensions.y - 1f.lit))
        val size = textureDimensions(texture)
        val local = clamp(relative / grid.z - cell, vec2(0f.lit, 0f.lit), vec2(1f.lit, 1f.lit))
        // Height endpoints include both edges. Surface texels are cell-centred, selected by callers.
        val uv = if (endpoints) {
            vec2((local.x * (toF32(size.x) - 1f.lit) + 0.5f.lit) / toF32(size.x), (local.y * (toF32(size.y) - 1f.lit) + 0.5f.lit) / toF32(size.y))
        } else {
            vec2(
                (clamp(floor(local.x * toF32(size.x) / parts.toFloat().lit), 0f.lit, toF32(size.x) / parts.toFloat().lit - 1f.lit) * parts.toFloat().lit + (part + 0.5f).lit) / toF32(size.x),
                (clamp(floor(local.y * toF32(size.y)), 0f.lit, toF32(size.y) - 1f.lit) + 0.5f.lit) / toF32(size.y),
            )
        }
        val coarseSize = textureDimensions(fallback)
        val coarseUV = if (coarseLinear) {
            relative / (vec2(dimensions.x, dimensions.y) * grid.z)
        } else if (endpoints) {
            vec2(
                (relative.x / (dimensions.x * grid.z) * (toF32(coarseSize.x) - 1f.lit) + 0.5f.lit) / toF32(coarseSize.x),
                (relative.y / (dimensions.y * grid.z) * (toF32(coarseSize.y) - 1f.lit) + 0.5f.lit) / toF32(coarseSize.y),
            )
        } else {
            vec2(
                (clamp(floor(relative.x / (dimensions.x * grid.z) * toF32(coarseSize.x) / parts.toFloat().lit), 0f.lit, toF32(coarseSize.x) / parts.toFloat().lit - 1f.lit) * parts.toFloat().lit + (part + 0.5f).lit) / toF32(coarseSize.x),
                (clamp(floor(relative.y / (dimensions.y * grid.z) * toF32(coarseSize.y)), 0f.lit, toF32(coarseSize.y) - 1f.lit) + 0.5f.lit) / toF32(coarseSize.y),
            )
        }
        return resolve(texture, fallback, cell, uv, coarseUV)
    }

    /**
     * Resolves a cell through the page table inside [block], branching so only the resident page
     * array or the coarse fallback texture is sampled (never both).
     */
    @Suppress("LongParameterList") // Sampling options describe endpoint and packed-control layouts.
    fun sampleIn(
        block: AslBlockBuilder,
        name: String,
        texture: AslExpr,
        fallback: AslExpr,
        relative: AslExpr,
        endpoints: Boolean = true,
        parts: Int = 1,
        part: Int = 0,
        coarseLinear: Boolean = false,
    ): AslExpr {
        val cell = block.let("${name}Cell", clamp(floor(relative / grid.z), vec2(0f.lit, 0f.lit), vec2(dimensions.x - 1f.lit, dimensions.y - 1f.lit)))
        val local = clamp(relative / grid.z - cell, vec2(0f.lit, 0f.lit), vec2(1f.lit, 1f.lit))
        val uv = pageUv(texture, local, endpoints, parts, part)
        val coarseUV = fallbackUv(fallback, relative, endpoints, parts, part, coarseLinear)
        return resolveIn(block, name, texture, fallback, cell, uv, coarseUV)
    }

    /**
     * Resolves a cell once through the page table inside [block] and samples a paired resident
     * texture set ([first], [second]) or their coarse fallbacks, halving table and texture fetches
     * for paired control maps.
     */
    @Suppress("LongParameterList")
    fun samplePairIn(
        block: AslBlockBuilder,
        name: String,
        first: AslExpr,
        firstFallback: AslExpr,
        second: AslExpr,
        secondFallback: AslExpr,
        relative: AslExpr,
        parts: Int = 1,
        part: Int = 0,
    ): Pair<AslExpr, AslExpr> {
        val cell = block.let("${name}Cell", clamp(floor(relative / grid.z), vec2(0f.lit, 0f.lit), vec2(dimensions.x - 1f.lit, dimensions.y - 1f.lit)))
        val local = block.let("${name}Local", clamp(relative / grid.z - cell, vec2(0f.lit, 0f.lit), vec2(1f.lit, 1f.lit)))
        val firstUv = pageUv(first, local, endpoints = false, parts = parts, part = part)
        val secondUv = pageUv(second, local, endpoints = false, parts = parts, part = part)
        val firstCoarseUv = fallbackUv(firstFallback, relative, endpoints = false, parts = parts, part = part, coarseLinear = false)
        val secondCoarseUv = fallbackUv(secondFallback, relative, endpoints = false, parts = parts, part = part, coarseLinear = false)
        val layer = resolveLayer(block, name, cell)
        val firstSample = block.variable("${name}First", vec4(0f.lit, 0f.lit, 0f.lit, 0f.lit))
        val secondSample = block.variable("${name}Second", vec4(0f.lit, 0f.lit, 0f.lit, 0f.lit))
        block.iff(layer gt (-0.5f).lit) {
            val pageLayer = let("${name}PageLayer", toU32(layer))
            assign(firstSample, textureSampleArrayLevel(first, sampler, firstUv, pageLayer, 0f.lit))
            assign(secondSample, textureSampleArrayLevel(second, sampler, secondUv, pageLayer, 0f.lit))
        }
        block.iff(layer le (-0.5f).lit) {
            assign(firstSample, textureSampleLevel(firstFallback, sampler, firstCoarseUv, 0f.lit))
            assign(secondSample, textureSampleLevel(secondFallback, sampler, secondCoarseUv, 0f.lit))
        }
        return firstSample to secondSample
    }

    /**
     * Endpoint-inclusive lookup of the global fine [knot], whose components are whole numbers.
     * Integer-valued arithmetic picks the same positive-side owner as `TerrainPageLayout.owner` on the
     * CPU, where dividing a reconstructed world position could round a boundary knot into the wrong cell.
     */
    internal fun sampleKnot(texture: AslExpr, fallback: AslExpr, knot: AslExpr): AslExpr {
        val intervals = grid.w
        val cell = clamp(floor(knot / intervals), vec2(0f.lit, 0f.lit), vec2(dimensions.x - 1f.lit, dimensions.y - 1f.lit))
        val size = textureDimensions(texture)
        val sample = knot - cell * intervals
        val uv = vec2((sample.x + 0.5f.lit) / toF32(size.x), (sample.y + 0.5f.lit) / toF32(size.y))
        val coarseSize = textureDimensions(fallback)
        val coarseUV = vec2(
            (knot.x / (dimensions.x * intervals) * (toF32(coarseSize.x) - 1f.lit) + 0.5f.lit) / toF32(coarseSize.x),
            (knot.y / (dimensions.y * intervals) * (toF32(coarseSize.y) - 1f.lit) + 0.5f.lit) / toF32(coarseSize.y),
        )
        return resolve(texture, fallback, cell, uv, coarseUV)
    }

    internal fun sampleKnotIn(block: AslBlockBuilder, texture: AslExpr, fallback: AslExpr, knot: AslExpr): AslExpr {
        val intervals = grid.w
        val cell = block.let("knotCell", clamp(floor(knot / intervals), vec2(0f.lit, 0f.lit), vec2(dimensions.x - 1f.lit, dimensions.y - 1f.lit)))
        val size = textureDimensions(texture)
        val sample = knot - cell * intervals
        val uv = vec2((sample.x + 0.5f.lit) / toF32(size.x), (sample.y + 0.5f.lit) / toF32(size.y))
        val coarseSize = textureDimensions(fallback)
        val coarseUV = vec2(
            (knot.x / (dimensions.x * intervals) * (toF32(coarseSize.x) - 1f.lit) + 0.5f.lit) / toF32(coarseSize.x),
            (knot.y / (dimensions.y * intervals) * (toF32(coarseSize.y) - 1f.lit) + 0.5f.lit) / toF32(coarseSize.y),
        )
        return resolveIn(block, "knot", texture, fallback, cell, uv, coarseUV)
    }

    private fun pageUv(texture: AslExpr, local: AslExpr, endpoints: Boolean, parts: Int, part: Int): AslExpr {
        val size = textureDimensions(texture)
        return if (endpoints) {
            vec2((local.x * (toF32(size.x) - 1f.lit) + 0.5f.lit) / toF32(size.x), (local.y * (toF32(size.y) - 1f.lit) + 0.5f.lit) / toF32(size.y))
        } else {
            vec2(
                (clamp(floor(local.x * toF32(size.x) / parts.toFloat().lit), 0f.lit, toF32(size.x) / parts.toFloat().lit - 1f.lit) * parts.toFloat().lit + (part + 0.5f).lit) / toF32(size.x),
                (clamp(floor(local.y * toF32(size.y)), 0f.lit, toF32(size.y) - 1f.lit) + 0.5f.lit) / toF32(size.y),
            )
        }
    }

    @Suppress("LongParameterList")
    private fun fallbackUv(fallback: AslExpr, relative: AslExpr, endpoints: Boolean, parts: Int, part: Int, coarseLinear: Boolean): AslExpr {
        val coarseSize = textureDimensions(fallback)
        return if (coarseLinear) {
            relative / (vec2(dimensions.x, dimensions.y) * grid.z)
        } else if (endpoints) {
            vec2(
                (relative.x / (dimensions.x * grid.z) * (toF32(coarseSize.x) - 1f.lit) + 0.5f.lit) / toF32(coarseSize.x),
                (relative.y / (dimensions.y * grid.z) * (toF32(coarseSize.y) - 1f.lit) + 0.5f.lit) / toF32(coarseSize.y),
            )
        } else {
            vec2(
                (clamp(floor(relative.x / (dimensions.x * grid.z) * toF32(coarseSize.x) / parts.toFloat().lit), 0f.lit, toF32(coarseSize.x) / parts.toFloat().lit - 1f.lit) * parts.toFloat().lit + (part + 0.5f).lit) / toF32(coarseSize.x),
                (clamp(floor(relative.y / (dimensions.y * grid.z) * toF32(coarseSize.y)), 0f.lit, toF32(coarseSize.y) - 1f.lit) + 0.5f.lit) / toF32(coarseSize.y),
            )
        }
    }

    /** Resolves [cell] once through the page table inside [block], returning `-1.0` when unmapped. */
    fun resolveLayerIn(block: AslBlockBuilder, name: String, cell: AslExpr): AslExpr =
        resolveLayer(block, name, cell)

    private fun resolveLayer(block: AslBlockBuilder, name: String, cell: AslExpr): AslExpr {
        val lookup = block.let(
            "${name}Lookup",
            textureSampleLevel(table, sampler, vec2((cell.x + 0.5f.lit) / dimensions.x, (cell.y + 0.5f.lit) / dimensions.y), 0f.lit),
        )
        return block.let(
            "${name}Layer",
            floor(lookup.x * 255f.lit + 0.5f.lit) + floor(lookup.y * 255f.lit + 0.5f.lit) * 256f.lit - 1f.lit,
        )
    }

    @Suppress("LongParameterList")
    private fun resolveIn(
        block: AslBlockBuilder,
        name: String,
        texture: AslExpr,
        fallback: AslExpr,
        cell: AslExpr,
        uv: AslExpr,
        coarseUV: AslExpr,
    ): AslExpr {
        val layer = resolveLayer(block, name, cell)
        val result = block.variable("${name}Sample", vec4(0f.lit, 0f.lit, 0f.lit, 0f.lit))
        block.iff(layer gt (-0.5f).lit) {
            assign(result, textureSampleArrayLevel(texture, sampler, uv, toU32(layer), 0f.lit))
        }
        block.iff(layer le (-0.5f).lit) {
            assign(result, textureSampleLevel(fallback, sampler, coarseUV, 0f.lit))
        }
        return result
    }

    /** The resident image at [uv] when the table maps [cell] to a layer, otherwise the coarse image at [coarseUV]. */
    private fun resolve(texture: AslExpr, fallback: AslExpr, cell: AslExpr, uv: AslExpr, coarseUV: AslExpr): AslExpr {
        val lookup = textureSampleLevel(table, sampler, vec2((cell.x + 0.5f.lit) / dimensions.x, (cell.y + 0.5f.lit) / dimensions.y), 0f.lit)
        val layer = floor(lookup.x * 255f.lit + 0.5f.lit) + floor(lookup.y * 255f.lit + 0.5f.lit) * 256f.lit - 1f.lit
        return select(textureSampleLevel(fallback, sampler, coarseUV, 0f.lit), textureSampleArrayLevel(texture, sampler, uv, toU32(max(layer, 0f.lit)), 0f.lit), layer gt (-0.5f).lit)
    }
}

internal fun AslShaderBuilder.pageSampling(handles: AslLayoutHandles, sampler: AslExpr): TerrainPageSampling {
    val table by texture2d(group = 0, binding = TERRAIN_PAGE_TABLE_BINDING)
    return TerrainPageSampling(handles.value("pageGrid"), handles.value("pageDimensions"), table, sampler)
}

internal class PagedHeightFunctions(
    val height: AslFunctionHandle,
    val heightAndNormal: AslFunctionHandle?,
)

@Suppress("LongMethod")
internal fun AslShaderBuilder.pagedHeightSampler(
    pages: TerrainPageSampling,
    fallback: AslExpr,
    includeNormal: Boolean = false,
): PagedHeightFunctions {
    val heightPages by texture2dArray(group = 0, binding = TERRAIN_HEIGHT_PAGES_BINDING)
    val knot = fn("pageHeightKnot") {
        val index by param(GpuDataShape.Vec2)
        returnValue(decodeHeight(pages.sampleKnotIn(this, heightPages, fallback, index)))
    }
    val heightFn = fn("pageHeight") {
        val position by param(GpuDataShape.Vec2)
        val relative = let(
            "relative",
            clamp(
                position - vec2(pages.grid.x, pages.grid.y),
                vec2(0f.lit, 0f.lit),
                vec2(pages.dimensions.x, pages.dimensions.y) * pages.grid.z,
            ),
        )
        val spacing = let("spacing", pages.grid.z / pages.grid.w)
        val sample = let("sample", relative / spacing)
        val base = let("base", floor(sample))
        val fraction = let("fraction", sample - base)
        // Unmorphed clipmap vertices sit on integer knots (~70% of vertices): 1 knot instead of 4.
        iff((fraction.x lt 0.0001f.lit) and (fraction.y lt 0.0001f.lit)) {
            returnValue(knot(base))
        }
        val limit = let("limit", vec2(pages.dimensions.x, pages.dimensions.y) * pages.grid.w)
        val k11 = let("k11", min(base + vec2(1f.lit, 1f.lit), limit))
        val maxCell = vec2(pages.dimensions.x - 1f.lit, pages.dimensions.y - 1f.lit)
        val c0 = let("c0", clamp(floor(base / pages.grid.w), vec2(0f.lit, 0f.lit), maxCell))
        val c1 = let("c1", clamp(floor(k11 / pages.grid.w), vec2(0f.lit, 0f.lit), maxCell))
        // Interior morphed vertices share one cell: 1 page-table lookup + 4 heights instead of 8 fetches.
        iff((c0.x eq c1.x) and (c0.y eq c1.y)) {
            val layer = pages.resolveLayerIn(this, "single", c0)
            val k10 = vec2(k11.x, base.y)
            val k01 = vec2(base.x, k11.y)
            iff(layer gt (-0.5f).lit) {
                val pDims = textureDimensions(heightPages)
                val pSize = let("pSize", vec2(toF32(pDims.x), toF32(pDims.y)))
                val pLayer = let("pLayer", toU32(layer))
                val a = let("sa", decodeHeight(textureSampleArrayLevel(heightPages, pages.sampler, pages.residentKnotUv(pSize, base, c0), pLayer, 0f.lit)))
                val b = let("sb", decodeHeight(textureSampleArrayLevel(heightPages, pages.sampler, pages.residentKnotUv(pSize, k10, c0), pLayer, 0f.lit)))
                val c = let("sc", decodeHeight(textureSampleArrayLevel(heightPages, pages.sampler, pages.residentKnotUv(pSize, k01, c0), pLayer, 0f.lit)))
                val d = let("sd", decodeHeight(textureSampleArrayLevel(heightPages, pages.sampler, pages.residentKnotUv(pSize, k11, c0), pLayer, 0f.lit)))
                returnValue(mix(mix(a, b, fraction.x), mix(c, d, fraction.x), fraction.y))
            }
            val cDims = textureDimensions(fallback)
            val cSize = let("cSize", vec2(toF32(cDims.x), toF32(cDims.y)))
            val a = let("fa", decodeHeight(textureSampleLevel(fallback, pages.sampler, pages.fallbackKnotUv(cSize, base), 0f.lit)))
            val b = let("fb", decodeHeight(textureSampleLevel(fallback, pages.sampler, pages.fallbackKnotUv(cSize, k10), 0f.lit)))
            val c = let("fc", decodeHeight(textureSampleLevel(fallback, pages.sampler, pages.fallbackKnotUv(cSize, k01), 0f.lit)))
            val d = let("fd", decodeHeight(textureSampleLevel(fallback, pages.sampler, pages.fallbackKnotUv(cSize, k11), 0f.lit)))
            returnValue(mix(mix(a, b, fraction.x), mix(c, d, fraction.x), fraction.y))
        }
        val a = let("a", knot(base))
        val b = let("b", knot(min(base + vec2(1f.lit, 0f.lit), limit)))
        val c = let("c", knot(min(base + vec2(0f.lit, 1f.lit), limit)))
        val d = let("d", knot(k11))
        returnValue(mix(mix(a, b, fraction.x), mix(c, d, fraction.x), fraction.y))
    }
    val heightAndNormalFn = if (includeNormal) {
        pagedHeightAndNormalFunction(pages, heightPages, fallback, heightFn)
    } else {
        null
    }
    return PagedHeightFunctions(heightFn, heightAndNormalFn)
}

internal fun TerrainPageSampling.residentKnotUv(pageSize: AslExpr, knot: AslExpr, cell: AslExpr): AslExpr {
    val sample = knot - cell * grid.w
    return vec2((sample.x + 0.5f.lit) / pageSize.x, (sample.y + 0.5f.lit) / pageSize.y)
}

internal fun TerrainPageSampling.fallbackKnotUv(coarseSize: AslExpr, knot: AslExpr): AslExpr = vec2(
    (knot.x / (dimensions.x * grid.w) * (coarseSize.x - 1f.lit) + 0.5f.lit) / coarseSize.x,
    (knot.y / (dimensions.y * grid.w) * (coarseSize.y - 1f.lit) + 0.5f.lit) / coarseSize.y,
)

@Suppress("LongMethod")
private fun AslShaderBuilder.pagedHeightAndNormalFunction(
    pages: TerrainPageSampling,
    heightPages: AslExpr,
    fallback: AslExpr,
    pageHeight: AslFunctionHandle,
): AslFunctionHandle = fn("pageHeightAndNormal", returns = AslType.Data(GpuDataShape.Vec4)) {
    val position by param(GpuDataShape.Vec2)
    val heightScale by param(F32)

    val extent = let("extent", vec2(pages.dimensions.x, pages.dimensions.y) * pages.grid.z)
    val relative = let("relative", clamp(position - vec2(pages.grid.x, pages.grid.y), vec2(0f.lit, 0f.lit), extent))
    val step = let("step", pages.grid.z / pages.grid.w)
    val sample = let("sample", relative / step)
    val base = let("base", floor(sample))
    val fraction = let("fraction", sample - base)
    val limit = let("limit", vec2(pages.dimensions.x, pages.dimensions.y) * pages.grid.w)
    val maxCell = vec2(pages.dimensions.x - 1f.lit, pages.dimensions.y - 1f.lit)

    val left = let("leftX", max(position.x - step, pages.grid.x))
    val right = let("rightX", min(position.x + step, pages.grid.x + extent.x))
    val down = let("downZ", max(position.y - step, pages.grid.y))
    val up = let("upZ", min(position.y + step, pages.grid.y + extent.y))
    val invSpanX = let("invSpanX", heightScale / max(right - left, 0.0001f.lit))
    val invSpanZ = let("invSpanZ", heightScale / max(up - down, 0.0001f.lit))

    val kMin = let("kMin", max(base - vec2(1f.lit, 1f.lit), vec2(0f.lit, 0f.lit)))
    val kMax = let("kMax", min(base + vec2(1f.lit, 1f.lit), limit))
    val cMin = let("cMin", clamp(floor(kMin / pages.grid.w), vec2(0f.lit, 0f.lit), maxCell))
    val cMax = let("cMax", clamp(floor(kMax / pages.grid.w), vec2(0f.lit, 0f.lit), maxCell))

    // Fast path 1 (~70% of vertices): unmorphed integer knot whose 5-point stencil lies in one cell.
    // 1 page-table fetch + 5 height fetches = 6 fetches (down from 40).
    iff((fraction.x lt 0.0001f.lit) and (fraction.y lt 0.0001f.lit) and (cMin.x eq cMax.x) and (cMin.y eq cMax.y)) {
        val layer = pages.resolveLayerIn(this, "fast", cMin)
        val kL = vec2(kMin.x, base.y)
        val kR = vec2(kMax.x, base.y)
        val kD = vec2(base.x, kMin.y)
        val kU = vec2(base.x, kMax.y)
        iff(layer gt (-0.5f).lit) {
            val pDims = textureDimensions(heightPages)
            val pSize = let("fpSize", vec2(toF32(pDims.x), toF32(pDims.y)))
            val pLayer = let("fpLayer", toU32(layer))
            val hC = let("fhC", decodeHeight(textureSampleArrayLevel(heightPages, pages.sampler, pages.residentKnotUv(pSize, base, cMin), pLayer, 0f.lit)))
            val hL = let("fhL", decodeHeight(textureSampleArrayLevel(heightPages, pages.sampler, pages.residentKnotUv(pSize, kL, cMin), pLayer, 0f.lit)))
            val hR = let("fhR", decodeHeight(textureSampleArrayLevel(heightPages, pages.sampler, pages.residentKnotUv(pSize, kR, cMin), pLayer, 0f.lit)))
            val hD = let("fhD", decodeHeight(textureSampleArrayLevel(heightPages, pages.sampler, pages.residentKnotUv(pSize, kD, cMin), pLayer, 0f.lit)))
            val hU = let("fhU", decodeHeight(textureSampleArrayLevel(heightPages, pages.sampler, pages.residentKnotUv(pSize, kU, cMin), pLayer, 0f.lit)))
            val n = let("fnRes", normalize(vec3(-(hR - hL) * invSpanX, 1f.lit, -(hU - hD) * invSpanZ)))
            returnValue(vec4(hC, n.x, n.y, n.z))
        }
        val cDims = textureDimensions(fallback)
        val cSize = let("fcSize", vec2(toF32(cDims.x), toF32(cDims.y)))
        val hC = let("fbC", decodeHeight(textureSampleLevel(fallback, pages.sampler, pages.fallbackKnotUv(cSize, base), 0f.lit)))
        val hL = let("fbL", decodeHeight(textureSampleLevel(fallback, pages.sampler, pages.fallbackKnotUv(cSize, kL), 0f.lit)))
        val hR = let("fbR", decodeHeight(textureSampleLevel(fallback, pages.sampler, pages.fallbackKnotUv(cSize, kR), 0f.lit)))
        val hD = let("fbD", decodeHeight(textureSampleLevel(fallback, pages.sampler, pages.fallbackKnotUv(cSize, kD), 0f.lit)))
        val hU = let("fbU", decodeHeight(textureSampleLevel(fallback, pages.sampler, pages.fallbackKnotUv(cSize, kU), 0f.lit)))
        val n = let("fnFb", normalize(vec3(-(hR - hL) * invSpanX, 1f.lit, -(hU - hD) * invSpanZ)))
        returnValue(vec4(hC, n.x, n.y, n.z))
    }

    // Fast path 2 (~25% of vertices): morphed interior vertex sharing the 12 unique stencil knots in one cell.
    // 1 page-table fetch + 12 height fetches = 13 fetches (down from 40), bit-exact to 5x pageHeight.
    val interior = (base.x ge 1f.lit) and (base.y ge 1f.lit) and (base.x le limit.x - 2f.lit) and (base.y le limit.y - 2f.lit)
    val kMax2 = min(base + vec2(2f.lit, 2f.lit), limit)
    val cMax2 = let("cMax2", clamp(floor(kMax2 / pages.grid.w), vec2(0f.lit, 0f.lit), maxCell))
    iff(interior and (cMin.x eq cMax2.x) and (cMin.y eq cMax2.y)) {
        val layer = pages.resolveLayerIn(this, "morph", cMin)
        fun AslBlockBuilder.evalStencil(prefix: String, sampleAt: (AslExpr) -> AslExpr): AslExpr {
            val h00 = let("${prefix}00", sampleAt(base))
            val h10 = let("${prefix}10", sampleAt(base + vec2(1f.lit, 0f.lit)))
            val h01 = let("${prefix}01", sampleAt(base + vec2(0f.lit, 1f.lit)))
            val h11 = let("${prefix}11", sampleAt(base + vec2(1f.lit, 1f.lit)))
            val hm10 = let("${prefix}m10", sampleAt(base + vec2((-1f).lit, 0f.lit)))
            val hm11 = let("${prefix}m11", sampleAt(base + vec2((-1f).lit, 1f.lit)))
            val h20 = let("${prefix}20", sampleAt(base + vec2(2f.lit, 0f.lit)))
            val h21 = let("${prefix}21", sampleAt(base + vec2(2f.lit, 1f.lit)))
            val h0m1 = let("${prefix}0m1", sampleAt(base + vec2(0f.lit, (-1f).lit)))
            val h1m1 = let("${prefix}1m1", sampleAt(base + vec2(1f.lit, (-1f).lit)))
            val h02 = let("${prefix}02", sampleAt(base + vec2(0f.lit, 2f.lit)))
            val h12 = let("${prefix}12", sampleAt(base + vec2(1f.lit, 2f.lit)))
            val hC = let("${prefix}C", mix(mix(h00, h10, fraction.x), mix(h01, h11, fraction.x), fraction.y))
            val hL = let("${prefix}L", mix(mix(hm10, h00, fraction.x), mix(hm11, h01, fraction.x), fraction.y))
            val hR = let("${prefix}R", mix(mix(h10, h20, fraction.x), mix(h11, h21, fraction.x), fraction.y))
            val hD = let("${prefix}D", mix(mix(h0m1, h1m1, fraction.x), mix(h00, h10, fraction.x), fraction.y))
            val hU = let("${prefix}U", mix(mix(h01, h11, fraction.x), mix(h02, h12, fraction.x), fraction.y))
            val n = let("${prefix}N", normalize(vec3(-(hR - hL) * invSpanX, 1f.lit, -(hU - hD) * invSpanZ)))
            return vec4(hC, n.x, n.y, n.z)
        }
        iff(layer gt (-0.5f).lit) {
            val pDims = textureDimensions(heightPages)
            val pSize = let("mpSize", vec2(toF32(pDims.x), toF32(pDims.y)))
            val pLayer = let("mpLayer", toU32(layer))
            returnValue(
                evalStencil("mr") { k ->
                    decodeHeight(textureSampleArrayLevel(heightPages, pages.sampler, pages.residentKnotUv(pSize, k, cMin), pLayer, 0f.lit))
                },
            )
        }
        val cDims = textureDimensions(fallback)
        val cSize = let("mcSize", vec2(toF32(cDims.x), toF32(cDims.y)))
        returnValue(
            evalStencil("mf") { k ->
                decodeHeight(textureSampleLevel(fallback, pages.sampler, pages.fallbackKnotUv(cSize, k), 0f.lit))
            },
        )
    }

    // Seam / border fallback: delegates to optimized pageHeight.
    val hC = let("seamC", pageHeight(position))
    val hR = let("seamR", pageHeight(vec2(right, position.y)))
    val hL = let("seamL", pageHeight(vec2(left, position.y)))
    val hU = let("seamU", pageHeight(vec2(position.x, up)))
    val hD = let("seamD", pageHeight(vec2(position.x, down)))
    val n = let("seamN", normalize(vec3(-(hR - hL) * invSpanX, 1f.lit, -(hU - hD) * invSpanZ)))
    returnValue(vec4(hC, n.x, n.y, n.z))
}
