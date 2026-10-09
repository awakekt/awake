/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.and
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.texture2dArray
import com.awakekt.awake.asset.shaderdsl.textureDimensions
import com.awakekt.awake.asset.shaderdsl.textureSampleArray
import com.awakekt.awake.asset.shaderdsl.textureSampleLevel
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toF32
import com.awakekt.awake.asset.shaderdsl.toU32
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.asset.shaderpack.DebugSurface
import com.awakekt.awake.asset.shaderpack.TERRAIN_SURFACE_FIRST_BINDING
import com.awakekt.awake.asset.shaderpack.TerrainPageSampling
import com.awakekt.awake.asset.shaderpack.debugLayerColor
import com.awakekt.awake.asset.shaderpack.debugViewColor
import com.awakekt.awake.asset.shaderpack.pagedTerrainClipmapVertexStage
import com.awakekt.awake.asset.shaderpack.sceneDisplayTransform
import com.awakekt.awake.asset.shaderpack.terrainClipmapDiscardUnderFinerRing
import com.awakekt.awake.asset.shaderpack.terrainClipmapVertexStage
import com.awakekt.awake.asset.shaderpack.terrainShadowSampling
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic

/** The palette's albedo images, one array layer each, height in alpha. See [packLayerArray]. */
const val LAYER_ALBEDO_BINDING: Int = TERRAIN_SURFACE_FIRST_BINDING

/** Per-layer tiling and sharpness. See [layerTable]. */
const val LAYER_TABLE_BINDING: Int = TERRAIN_SURFACE_FIRST_BINDING + 1

/** Shader texture binding slot for terrain control layer indices. */
const val CONTROL_INDICES_BINDING: Int = TERRAIN_SURFACE_FIRST_BINDING + 2

/** Shader texture binding slot for terrain control layer blend weights. */
const val CONTROL_WEIGHTS_BINDING: Int = TERRAIN_SURFACE_FIRST_BINDING + 3
private const val LAYER_SAMPLER_BINDING = TERRAIN_SURFACE_FIRST_BINDING + 4

/** Baked lighting. See [TerrainLightmap]. */
const val LIGHTMAP_BINDING: Int = TERRAIN_SURFACE_FIRST_BINDING + 5

/** Whole-world coarse control indices, read where a streamed world has no resident cell. */
internal const val FALLBACK_CONTROL_INDICES_BINDING = 34

/** Whole-world coarse control weights, beside [FALLBACK_CONTROL_INDICES_BINDING]. */
internal const val FALLBACK_CONTROL_WEIGHTS_BINDING = 35

/** Whole-world coarse baked lighting for a streamed world. */
internal const val FALLBACK_LIGHTMAP_BINDING = 36

/** How far below the highest surface another still shows through, in weight-plus-height units. */
private const val BLEND_DEPTH = 0.25f

/** The sun's height, as the sine of its elevation, over which the bake's sunlit share fades in. */
private const val SUNRISE_FADE = 0.1f
private const val NO_LAYER = -1f
private const val EPSILON = 1e-5f
private const val CONTROL_TAPS = 4

/**
 * Shades a clipmap terrain from an unbounded layer palette at a fixed per-pixel cost.
 *
 * Each pixel reads the four control texels around it, merges their layers into the
 * [CONTROL_SLOTS] strongest (weighted by bilinear distance), and samples only those from the
 * albedo array. Adding layers to the palette changes data, not this shader or its cost.
 *
 * Control texels are read at their centres with the shared linear sampler at level 0, which
 * returns each texel exactly: content textures share one sampler, and the DSL has no
 * `textureLoad`. Every texture sample sits outside the merge's branches, where implicit
 * derivatives are defined.
 *
 * The engine's shadows darken both lighting paths: the sun's direct share, and the bake down to
 * the ambient floor, since a bake cannot know what stands on the terrain now. The bake's sunlit
 * share fades out as the sun sets, as the direct share does through N·L, so a sun below the horizon
 * leaves the bake at ambient rather than lit only beyond the shadow range.
 */
fun terrainLayersShader(clipSpace: ClipSpace): AslShaderDefinition = terrainLayersShader(clipSpace, CONTROL_SLOTS)

/** [terrainLayersShader] merging into [slots] layers, about twice the per-pixel cost at [MAX_CONTROL_SLOTS]. */
@Suppress("LongMethod") // Ordered resource and fragment construction share the terrain stage.
internal fun terrainLayersShader(clipSpace: ClipSpace, slots: Int, paged: Boolean = false): AslShaderDefinition = shader(
    (if (paged) "paged_" else "") + if (slots == CONTROL_SLOTS) "terrain_layers" else "terrain_layers_$slots",
) {
    val terrain = if (paged) pagedTerrainClipmapVertexStage() else terrainClipmapVertexStage()
    val shadows = terrainShadowSampling(terrain, clipSpace)
    val displayTransform = sceneDisplayTransform(decodesDisplayReferred = true)
    val group = BindingLayout.Standard.slot(BindingSemantic.Material)
    val albedoLayers by texture2dArray(group = group, binding = LAYER_ALBEDO_BINDING)
    val layerParams by texture2d(group = group, binding = LAYER_TABLE_BINDING)
    val controlIndices by if (paged) texture2dArray(group = group, binding = CONTROL_INDICES_BINDING) else texture2d(group = group, binding = CONTROL_INDICES_BINDING)
    val controlWeights by if (paged) texture2dArray(group = group, binding = CONTROL_WEIGHTS_BINDING) else texture2d(group = group, binding = CONTROL_WEIGHTS_BINDING)
    val layerSampler by sampler(group = group, binding = LAYER_SAMPLER_BINDING)
    val lightmap by if (paged) texture2dArray(group = group, binding = LIGHTMAP_BINDING) else texture2d(group = group, binding = LIGHTMAP_BINDING)
    val fallbackIndices = if (paged) {
        val fallbackIndices by texture2d(group = group, binding = FALLBACK_CONTROL_INDICES_BINDING)
        fallbackIndices
    } else {
        null
    }
    val fallbackWeights = if (paged) {
        val fallbackWeights by texture2d(group = group, binding = FALLBACK_CONTROL_WEIGHTS_BINDING)
        fallbackWeights
    } else {
        null
    }
    val fallbackLightmap = if (paged) {
        val fallbackLightmap by texture2d(group = group, binding = FALLBACK_LIGHTMAP_BINDING)
        fallbackLightmap
    } else {
        null
    }

    fragment {
        terrainClipmapDiscardUnderFinerRing(terrain)
        val merged = mergeControlTaps(slots, terrain.worldPosition, terrain.terrainSampling, controlIndices, controlWeights, layerSampler, terrain.pages, fallbackIndices, fallbackWeights)
        // Anchor palette repeats to the fixed index footprint, so floating-origin shifts preserve UVs.
        val surfacePosition = terrain.pages?.let { pages -> vec4(terrain.worldPosition.x - pages.grid.x, terrain.worldPosition.y, terrain.worldPosition.z - pages.grid.y, 1f.lit).xyz } ?: terrain.worldPosition
        val albedo = blendLayers(merged, surfacePosition, albedoLayers, layerParams, layerSampler)
        val normal = let("normal", normalize(terrain.worldNormal))
        val toLight = let("toLight", normalize(terrain.sunDirection.xyz))
        val ambient = let("ambient", terrain.sunDirection.w)
        val nDotL = let("nDotL", max(dot(normal, toLight), 0f.lit))
        val shadow = let("shadow", shadows.sampleShadow(terrain.worldPosition, normal, nDotL))
        val lighting = let("lighting", ambient + (1f.lit - ambient) * nDotL * shadow)
        val position = terrain.worldPosition
        val sampling = terrain.terrainSampling
        val pages = terrain.pages
        val baked = let(
            "baked",
            if (pages != null && fallbackLightmap != null) {
                samplePagedLight(pages, lightmap, fallbackLightmap, vec2(position.x - pages.grid.x, position.z - pages.grid.y))
            } else {
                textureSampleLevel(lightmap, layerSampler, vec2(position.x / sampling.x + 0.5f.lit, position.z / sampling.y + 0.5f.lit), 0f.lit)
            },
        )
        // 128 in the lightmap is x1; its alpha hands lighting over from the sun to the bake.
        val daylight = let("daylight", saturate(toLight.y / SUNRISE_FADE.lit))
        val light = let("light", mix(lighting, mix(ambient, 1f.lit, shadow * daylight), baked.w))
        val surface = DebugSurface(
            normal = normal,
            worldPosition = position,
            albedo = albedo,
            shadow = shadow,
            shadowCascade = shadows.shadowCascade(position),
            layerWeights = layerWeightColor(merged),
            dominantLayer = dominantLayerColor(merged),
            lightmap = baked.xyz,
        )
        val lit = let("lit", albedo * baked.xyz * 2f.lit * light)
        val shaded = vec4(displayTransform.displayReferred(lit, terrain.exposure.x), 1f.lit)
        colorOutput(debugViewColor(terrain.debugView, terrain.cascades.cameraPosition, surface, shaded))
    }
}

/** Four global cell-centred taps preserve baked-light interpolation across resident boundaries. */
private fun AslBlockBuilder.samplePagedLight(pages: TerrainPageSampling, image: AslExpr, fallback: AslExpr, relative: AslExpr): AslExpr {
    val size = let("lightPageSize", textureDimensions(image))
    val pageSize = vec2(toF32(size.x), toF32(size.y))
    val limit = pageSize * vec2(pages.dimensions.x, pages.dimensions.y)
    val texel = let("lightTexel", relative / pages.grid.z * pageSize - vec2(0.5f.lit, 0.5f.lit))
    val base = let("lightBase", floor(texel))
    val fraction = let("lightFraction", texel - base)
    val taps = List(4) { tap ->
        val knot = clamp(base + vec2((tap % 2).toFloat().lit, (tap / 2).toFloat().lit), vec2(0f.lit, 0f.lit), limit - vec2(1f.lit, 1f.lit))
        let("lightTap$tap", pages.sample(image, fallback, (knot + vec2(0.5f.lit, 0.5f.lit)) / pageSize * pages.grid.z, endpoints = false, coarseLinear = true))
    }
    return mix(mix(taps[0], taps[1], fraction.x), mix(taps[2], taps[3], fraction.x), fraction.y)
}

/** Each slot's layer colour, mixed by its share. Expressions only, so only a debug view pays for them. */
private fun layerWeightColor(slots: List<Slot>): AslExpr {
    val total = max(slots.map { it.weight }.reduce { a, b -> a + b }, EPSILON.lit)
    return slots.map { debugLayerColor(max(it.layer, 0f.lit)) * it.weight }.reduce { a, b -> a + b } / total
}

/** The strongest slot's layer colour; the earlier slot wins a tie. */
private fun dominantLayerColor(slots: List<Slot>): AslExpr {
    var layer = slots.first().layer
    var weight = slots.first().weight
    slots.drop(1).forEach { slot ->
        layer = select(layer, slot.layer, slot.weight gt weight)
        weight = max(weight, slot.weight)
    }
    return debugLayerColor(max(layer, 0f.lit))
}

/** [terrainLayersShader] for both backends, for control maps of [CONTROL_SLOTS]. */
val TerrainLayersShaders = aslShaderSet { terrainLayersShader(it, CONTROL_SLOTS) }

/** [terrainLayersShader] for both backends, for control maps of [MAX_CONTROL_SLOTS]. */
internal val WideTerrainLayersShaders = aslShaderSet { terrainLayersShader(it, MAX_CONTROL_SLOTS) }

/** A merge slot: a palette index (or [NO_LAYER]) and its accumulated weight, both mutable. */
private class Slot(val layer: AslExpr, val weight: AslExpr)

/**
 * The four control texels around this pixel, merged into [slots] slots. A control texel is
 * `slots / 4` RGBA texels side by side.
 */
@Suppress("LongParameterList")
private fun AslBlockBuilder.mergeControlTaps(
    slots: Int,
    position: AslExpr,
    sampling: AslExpr,
    indices: AslExpr,
    weights: AslExpr,
    sampler: AslExpr,
    pages: TerrainPageSampling? = null,
    fallbackIndices: AslExpr? = null,
    fallbackWeights: AslExpr? = null,
): List<Slot> {
    val texelsPerControl = slots / CONTROL_SLOTS
    val size = let("controlSize", textureDimensions(indices))
    val textureWidth = let("controlTextureWidth", toF32(size.x))
    val pageWidth = let("pageControlWidth", textureWidth / texelsPerControl.toFloat().lit)
    val pageDepth = let("pageControlDepth", toF32(size.y))
    val sizeX = let("controlWidth", if (pages == null) pageWidth else pageWidth * pages.dimensions.x)
    val sizeZ = let("controlDepth", if (pages == null) pageDepth else pageDepth * pages.dimensions.y)
    // Control texels align with heightmap samples, whose UV the vertex stage centres the same way.
    val texelX = let("texelX", (if (pages == null) position.x / sampling.x + 0.5f.lit else (position.x - pages.grid.x) / (pages.grid.z * pages.dimensions.x)) * sizeX - 0.5f.lit)
    val texelZ = let("texelZ", (if (pages == null) position.z / sampling.y + 0.5f.lit else (position.z - pages.grid.y) / (pages.grid.z * pages.dimensions.y)) * sizeZ - 0.5f.lit)
    val baseX = let("baseX", floor(texelX))
    val baseZ = let("baseZ", floor(texelZ))
    val fractionX = let("fractionX", texelX - baseX)
    val fractionZ = let("fractionZ", texelZ - baseZ)
    val merged = List(slots) { Slot(variable("slotLayer$it", NO_LAYER.lit), variable("slotWeight$it", 0f.lit)) }
    for (tap in 0 until CONTROL_TAPS) {
        val offsetX = tap % 2
        val offsetZ = tap / 2
        val tapWeight = let(
            "tap${tap}Weight",
            (if (offsetX == 0) 1f.lit - fractionX else fractionX) * (if (offsetZ == 0) 1f.lit - fractionZ else fractionZ),
        )
        val column = let("tap${tap}Column", clamp(baseX + offsetX.toFloat().lit, 0f.lit, sizeX - 1f.lit) * texelsPerControl.toFloat().lit)
        val v = let("tap${tap}V", (clamp(baseZ + offsetZ.toFloat().lit, 0f.lit, sizeZ - 1f.lit) + 0.5f.lit) / sizeZ)
        for (part in 0 until texelsPerControl) {
            val u = let("tap${tap}U$part", (column + (part + 0.5f).lit) / textureWidth)
            val relative = if (pages == null) null else vec2((column / texelsPerControl.toFloat().lit + 0.5f.lit) / pageWidth * pages.grid.z, v * sizeZ / pageDepth * pages.grid.z)
            val layers = let("tap${tap}Layers$part", if (pages != null && relative != null) pages.sample(indices, requireNotNull(fallbackIndices), relative, false, texelsPerControl, part) else textureSampleLevel(indices, sampler, vec2(u, v), 0f.lit))
            val shares = let("tap${tap}Shares$part", if (pages != null && relative != null) pages.sample(weights, requireNotNull(fallbackWeights), relative, false, texelsPerControl, part) else textureSampleLevel(weights, sampler, vec2(u, v), 0f.lit))
            listOf(layers.x to shares.x, layers.y to shares.y, layers.z to shares.z, layers.w to shares.w)
                .forEachIndexed { channel, (layerChannel, shareChannel) ->
                    val slot = part * CONTROL_SLOTS + channel
                    val layer = let("tap${tap}Layer$slot", floor(layerChannel * 255f.lit + 0.5f.lit))
                    val share = let("tap${tap}Share$slot", shareChannel * tapWeight)
                    mergeIntoSlots("tap${tap}Merge$slot", layer, share, merged)
                }
        }
    }
    return merged
}

/** Adds [share] of [layer] to its slot, else an empty one, else replaces the weakest if stronger. */
private fun AslBlockBuilder.mergeIntoSlots(name: String, layer: AslExpr, share: AslExpr, slots: List<Slot>) {
    iff(share gt 0f.lit) {
        val placed = variable("${name}Placed", 0.lit)
        slots.forEach { slot ->
            iff((placed eq 0.lit) and (slot.layer eq layer)) {
                assign(slot.weight, slot.weight + share)
                assign(placed, 1.lit)
            }
        }
        slots.forEach { slot ->
            iff((placed eq 0.lit) and (slot.layer eq NO_LAYER.lit)) {
                assign(slot.layer, layer)
                assign(slot.weight, share)
                assign(placed, 1.lit)
            }
        }
        iff(placed eq 0.lit) {
            val weakest = variable("${name}Weakest", 0.lit)
            val weakestWeight = variable("${name}WeakestWeight", slots.first().weight)
            slots.forEachIndexed { index, slot ->
                if (index > 0) {
                    iff(slot.weight lt weakestWeight) {
                        assign(weakestWeight, slot.weight)
                        assign(weakest, index.lit)
                    }
                }
            }
            iff(share gt weakestWeight) {
                slots.forEachIndexed { index, slot ->
                    iff(weakest eq index.lit) {
                        assign(slot.layer, layer)
                        assign(slot.weight, share)
                    }
                }
            }
        }
    }
}

/**
 * Blends the merged slots. A linear blend by weight share, and a height blend where each layer
 * stands at its share plus its height and only layers within [BLEND_DEPTH] of the highest show,
 * are mixed by the slots' share-weighted sharpness: 0 is purely linear, 1 purely height-driven.
 * An empty slot has no share, so it adds nothing to either.
 */
private fun AslBlockBuilder.blendLayers(
    slots: List<Slot>,
    position: AslExpr,
    albedoLayers: AslExpr,
    layerParams: AslExpr,
    sampler: AslExpr,
): AslExpr {
    val tableSize = let("layerTableSize", toF32(textureDimensions(layerParams).x))
    val total = let("slotTotal", max(slots.map { it.weight }.reduce { a, b -> a + b }, EPSILON.lit))
    val colours = mutableListOf<AslExpr>()
    val shares = mutableListOf<AslExpr>()
    val sharpness = mutableListOf<AslExpr>()
    val heights = slots.mapIndexed { index, slot ->
        val layer = let("layer$index", max(slot.layer, 0f.lit))
        val params = let("layerParams$index", textureSampleLevel(layerParams, sampler, vec2((layer + 0.5f.lit) / tableSize, 0.5f.lit), 0f.lit))
        // Inverse of encodeTiling: 2^(byte / 255 * 8 - 2) world units per repeat.
        val tiling = let("tiling$index", pow(2f.lit, params.x * 8f.lit - 2f.lit))
        val albedo = let(
            "albedo$index",
            textureSampleArray(albedoLayers, sampler, vec2(position.x / tiling, position.z / tiling), toU32(layer)),
        )
        colours += albedo.xyz
        val share = let("share$index", slot.weight / total)
        shares += share
        sharpness += share * params.y
        let("height$index", select(NO_LAYER.lit, share + albedo.w, share gt 0f.lit))
    }
    val ceiling = let("blendCeiling", heights.reduce { a, b -> max(a, b) } - BLEND_DEPTH.lit)
    val blends = heights.mapIndexed { index, height -> let("blend$index", max(height - ceiling, 0f.lit)) }
    val blendTotal = let("blendTotal", max(blends.reduce { a, b -> a + b }, EPSILON.lit))
    val byHeight = let("byHeight", colours.zip(blends).map { (colour, blend) -> colour * blend }.reduce { a, b -> a + b } / blendTotal)
    val byWeight = let("byWeight", colours.zip(shares).map { (colour, share) -> colour * share }.reduce { a, b -> a + b })
    return let("albedo", mix(byWeight, byHeight, sharpness.reduce { a, b -> a + b }))
}
