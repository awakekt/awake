/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.passes.uniforms.DEFAULT_METALLIC
import com.awakekt.awake.render.passes.uniforms.DEFAULT_ROUGHNESS
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.renderer.SkinnedInstanceLayout
import com.awakekt.awake.render.renderer.SkinnedMaterialLayout
import com.awakekt.awake.render.renderer.UniformFields

/**
 * Automatic batching rules for separate single posed skinned draws (`mesh.format.isSkinned == true`,
 * `instanceModels == null`, `instanceJointPalettes == null`).
 *
 * Single posed skinned draws carry their joint palette (and optional [SkinnedMaterialLayout] tail)
 * in `extraUniformFloats`. When a backend provides a matching skinned-instanced pipeline, copies
 * sharing mesh, material, render state, and PBR metallic/roughness factors fold into one
 * skinned-instanced draw (chunked at [MAX_SKINNED_BATCHED_INSTANCES]), carrying each copy's pose in
 * `instanceJointPalettes` and its `baseColorFactor` RGBA in `instanceColors`.
 *
 * Unlit, emissive, reflective, transparent, or masked skinned draws stay single because the
 * skinned-instanced lit-shadow shader carries no unlit/emissive/alpha-cutoff tail.
 */
internal object SkinnedInstanceFold {
    internal val PALETTE_FLOATS: Int = SkinnedInstanceLayout.PALETTE_FLOATS
    internal val MATERIAL_TOTAL: Int = SkinnedMaterialLayout.total
    private val BASE_COLOR_AT: Int = SkinnedMaterialLayout.offsetOf(UniformFields.BaseColorFactor)
    private val EMISSIVE_AT: Int = SkinnedMaterialLayout.offsetOf(UniformFields.EmissiveFactor)
    private val PBR_FACTORS_AT: Int = SkinnedMaterialLayout.offsetOf(UniformFields.PbrFactors)
    private const val MAT4_FLOATS = 16
    private const val UNLIT_THRESHOLD = 0.5f
    private const val HASH_STEP = 31

    /** Whether [draw] is an opaque single skinned draw whose payload fits the skinned-instanced shader. */
    fun canFold(draw: RenderDrawCommand): Boolean =
        draw.mesh.format.isSkinned &&
            draw.instanceModels == null &&
            draw.instanceJointPalettes == null &&
            !draw.transparent &&
            draw.alphaMode == AlphaMode.Opaque &&
            isFoldablePayload(draw.extraUniformFloats)

    /**
     * Accepts a bare joint palette (`1..MAX_JOINTS` matrices) or a lit, non-emissive
     * [SkinnedMaterialLayout] payload.
     */
    internal fun isFoldablePayload(extras: FloatArray): Boolean = when {
        extras.size in MAT4_FLOATS..PALETTE_FLOATS && (extras.size and (MAT4_FLOATS - 1)) == 0 -> true
        extras.size == MATERIAL_TOTAL ->
            extras[PBR_FACTORS_AT + 3] <= UNLIT_THRESHOLD &&
                extras[EMISSIVE_AT] == 0f &&
                extras[EMISSIVE_AT + 1] == 0f &&
                extras[EMISSIVE_AT + 2] == 0f &&
                extras[EMISSIVE_AT + 3] == 0f
        else -> false
    }

    /**
     * Whether [first] and [second] can share one skinned-instanced draw: identical mesh, material,
     * animation, render state (including `fadeOpacity`, which is per-draw on the skinned-instanced
     * shader), and metallic/roughness factors. Per-instance model, joint palette, and RGBA tint may
     * differ.
     */
    fun alike(first: RenderDrawCommand, second: RenderDrawCommand): Boolean =
        first.mesh === second.mesh &&
            first.material === second.material &&
            first.vertexAnimation.sameAnimation(second.vertexAnimation) &&
            first.timeSeconds == second.timeSeconds &&
            first.sameRenderState(second, tints = false) &&
            metallicOf(first.extraUniformFloats).toRawBits() == metallicOf(second.extraUniformFloats).toRawBits() &&
            roughnessOf(first.extraUniformFloats).toRawBits() == roughnessOf(second.extraUniformFloats).toRawBits()

    /** Hash equal for any two skinned draws accepted by [alike], leaving out per-instance pose and tint. */
    fun hash(draw: RenderDrawCommand): Int {
        val extras = draw.extraUniformFloats
        var h = draw.mesh.hashCode()
        h = h * HASH_STEP + draw.material.hashCode()
        h = h * HASH_STEP + metallicOf(extras).toRawBits()
        h = h * HASH_STEP + roughnessOf(extras).toRawBits()
        return h * HASH_STEP + draw.cullMode.hashCode()
    }

    internal fun metallicOf(extras: FloatArray): Float =
        if (extras.size == MATERIAL_TOTAL) extras[PBR_FACTORS_AT] else DEFAULT_METALLIC

    internal fun roughnessOf(extras: FloatArray): Float =
        if (extras.size == MATERIAL_TOTAL) extras[PBR_FACTORS_AT + 1] else DEFAULT_ROUGHNESS

    internal fun hasNonWhiteTint(extras: FloatArray): Boolean {
        if (extras.size != MATERIAL_TOTAL) return false
        return extras[BASE_COLOR_AT] != 1f ||
            extras[BASE_COLOR_AT + 1] != 1f ||
            extras[BASE_COLOR_AT + 2] != 1f ||
            extras[BASE_COLOR_AT + 3] != 1f
    }

    internal fun writeTint(extras: FloatArray, into: Vec4) {
        if (extras.size == MATERIAL_TOTAL) {
            into.x = extras[BASE_COLOR_AT]
            into.y = extras[BASE_COLOR_AT + 1]
            into.z = extras[BASE_COLOR_AT + 2]
            into.w = extras[BASE_COLOR_AT + 3]
        } else {
            into.x = 1f
            into.y = 1f
            into.z = 1f
            into.w = 1f
        }
    }
}

/**
 * Reusable per-group skinned batching storage kept across frames so folding posed skinned draws
 * allocates nothing once warm.
 */
internal class GroupSkinnedData {
    private val palettes = ArrayList<FloatArray>()
    private val palettePool = ArrayList<FloatArray>()
    private val colors = ArrayList<Vec4>()
    private val colorPool = ArrayList<Vec4>()
    private val sharedMaterial = FloatArray(SHARED_MATERIAL_FLOATS)

    /**
     * Extracts one joint palette (<= [SkinnedInstanceFold.PALETTE_FLOATS] floats) per member.
     * Bare palettes are referenced directly; [SkinnedMaterialLayout] payloads are sliced into
     * pooled arrays.
     */
    fun palettesOf(members: List<RenderDrawCommand>): List<FloatArray> {
        palettes.clear()
        var pooledCount = 0
        for (index in 0 until members.size) {
            val extras = members[index].extraUniformFloats
            if (extras.size <= SkinnedInstanceFold.PALETTE_FLOATS) {
                palettes += extras
            } else {
                if (pooledCount == palettePool.size) {
                    palettePool += FloatArray(SkinnedInstanceFold.PALETTE_FLOATS)
                }
                val slice = palettePool[pooledCount++]
                extras.copyInto(slice, destinationOffset = 0, startIndex = 0, endIndex = SkinnedInstanceFold.PALETTE_FLOATS)
                palettes += slice
            }
        }
        return palettes
    }

    /**
     * Returns per-instance RGBA tints when any member carries a non-white `baseColorFactor`, or
     * `null` when every member is opaque white.
     */
    fun colorsOf(members: List<RenderDrawCommand>): List<Vec4>? {
        var anyNonWhite = false
        for (index in 0 until members.size) {
            if (SkinnedInstanceFold.hasNonWhiteTint(members[index].extraUniformFloats)) {
                anyNonWhite = true
                break
            }
        }
        if (!anyNonWhite) return null
        colors.clear()
        for (index in 0 until members.size) {
            if (index == colorPool.size) colorPool += Vec4()
            val color = colorPool[index]
            SkinnedInstanceFold.writeTint(members[index].extraUniformFloats, color)
            colors += color
        }
        return colors
    }

    /** Shared non-palette material floats (`[metallic, roughness, 0, 0]` or empty) for the group's draw block. */
    fun sharedExtras(first: RenderDrawCommand): FloatArray {
        val extras = first.extraUniformFloats
        if (extras.size != SkinnedInstanceFold.MATERIAL_TOTAL) return EMPTY_EXTRAS
        sharedMaterial[0] = SkinnedInstanceFold.metallicOf(extras)
        sharedMaterial[1] = SkinnedInstanceFold.roughnessOf(extras)
        sharedMaterial[2] = 0f
        sharedMaterial[3] = 0f
        return sharedMaterial
    }

    private companion object {
        const val SHARED_MATERIAL_FLOATS = 4
        val EMPTY_EXTRAS = FloatArray(0)
    }
}
