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
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.le
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
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
import com.awakekt.awake.asset.shaderdsl.vec2
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

internal fun AslShaderBuilder.pagedHeightSampler(pages: TerrainPageSampling, fallback: AslExpr): AslFunctionHandle {
    val heightPages by texture2dArray(group = 0, binding = TERRAIN_HEIGHT_PAGES_BINDING)
    val knot = fn("pageHeightKnot") {
        val index by param(GpuDataShape.Vec2)
        returnValue(decodeHeight(pages.sampleKnotIn(this, heightPages, fallback, index)))
    }
    return fn("pageHeight") {
        val position by param(GpuDataShape.Vec2)
        val relative = let("relative", clamp(position - vec2(pages.grid.x, pages.grid.y), vec2(0f.lit, 0f.lit), vec2(pages.dimensions.x, pages.dimensions.y) * pages.grid.z))
        val spacing = let("spacing", pages.grid.z / pages.grid.w)
        val sample = let("sample", relative / spacing)
        val base = let("base", floor(sample))
        val fraction = let("fraction", sample - base)
        val limit = vec2(pages.dimensions.x, pages.dimensions.y) * pages.grid.w
        val a = let("a", knot(base))
        val b = let("b", knot(min(base + vec2(1f.lit, 0f.lit), limit)))
        val c = let("c", knot(min(base + vec2(0f.lit, 1f.lit), limit)))
        val d = let("d", knot(min(base + vec2(1f.lit, 1f.lit), limit)))
        returnValue(mix(mix(a, b, fraction.x), mix(c, d, fraction.x), fraction.y))
    }
}
