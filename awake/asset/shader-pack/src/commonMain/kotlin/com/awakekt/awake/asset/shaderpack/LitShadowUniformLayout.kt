/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts

/**
 * `lit_shadow.wgsl`'s `Uniforms` struct, field for field: mvp, light direction/colour, point
 * light slots, lightMvp, vertex animation, model, camera position, metallic/roughness `material`, fog.
 *
 * Re-exported from `render:passes` rather than declared here. The two used to be separate
 * declarations of one struct -- that one sizing what a renderer writes into the buffer, this one
 * sizing the material and checked against the WGSL by `ShaderUniformStructTest`. They agreed
 * only because someone kept them in step, and only this one was tested, so a field added to the
 * other would have gone unnoticed in exactly the way this shader's point lights already did.
 *
 * The alias stays because the name is what a consumer reads: `createMaterial(LitShadowUniformLayout)`
 * says which shader the material is for, where the layout's home says which layer owns it.
 */
val LitShadowUniformLayout = MaterialUniformLayouts.LitShadow
