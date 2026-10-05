/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.geometry.VertexFormat

/**
 * Selects the shared per-draw ABI from packet metadata.
 *
 * Backends still own buffer allocation and native binding creation, but they must not each repeat
 * this format/material policy. Keeping the decision here makes a new backend use the same shader
 * ABI and keeps the order of the special cases explicit: shadowed lit, skinned, textured PBR,
 * then the ordinary lit fallback.
 */
enum class DrawUniformPlan {
    /** Lit shading receiving directional shadow cascades. */
    LitShadow,

    /** Skeletally animated / vertex-blended joint uniform plan. */
    Skinned,

    /** Full physically-based rendering with material texture maps. */
    TexturedPbr,

    /** Baseline unshadowed lit shading. */
    Lit,
}

/**
 * Resolves the [DrawUniformPlan] given the mesh vertex [format], the size of the material uniform buffer
 * in floats ([materialUniformFloatCount]), and whether shadow cascades are available ([hasShadowCascades]).
 */
fun drawUniformPlan(
    format: VertexFormat,
    materialUniformFloatCount: Int,
    hasShadowCascades: Boolean,
): DrawUniformPlan = when {
    // Exact, and first: the textured block is larger than lit_shadow's, so "at least" cannot tell them apart.
    format == VertexFormat.PositionNormalColorUv &&
        materialUniformFloatCount == MaterialUniformLayouts.PbrTextured.total ->
        DrawUniformPlan.TexturedPbr

    materialUniformFloatCount >= MaterialUniformLayouts.LitShadow.total &&
        format in LIT_SHADOW_FORMATS ->
        DrawUniformPlan.LitShadow

    format == VertexFormat.PositionNormalColorSkin ||
        format == VertexFormat.PositionNormalColorUvSkin ->
        DrawUniformPlan.Skinned

    else -> DrawUniformPlan.Lit
}

/** Built once: this check runs per draw per frame. */
private val LIT_SHADOW_FORMATS = setOf(VertexFormat.PositionNormalColor, VertexFormat.PositionNormalColorUv)
