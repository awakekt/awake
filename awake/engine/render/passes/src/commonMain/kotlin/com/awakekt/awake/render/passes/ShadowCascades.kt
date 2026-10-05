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

/**
 * Computes [ShadowCascadeUniforms] for a directional [light] and [camera] lens.
 *
 * @param light The scene light driving the shadow cascades.
 * @param camera Camera lens providing the view and projection frustum.
 * @param aspect Viewport aspect ratio.
 * @param clipSpace Target graphics API clip space coordinates.
 * @param cascadeCount Number of shadow cascades to partition the frustum into (default [DEFAULT_SHADOW_CASCADES]).
 * @return Computed [ShadowCascadeUniforms] containing cascade splits and view-projection matrices.
 */
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
