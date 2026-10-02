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
        UniformFields.TextureFrames,
        UniformFields.TextureScroll,
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
     * What `shadow_depth` reads from any draw's block, `model` and `vertexAnimation` included.
     * Every layout the shadow pass draws starts with these fields, in this order.
     */
    private val SHADOW_DEPTH_PREFIX = arrayOf(
        UniformFields.Mvp,
        UniformFields.LightDirection,
        UniformFields.LightColor,
        UniformFields.PointLightPositions,
        UniformFields.PointLightColors,
        UniformFields.CascadeViewProjections,
        UniformFields.CascadeDepthScales,
        UniformFields.Model,
        UniformFields.VertexAnimation,
    )

    /**
     * Everything `lit_shadow.wgsl` declares = 180 floats.
     *
     * `awake:asset:shader-pack` re-exports this as `LitShadowUniformLayout` rather than
     * declaring its own. It used to declare one, so the struct had two Kotlin descriptions --
     * this one sizing what the renderer writes, that one sizing the material and checked against
     * the WGSL by `ShaderUniformStructTest`. They agreed by hand, and only one of them was
     * tested.
     */
    @Suppress("SpreadOperator") // Once, at class initialisation.
    val LitShadow = UniformLayout(
        *SHADOW_DEPTH_PREFIX,
        UniformFields.CameraPosition,
        UniformFields.CameraForward,
        UniformFields.Material,
        UniformFields.FogColor,
        UniformFields.DebugView,
        UniformFields.Exposure,
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
        UniformFields.TextureFrames,
        UniformFields.TextureScroll,
        UniformFields.FogColor,
    )

    /**
     * Full textured glTF PBR = 196 floats. Starts with [SHADOW_DEPTH_PREFIX], because the shadow
     * pass draws textured meshes with the same depth shader as lit ones.
     */
    @Suppress("SpreadOperator") // Once, at class initialisation.
    val PbrTextured = UniformLayout(
        *SHADOW_DEPTH_PREFIX,
        UniformFields.CameraPosition,
        UniformFields.PbrFactors,
        UniformFields.BaseColorFactor,
        UniformFields.EmissiveFactor,
        UniformFields.TextureFrames,
        UniformFields.TextureScroll,
        UniformFields.FogColor,
        UniformFields.DebugView,
        UniformFields.CameraForward,
        UniformFields.Exposure,
    )
}

/** The material block of [MaterialUniformLayouts.Lit] -- see [pbrMaterialFloats]. */
val PBR_MATERIAL_FLOATS: Int = UniformFields.PbrFactors.floats

/** The material block of [MaterialUniformLayouts.PbrTextured] -- see [pbrTexturedMaterialFloats]. */
val PBR_TEXTURED_MATERIAL_FLOATS: Int = MaterialUniformLayouts.PbrTexturedMaterial.total
