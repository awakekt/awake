/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.DirectionalShadowBox
import com.awakekt.awake.render.renderer.ShadowCascadeUniforms
import com.awakekt.awake.render.renderer.cascadeShadowBoxes
import com.awakekt.awake.render.renderer.cascadeSplitDistances
import com.awakekt.awake.render.renderer.shadowCascadeUniforms
import kotlin.math.max

const val DEFAULT_SHADOW_CASCADES = 3

fun shadowCascadeUniforms(
    light: SceneLight,
    camera: Lens,
    aspect: Float,
    clipSpace: ClipSpace,
    cascadeCount: Int = DEFAULT_SHADOW_CASCADES,
): ShadowCascadeUniforms {
    val legacyLight = com.awakekt.awake.render.renderer.SceneLight(
        direction = light.direction,
        color = light.color,
    )
    return shadowCascadeUniforms(
        legacyLight,
        camera,
        aspect,
        clipSpace,
        cascadeCount,
    )
}
