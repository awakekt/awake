/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.passes.DEFAULT_SHADOW_CASCADES
import com.awakekt.awake.render.passes.DEFAULT_SHADOW_DISTANCE
import com.awakekt.awake.render.passes.cascadeShadowBoxes
import com.awakekt.awake.render.passes.cascadeSplitDistances
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformLayout
import kotlin.math.abs
typealias ShadowCascadeUniforms = GpuShadowCascadeData

/**
 * The cascade set for [light] seen through [camera], or null when the light casts no shadow.
 *
 * Null rather than a single fixed box, so a caller can tell "this light has no shadow pass" from
 * "this light has one cascade": the first skips the depth pass entirely.
 */
fun shadowCascadeUniforms(
    light: SceneLight,
    camera: com.awakekt.awake.core.math.Lens,
    aspect: Float,
    clipSpace: com.awakekt.awake.core.math.ClipSpace,
    count: Int = DEFAULT_SHADOW_CASCADES,
    shadowDistance: Float = DEFAULT_SHADOW_DISTANCE,
): ShadowCascadeUniforms {
    // Cascades stop at the shadow distance, not at the camera's far plane. Fitting to the far
    // plane spends the whole map on ground nobody can resolve: a 1000m view gave a 5m-wide scene
    // cascades 329m, 676m and 2338m across -- texels of 16cm to 114cm, on which a 2m box is six
    // texels wide and every artefact downstream of that is unfixable by bias.
    val splits = cascadeSplitDistances(camera.near, minOf(camera.far, shadowDistance), count)
    val boxes = cascadeShadowBoxes(camera, aspect, light.direction, clipSpace, splits)
    return ShadowCascadeUniforms(
        boxes.map { it.viewProjection },
        splits,
        // |m22| IS ndc-depth-per-world-unit for an orthographic box (see Mat4.orthographic),
        // so the shader gets the conversion without being told the near and far it came from.
        FloatArray(boxes.size) { abs(boxes[it].projection.m22) },
        // 2/m00 is the box's world width, for the same reason: an ortho projection maps that
        // width onto -1..1.
        FloatArray(boxes.size) { 2f / abs(boxes[it].projection.m00) },
    )
}

/**
 * The depth pass's own tiny block: which cascade it is rendering right now.
 *
 * Its own layout, not a slice of the material's, because it changes BETWEEN draws of the same
 * material -- the pass draws every mesh once per cascade.
 */
val CascadePassUniformLayout = UniformLayout(UniformFields.CascadeViewProjection)

/**
 * Which bind group the depth pass's cascade block sits at.
 *
 * In the contract rather than beside the shader that declares it, because the backend that binds
 * the set and the shader that reads it are in modules that cannot see each other -- and a
 * mismatch between them is a validation error naming a set index, not a shader.
 */
val SHADOW_CASCADE_PASS_GROUP: Int =
    com.awakekt.awake.render.pipeline.BindingLayout.Standard.slot(
        com.awakekt.awake.render.pipeline.ShadowCascadePassBinding,
    )

/**
 * A cascade set that shadows nothing, for a shader that declares the block while shadows are off.
 *
 * A pipeline's uniform block is decided by its SHADER, not by whether shadows happen to be
 * enabled: `lit_shadow` reads material, camera position, fog and cascades whatever the renderer's
 * `shadowsEnabled` says, so handing it a shorter block leaves those fields reading whatever was
 * in the buffer. That renders as a scene which gets DARKER when shadows are turned off.
 *
 * The matrices are zero, so every fragment's `projected.w` is zero and the shader's own
 * containment test rejects every cascade -- the same path a fragment beyond the last cascade
 * takes. The scales are ones rather than derived, because a zero matrix has no scale to derive
 * and an infinite texel would reach the arithmetic before the rejection does.
 */
val UNSHADOWED_CASCADES: ShadowCascadeUniforms = GpuShadowCascadeData.UNSHADOWED
