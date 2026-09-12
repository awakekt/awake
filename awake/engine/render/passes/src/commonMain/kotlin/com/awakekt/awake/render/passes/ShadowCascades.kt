/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.render.passes.DirectionalShadowBox
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.ShadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.shadowCascadeUniforms

fun shadowCascadeUniforms(
    light: SceneLight,
    camera: Lens,
    aspect: Float,
    clipSpace: ClipSpace,
    cascadeCount: Int = DEFAULT_SHADOW_CASCADES,
): ShadowCascadeUniforms {
    val legacyLight = com.awakekt.awake.render.passes.uniforms.SceneLight(
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
