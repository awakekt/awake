/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslFunctionHandle
import com.awakekt.awake.asset.shaderdsl.AslLayoutHandles
import com.awakekt.awake.asset.shaderdsl.AslShaderBuilder
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.gt
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
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.terrain.TERRAIN_HEIGHT_PAGES_BINDING
import com.awakekt.awake.terrain.TERRAIN_PAGE_TABLE_BINDING

/** Appends page geometry to the existing lit/cascade uniform ABI. */
object PagedTerrainUniformLayout {
    /** xy = index minimum in local space, z = cell size, w = intervals per height page. */
    val PageGrid = UniformField("pageGrid", GpuDataShape.Vec4)

    /** xy = cell counts, zw = origin's phase modulo twice the coarsest ring spacing. */
    val PageDimensions = UniformField("pageDimensions", GpuDataShape.Vec4)

    /** Uniform layout shared by paged lit and depth shaders. */
    @Suppress("SpreadOperator") // One-time ABI composition, never a frame-path copy.
    val Layout = UniformLayout(*TerrainUniformLayout.Layout.fields, PageGrid, PageDimensions)
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
        val lookup = textureSampleLevel(table, sampler, vec2((cell.x + 0.5f.lit) / dimensions.x, (cell.y + 0.5f.lit) / dimensions.y), 0f.lit)
        val layer = floor(lookup.x * 255f.lit + 0.5f.lit) + floor(lookup.y * 255f.lit + 0.5f.lit) * 256f.lit - 1f.lit
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
        val relative by param(GpuDataShape.Vec2)
        returnValue(decodeHeight(pages.sample(heightPages, fallback, relative)))
    }
    return fn("pageHeight") {
        val position by param(GpuDataShape.Vec2)
        val relative = let("relative", clamp(position - vec2(pages.grid.x, pages.grid.y), vec2(0f.lit, 0f.lit), vec2(pages.dimensions.x, pages.dimensions.y) * pages.grid.z))
        val spacing = let("spacing", pages.grid.z / pages.grid.w)
        val sample = let("sample", relative / spacing)
        val base = let("base", floor(sample))
        val fraction = let("fraction", sample - base)
        val limit = vec2(pages.dimensions.x, pages.dimensions.y) * pages.grid.w
        val a = let("a", knot(base * spacing))
        val b = let("b", knot(min(base + vec2(1f.lit, 0f.lit), limit) * spacing))
        val c = let("c", knot(min(base + vec2(0f.lit, 1f.lit), limit) * spacing))
        val d = let("d", knot(min(base + vec2(1f.lit, 1f.lit), limit) * spacing))
        returnValue(mix(mix(a, b, fraction.x), mix(c, d, fraction.x), fraction.y))
    }
}
