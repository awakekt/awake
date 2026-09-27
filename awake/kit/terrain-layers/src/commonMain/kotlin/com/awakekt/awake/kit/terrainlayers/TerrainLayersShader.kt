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
import com.awakekt.awake.asset.shaderpack.TERRAIN_SURFACE_FIRST_BINDING
import com.awakekt.awake.asset.shaderpack.terrainClipmapVertexStage
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic

/** The palette's albedo images, one array layer each, height in alpha. See [packLayerArray]. */
const val LAYER_ALBEDO_BINDING: Int = TERRAIN_SURFACE_FIRST_BINDING

/** Per-layer tiling and sharpness. See [layerTable]. */
const val LAYER_TABLE_BINDING: Int = TERRAIN_SURFACE_FIRST_BINDING + 1

const val CONTROL_INDICES_BINDING: Int = TERRAIN_SURFACE_FIRST_BINDING + 2
const val CONTROL_WEIGHTS_BINDING: Int = TERRAIN_SURFACE_FIRST_BINDING + 3
private const val LAYER_SAMPLER_BINDING = TERRAIN_SURFACE_FIRST_BINDING + 4

/** Baked lighting. See [TerrainLightmap]. */
const val LIGHTMAP_BINDING: Int = TERRAIN_SURFACE_FIRST_BINDING + 5

/** How far below the highest surface another still shows through, in weight-plus-height units. */
private const val BLEND_DEPTH = 0.25f
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
 */
val TerrainLayersShader: AslShaderDefinition = shader("terrain_layers") {
    val terrain = terrainClipmapVertexStage()
    val group = BindingLayout.Standard.slot(BindingSemantic.Material)
    val albedoLayers by texture2dArray(group = group, binding = LAYER_ALBEDO_BINDING)
    val layerParams by texture2d(group = group, binding = LAYER_TABLE_BINDING)
    val controlIndices by texture2d(group = group, binding = CONTROL_INDICES_BINDING)
    val controlWeights by texture2d(group = group, binding = CONTROL_WEIGHTS_BINDING)
    val layerSampler by sampler(group = group, binding = LAYER_SAMPLER_BINDING)
    val lightmap by texture2d(group = group, binding = LIGHTMAP_BINDING)

    fragment {
        val slots = mergeControlTaps(terrain.worldPosition, terrain.terrainSampling, controlIndices, controlWeights, layerSampler)
        val albedo = blendLayers(slots, terrain.worldPosition, albedoLayers, layerParams, layerSampler)
        val normal = let("normal", normalize(terrain.worldNormal))
        val toLight = let("toLight", normalize(terrain.sunDirection.xyz))
        val ambient = let("ambient", terrain.sunDirection.w)
        val diffuse = let("diffuse", max(dot(normal, toLight), 0f.lit))
        val lighting = let("lighting", ambient + (1f.lit - ambient) * diffuse)
        val position = terrain.worldPosition
        val sampling = terrain.terrainSampling
        val baked = let(
            "baked",
            textureSampleLevel(lightmap, layerSampler, vec2(position.x / sampling.x + 0.5f.lit, position.z / sampling.y + 0.5f.lit), 0f.lit),
        )
        // 128 in the lightmap is x1; its alpha hands lighting over from the sun to the bake.
        val light = let("light", mix(lighting, 1f.lit, baked.w))
        colorOutput(vec4(albedo * baked.xyz * 2f.lit * light, 1f.lit))
    }
}

/** [TerrainLayersShader] for both backends. */
val TerrainLayersShaders = aslShaderSet(TerrainLayersShader)

/** A merge slot: a palette index (or [NO_LAYER]) and its accumulated weight, both mutable. */
private class Slot(val layer: AslExpr, val weight: AslExpr)

/** The four control texels around this pixel, merged into [CONTROL_SLOTS] slots. */
private fun AslBlockBuilder.mergeControlTaps(
    position: AslExpr,
    sampling: AslExpr,
    indices: AslExpr,
    weights: AslExpr,
    sampler: AslExpr,
): List<Slot> {
    val size = let("controlSize", textureDimensions(indices))
    val sizeX = let("controlWidth", toF32(size.x))
    val sizeZ = let("controlDepth", toF32(size.y))
    // Control texels align with heightmap samples, whose UV the vertex stage centres the same way.
    val texelX = let("texelX", (position.x / sampling.x + 0.5f.lit) * sizeX - 0.5f.lit)
    val texelZ = let("texelZ", (position.z / sampling.y + 0.5f.lit) * sizeZ - 0.5f.lit)
    val baseX = let("baseX", floor(texelX))
    val baseZ = let("baseZ", floor(texelZ))
    val fractionX = let("fractionX", texelX - baseX)
    val fractionZ = let("fractionZ", texelZ - baseZ)
    val slots = List(CONTROL_SLOTS) { Slot(variable("slotLayer$it", NO_LAYER.lit), variable("slotWeight$it", 0f.lit)) }
    for (tap in 0 until CONTROL_TAPS) {
        val offsetX = tap % 2
        val offsetZ = tap / 2
        val tapWeight = let(
            "tap${tap}Weight",
            (if (offsetX == 0) 1f.lit - fractionX else fractionX) * (if (offsetZ == 0) 1f.lit - fractionZ else fractionZ),
        )
        val u = let("tap${tap}U", (clamp(baseX + offsetX.toFloat().lit, 0f.lit, sizeX - 1f.lit) + 0.5f.lit) / sizeX)
        val v = let("tap${tap}V", (clamp(baseZ + offsetZ.toFloat().lit, 0f.lit, sizeZ - 1f.lit) + 0.5f.lit) / sizeZ)
        val layers = let("tap${tap}Layers", textureSampleLevel(indices, sampler, vec2(u, v), 0f.lit))
        val shares = let("tap${tap}Shares", textureSampleLevel(weights, sampler, vec2(u, v), 0f.lit))
        listOf(layers.x to shares.x, layers.y to shares.y, layers.z to shares.z, layers.w to shares.w)
            .forEachIndexed { channel, (layerChannel, shareChannel) ->
                val layer = let("tap${tap}Layer$channel", floor(layerChannel * 255f.lit + 0.5f.lit))
                val share = let("tap${tap}Share$channel", shareChannel * tapWeight)
                mergeIntoSlots("tap${tap}Merge$channel", layer, share, slots)
            }
    }
    return slots
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
