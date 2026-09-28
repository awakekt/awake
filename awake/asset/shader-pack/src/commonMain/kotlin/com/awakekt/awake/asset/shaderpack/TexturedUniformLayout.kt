/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts

/** `textured.wgsl`'s `Uniforms` struct, re-exported from `render:passes` for the same reason as
 * [LitShadowUniformLayout]: a second declaration here drifted a field behind the shader once. */
val TexturedUniformLayout = MaterialUniformLayouts.PbrTextured
