// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.passes.uniforms

import io.github.ronjunevaldoz.awake.render.renderer.UniformFields
import io.github.ronjunevaldoz.awake.render.renderer.UniformLayout

/**
 * The `Uniforms` struct each lit shader declares, as typed fields.
 *
 * Every buffer size in the render path derives from one of these rather than a hand-summed
 * literal -- adding a field resizes the buffer, the material and the packer together. Field
 * ORDER is load-bearing: it is exactly the concatenation order the packers below emit and the
 * shader reads.
 */
object MaterialUniformLayouts {
    /** The primary lit path -- exactly what `triangle.wgsl` reads: MVP + lightDirection +
     * lightColor = 24 floats. Both light halves are `vec4f` rather than `vec3f` to sidestep
     * std140's vec3 padding; see [sceneLightFloats]. */
    val Primary = UniformLayout(
        UniformFields.Mvp,
        UniformFields.LightDirection,
        UniformFields.LightColor,
    )

    /** Untextured lit: [Primary] plus PBR factors = 28 floats. */
    val Lit = UniformLayout(
        UniformFields.Mvp,
        UniformFields.LightDirection,
        UniformFields.LightColor,
        UniformFields.PbrFactors,
    )

    /** Everything `lit_shadow.wgsl` reads after its MVP. The renderer prepends the MVP itself,
     * so this is the block a draw call contributes: 52 floats. */
    val LitShadowExtra = UniformLayout(
        UniformFields.LightDirection,
        UniformFields.LightColor,
        UniformFields.PointLightPositions,
        UniformFields.PointLightColors,
        UniformFields.LightMvp,
        UniformFields.Model,
        UniformFields.CameraPosition,
        UniformFields.PbrFactors,
        UniformFields.FogColor,
    )

    /** Everything `textured.wgsl` reads after its MVP -- see [LitShadowExtra]: 44 floats. */
    val TexturedExtra = UniformLayout(
        UniformFields.LightDirection,
        UniformFields.LightColor,
        UniformFields.PointLightPositions,
        UniformFields.PointLightColors,
        UniformFields.Model,
        UniformFields.CameraPosition,
        UniformFields.PbrFactors,
        UniformFields.BaseColorFactor,
        UniformFields.EmissiveFactor,
        UniformFields.FogColor,
    )

    /** Full textured glTF PBR = 60 floats. */
    val PbrTextured = UniformLayout(
        UniformFields.Mvp,
        UniformFields.LightDirection,
        UniformFields.LightColor,
        UniformFields.PointLightPositions,
        UniformFields.PointLightColors,
        UniformFields.Model,
        UniformFields.CameraPosition,
        UniformFields.PbrFactors,
        UniformFields.BaseColorFactor,
        UniformFields.EmissiveFactor,
        UniformFields.FogColor,
    )
}

/** The material block of [MaterialUniformLayouts.Lit] -- see [pbrMaterialFloats]. */
val PBR_MATERIAL_FLOATS: Int = UniformFields.PbrFactors.floats

/** The material block of [MaterialUniformLayouts.PbrTextured] -- see [pbrTexturedMaterialFloats]. */
val PBR_TEXTURED_MATERIAL_FLOATS: Int =
    UniformFields.PbrFactors.floats + UniformFields.BaseColorFactor.floats + UniformFields.EmissiveFactor.floats
