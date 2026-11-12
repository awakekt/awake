/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslArrayHandle
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslLayoutHandles
import com.awakekt.awake.asset.shaderdsl.AslShaderBuilder
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.FRAME_UNIFORM_BINDING

/**
 * The shadow pair's uniform handles: the draw's own block, and the frame block beside it.
 *
 * shadow_depth binds the SAME per-draw uniform buffer lit_shadow writes, so its struct must be a
 * field-for-field prefix of lit_shadow's -- the drift that once put lightMvp at float offset 24
 * instead of 56 and wrote an empty shadow map. That prefix is `model` and `vertexAnimation`.
 *
 * Every name, shape, and array count here comes from [MaterialUniformLayouts.LitShadowDraw] and
 * [MaterialUniformLayouts.Frame] via `fieldsFrom` -- the layouts the renderer packs. There is no
 * ASL-side field list to keep in step: rename or resize a layout field and both shader
 * definitions fail to build until their bodies catch up.
 *
 * What the camera, the light and the cascades decide is the frame block's, which a pass writes
 * once; a shader that reads none of it declares it and binds nothing for it.
 */
@Suppress("LongParameterList") // Pure aggregation of the derived field handles.
class ShadowUniforms internal constructor(
    /** The frame's view-projection times this draw's [model]; an instanced shader multiplies
     * [viewProjection] by its instance's matrix instead. */
    val mvp: AslExpr,
    /** World space to the pass's clip space, from the frame block. */
    val viewProjection: AslExpr,
    /** Primary directional light vector handle. */
    val lightDirection: AslExpr,
    /** Primary directional light color and intensity handle. */
    val lightColor: AslExpr,
    /** xyz = world position, w = range; w <= 0 means the slot is off. */
    val pointLightPositions: AslArrayHandle,
    /** Point light color and intensity array handle. */
    val pointLightColors: AslArrayHandle,
    /**
     * World space to each cascade's light clip space, near cascade first.
     *
     * Read by lit_shadow to choose and sample a cascade. shadow_depth declares it (a struct
     * prefix has to match field for field) and ignores it: which cascade IT renders comes from
     * its own pass-scoped block, since that changes between draws of one material.
     */
    val cascadeViewProjections: AslArrayHandle,
    /** Per cascade, NDC depth per world unit -- what turns a world-space bias into this
     * cascade's depth units. See `UniformFields.CascadeDepthScales`. */
    val cascadeDepthScales: AslArrayHandle,
    /** In the shared prefix: shadow_depth builds its clip position from this and the cascade it
     * is rendering, rather than from a per-draw matrix that could describe only one cascade. */
    val model: AslExpr,
    /** xyz = vertex-effect parameters; w = current frame time in seconds. */
    val vertexAnimation: AslExpr,
    /** Everything below exists only in lit_shadow; shadow_depth stops after vertexAnimation. */
    val cameraPosition: AslExpr?,
    /** xyz = normalized camera forward, w = active cascade count. */
    val cameraForward: AslExpr?,
    /** x = metallic, y = roughness. */
    val material: AslExpr?,
    /** rgb = fog colour, a = fog DENSITY (not alpha); density 0 makes applyFog a no-op. */
    val fogColor: AslExpr?,
    /** `UniformFields.DebugView`; see [debugViewColor]. */
    val debugView: AslExpr?,
    /** `x` = the exposure [SceneDisplayTransform.display] applies, from the frame block; `y` =
     * this draw's dither fade, from its own. */
    val exposure: AslExpr?,
)

/**
 * Declares and binds standard shadow and lighting uniform fields for the current shader.
 *
 * @param includeLitTail When true, includes additional lighting, fog, and tone mapping uniform fields.
 * @return Aggregated [ShadowUniforms] handle object.
 */
fun AslShaderBuilder.shadowUniforms(includeLitTail: Boolean): ShadowUniforms {
    val group = BindingLayout.Standard.slot(BindingSemantic.Material)
    val draw = uniformBlock("Uniforms", group = group, binding = 0).fieldsFrom(
        MaterialUniformLayouts.LitShadowDraw,
        throughField = if (includeLitTail) null else "vertexAnimation",
    )
    val frame = frameUniforms()
    val viewProjection = frame.value("viewProjection")
    return ShadowUniforms(
        mvp = viewProjection * draw.value("model"),
        viewProjection = viewProjection,
        lightDirection = frame.value("lightDirection"),
        lightColor = frame.value("lightColor"),
        pointLightPositions = frame.array("pointLightPositions"),
        pointLightColors = frame.array("pointLightColors"),
        cascadeViewProjections = frame.array("cascadeViewProjections"),
        cascadeDepthScales = frame.array("cascadeDepthScales"),
        model = draw.value("model"),
        vertexAnimation = draw.value("vertexAnimation"),
        cameraPosition = if (includeLitTail) frame.value("cameraPosition") else null,
        cameraForward = if (includeLitTail) frame.value("cameraForward") else null,
        material = if (includeLitTail) draw.value("material") else null,
        fogColor = if (includeLitTail) frame.value("fogColor") else null,
        debugView = if (includeLitTail) frame.value("debugView") else null,
        exposure = if (includeLitTail) exposureAndFade(frame, draw) else null,
    )
}

/**
 * Declares the frame block, [MaterialUniformLayouts.Frame], beside the draw's block in the
 * material group. A pipeline's layout holds it only when a stage reads one of its fields.
 */
internal fun AslShaderBuilder.frameUniforms(): AslLayoutHandles = uniformBlock(
    "FrameUniforms",
    group = BindingLayout.Standard.slot(BindingSemantic.Material),
    binding = FRAME_UNIFORM_BINDING,
).fieldsFrom(MaterialUniformLayouts.Frame)

/** `x` = the frame's exposure, `y` = the draw's dither fade: what a block's `exposure` field held. */
internal fun exposureAndFade(frame: AslLayoutHandles, draw: AslLayoutHandles): AslExpr =
    vec4(frame.value("exposure").x, draw.value("drawFade").x, 0f.lit, 0f.lit)
