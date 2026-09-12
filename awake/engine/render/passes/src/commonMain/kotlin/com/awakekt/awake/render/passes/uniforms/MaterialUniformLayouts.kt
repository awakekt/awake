/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformLayout

/**
 * The `Uniforms` struct each lit shader declares, as typed fields.
 *
 * Every buffer size in the render path derives from one of these rather than a hand-summed
 * literal -- adding a field resizes the buffer, the material and the packer together. Field
 * ORDER is load-bearing: it is exactly the concatenation order the packers below emit and the
 * shader reads.
 */
object MaterialUniformLayouts {
    /** Directional light payload used as the source block before it is embedded in a material. */
    val DirectionalLight = UniformLayout(
        UniformFields.LightDirection,
        UniformFields.LightColor,
    )

    /** Point-light source payload: positions first, then colours, exactly as the shader fields. */
    val PointLightSlots = UniformLayout(
        UniformFields.PointLightPositions,
        UniformFields.PointLightColors,
    )

    /** Complete scene-light payload carried between the scene compiler and a backend adapter. */
    val SceneLight = UniformLayout(
        UniformFields.LightDirection,
        UniformFields.LightColor,
        UniformFields.PointLightPositions,
        UniformFields.PointLightColors,
    )

    /** The compact PBR material payload carried by a draw command. */
    val PbrMaterial = UniformLayout(UniformFields.PbrFactors)

    /** The compact textured PBR payload carried by a draw command. */
    val PbrTexturedMaterial = UniformLayout(
        UniformFields.PbrFactors,
        UniformFields.BaseColorFactor,
        UniformFields.EmissiveFactor,
    )

    /** A standalone camera position field used by source packers. */
    val CameraPosition = UniformLayout(UniformFields.CameraPosition)

    /** A standalone fog field used by source packers. */
    val Fog = UniformLayout(UniformFields.FogColor)

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

    /**
     * Everything `lit_shadow.wgsl` declares = 104 floats.
     *
     * `awake:asset:shader-pack` re-exports this as `LitShadowUniformLayout` rather than
     * declaring its own. It used to declare one, so the struct had two Kotlin descriptions --
     * this one sizing what the renderer writes, that one sizing the material and checked against
     * the WGSL by `ShaderUniformStructTest`. They agreed by hand, and only one of them was
     * tested.
     */
    val LitShadow = UniformLayout(
        UniformFields.Mvp,
        UniformFields.LightDirection,
        UniformFields.LightColor,
        UniformFields.PointLightPositions,
        UniformFields.PointLightColors,
        UniformFields.CascadeViewProjections,
        UniformFields.CascadeDepthScales,
        // Ahead of vertexAnimation so shadow_depth's prefix reaches it: that pass now builds its
        // own clip position from model and the cascade it is rendering, rather than reading a
        // per-draw matrix that could only ever describe one cascade.
        UniformFields.Model,
        UniformFields.VertexAnimation,
        UniformFields.CameraPosition,
        UniformFields.Material,
        UniformFields.FogColor,
    )

    /** Everything `textured.wgsl` reads after its MVP -- see [LitShadow]: 44 floats. */
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
