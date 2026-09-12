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
    LitShadow,
    Skinned,
    TexturedPbr,
    Lit,
}

fun drawUniformPlan(
    format: VertexFormat,
    materialUniformFloatCount: Int,
    hasShadowCascades: Boolean,
): DrawUniformPlan = when {
    materialUniformFloatCount >= MaterialUniformLayouts.LitShadow.total &&
        format in setOf(VertexFormat.PositionNormalColor, VertexFormat.PositionNormalColorUv) ->
        DrawUniformPlan.LitShadow

    format == VertexFormat.PositionNormalColorSkin ||
        format == VertexFormat.PositionNormalColorUvSkin ->
        DrawUniformPlan.Skinned

    format == VertexFormat.PositionNormalColorUv &&
        materialUniformFloatCount >= MaterialUniformLayouts.PbrTextured.total ->
        DrawUniformPlan.TexturedPbr

    else -> DrawUniformPlan.Lit
}
