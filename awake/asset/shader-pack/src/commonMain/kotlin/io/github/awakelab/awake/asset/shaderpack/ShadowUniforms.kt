/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.asset.shaderdsl.AslArrayHandle
import io.github.awakelab.awake.asset.shaderdsl.AslExpr
import io.github.awakelab.awake.asset.shaderdsl.AslShaderBuilder
import io.github.awakelab.awake.asset.shaderdsl.fieldsFrom
import io.github.awakelab.awake.render.passes.uniforms.MaterialUniformLayouts

/**
 * The shadow pair's uniform struct handles. shadow_depth binds the SAME per-draw uniform
 * buffer lit_shadow writes, so its struct must be a field-for-field prefix of lit_shadow's --
 * the drift that once put lightMvp at float offset 24 instead of 56 and wrote an empty shadow
 * map.
 *
 * Every name, shape, and array count here comes from [MaterialUniformLayouts.LitShadow] via
 * `fieldsFrom` -- the same layout the renderer packs and the material is sized from. There is
 * no ASL-side field list to keep in step: rename or resize a layout field and both shader
 * definitions fail to build until their bodies catch up.
 */
@Suppress("LongParameterList") // Pure aggregation of the derived field handles.
class ShadowUniforms internal constructor(
    val mvp: AslExpr,
    val lightDirection: AslExpr,
    val lightColor: AslExpr,
    /** xyz = world position, w = range; w <= 0 means the slot is off. */
    val pointLightPositions: AslArrayHandle,
    val pointLightColors: AslArrayHandle,
    val lightMvp: AslExpr,
    /** xyz = vertex-effect parameters; w = current frame time in seconds. */
    val vertexAnimation: AslExpr,
    /** Everything below exists only in lit_shadow; shadow_depth stops after vertexAnimation. */
    val model: AslExpr?,
    val cameraPosition: AslExpr?,
    /** x = metallic, y = roughness. */
    val material: AslExpr?,
    /** rgb = fog colour, a = fog DENSITY (not alpha); density 0 makes applyFog a no-op. */
    val fogColor: AslExpr?,
)

fun AslShaderBuilder.shadowUniforms(includeLitTail: Boolean): ShadowUniforms {
    val block = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = block.fieldsFrom(
        MaterialUniformLayouts.LitShadow,
        throughField = if (includeLitTail) null else "vertexAnimation",
    )
    return ShadowUniforms(
        mvp = handles.value("mvp"),
        lightDirection = handles.value("lightDirection"),
        lightColor = handles.value("lightColor"),
        pointLightPositions = handles.array("pointLightPositions"),
        pointLightColors = handles.array("pointLightColors"),
        lightMvp = handles.value("lightMvp"),
        vertexAnimation = handles.value("vertexAnimation"),
        model = if (includeLitTail) handles.value("model") else null,
        cameraPosition = if (includeLitTail) handles.value("cameraPosition") else null,
        material = if (includeLitTail) handles.value("material") else null,
        fogColor = if (includeLitTail) handles.value("fogColor") else null,
    )
}
