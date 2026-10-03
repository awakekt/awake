/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslArrayHandle
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.AslVertexBuilder
import com.awakekt.awake.asset.shaderdsl.AslVertexInputHandles
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.column
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.instanceModelMatrix
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.storageArrayOfArrays
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.render.passes.uniforms.CascadePassUniformLayout
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.passes.uniforms.SHADOW_CASCADE_PASS_GROUP
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.MAX_JOINTS
import com.awakekt.awake.render.renderer.SkinnedUniformLayout

/**
 * Depth-only companions for structural families whose vertex inputs differ from the ordinary
 * mesh. These are deliberately separate shader definitions: a depth pipeline cannot reinterpret
 * an instance-rate matrix or a joint-palette storage binding through the ordinary material ABI.
 */
private fun instancedDepth(
    name: String,
    format: VertexFormat,
    skinned: Boolean,
): AslShaderDefinition = shader(name) {
    // These draws use the same LitShadow block as the visible pass. The compact instanced block
    // omitted vertexAnimation, which made depth casters disagree with animated visible meshes.
    val shadow = shadowUniforms(includeLitTail = false)
    val palette = if (skinned) {
        storageArrayOfArrays(
            structName = "JointPalette",
            varName = "palettes",
            group = BindingLayout.Standard.slot(BindingSemantic.JointPalette),
            binding = 0,
            fieldName = "joints",
            elementShape = GpuDataShape.Mat4,
            elementCount = MAX_JOINTS,
        )
    } else {
        null
    }
    val cascade = uniformBlock(
        "Cascade",
        group = SHADOW_CASCADE_PASS_GROUP,
        binding = 0,
    ).fieldsFrom(CascadePassUniformLayout).value("cascadeViewProjection")
    vertex {
        val ins = inputsFrom(format)
        val position = ins.input(VertexSemantic.Position)
        val local = if (skinned) {
            val joints = ins.input(VertexSemantic.JointIndices)
            val weights = ins.input(VertexSemantic.JointWeights)
            val index = instanceIndex()
            fun joint(slot: AslExpr): AslExpr = palette!!.element(index, slot)
            val skin = weights.x * joint(joints.x) + weights.y * joint(joints.y) +
                weights.z * joint(joints.z) + weights.w * joint(joints.w)
            skin * vec4(position, 1f.lit)
        } else {
            vec4(position, 1f.lit)
        }
        val model = instanceModelMatrixAfter(format)
        val animated = animatedShadowPosition(shadow, local.xyz)
        returnPosition(cascade * model * vec4(animated, 1f.lit))
    }
    fragment { }
}

/** Static instanced depth caster; the instance matrix starts after Position/Normal/Color. */
val InstancedShadowDepthShader: AslShaderDefinition =
    instancedDepth("instanced_shadow_depth", VertexFormat.PositionNormalColor, skinned = false)

/** Static instanced depth caster for textured meshes; reads the shadow-depth prefix textured blocks share. */
val InstancedTexturedShadowDepthShader: AslShaderDefinition =
    instancedDepth("instanced_textured_shadow_depth", VertexFormat.PositionNormalColorUv, skinned = false)

/**
 * This format's per-instance model matrix. Both backends put the instance attributes right after
 * the format's last vertex attribute, so the shader reads them there.
 */
internal fun AslVertexBuilder.instanceModelMatrixAfter(format: VertexFormat): AslExpr =
    instanceModelMatrix(format.attributes.maxOf { it.location } + 1)

/** Skinned-instanced depth caster; the joint palette remains in its dedicated storage group. */
val SkinnedInstancedShadowDepthShader: AslShaderDefinition =
    instancedDepth("skinned_instanced_shadow_depth", VertexFormat.PositionNormalColorSkin, skinned = true)

/** Non-instanced skinned depth caster; its palette is part of the material uniform block. */
val SkinnedShadowDepthShader: AslShaderDefinition =
    skinnedDepth("skinned_shadow_depth", VertexFormat.PositionNormalColorSkin)

/** [SkinnedShadowDepthShader] for textured skinned meshes, whose UVs shift the joint attributes. */
val SkinnedTexturedShadowDepthShader: AslShaderDefinition =
    skinnedDepth("skinned_textured_shadow_depth", VertexFormat.PositionNormalColorUvSkin)

/**
 * [SkinnedTexturedShadowDepthShader] for a masked material: discards texels whose base-colour alpha
 * falls below the cutoff `pbrFactors.z`, so cut-out hair or cloth casts its cut-out shape, as
 * [MaskedTexturedDepthShader] does for a static mesh.
 */
val SkinnedMaskedTexturedDepthShader: AslShaderDefinition = shader("skinned_textured_shadow_depth_masked") {
    val handles = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    ).fieldsFrom(SkinnedUniformLayout)
    val cascade = uniformBlock(
        "Cascade",
        group = SHADOW_CASCADE_PASS_GROUP,
        binding = 0,
    ).fieldsFrom(CascadePassUniformLayout).value("cascadeViewProjection")
    val baseColorTexture by texture2d(group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 1)
    val baseColorSampler by sampler(group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 2)
    val out = varyings("VertexOutput")
    val uv by out.varying(GpuDataShape.Vec2, location = 0)
    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColorUvSkin)
        out.position set (cascade * (handles.value("model") * skinnedPosition(ins, handles.array("jointPalette"))))
        // The visible pass's flip of the decoder's bottom-up V, so both read the same texel.
        val inUv = ins.input(VertexSemantic.Uv)
        uv set vec2(inUv.x, 1f.lit - inUv.y)
    }
    fragment {
        val alpha = let("alpha", textureSample(baseColorTexture, baseColorSampler, uv).a * handles.value("baseColorFactor").a)
        discardIf(alpha lt handles.value("pbrFactors").z)
    }
}

/**
 * A skinned mesh of [format] cast into the shadow map: posed by its joint palette, placed by its
 * model matrix, then projected by the cascade. The material block's `mvp` is the camera's, so the
 * shadow pass cannot use it.
 */
private fun skinnedDepth(name: String, format: VertexFormat): AslShaderDefinition = shader(name) {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(SkinnedUniformLayout)
    val palette = handles.array("jointPalette")
    val model = handles.value("model")
    val cascade = uniformBlock(
        "Cascade",
        group = SHADOW_CASCADE_PASS_GROUP,
        binding = 0,
    ).fieldsFrom(CascadePassUniformLayout).value("cascadeViewProjection")
    vertex {
        returnPosition(cascade * (model * skinnedPosition(inputsFrom(format), palette)))
    }
    fragment { }
}

/** The vertex's position posed by [palette]: its four weighted joint matrices applied. */
private fun skinnedPosition(ins: AslVertexInputHandles, palette: AslArrayHandle): AslExpr {
    val joints = ins.input(VertexSemantic.JointIndices)
    val weights = ins.input(VertexSemantic.JointWeights)
    fun joint(slot: AslExpr): AslExpr = palette[slot]
    val skin = weights.x * joint(joints.x) + weights.y * joint(joints.y) +
        weights.z * joint(joints.z) + weights.w * joint(joints.w)
    return skin * vec4(ins.input(VertexSemantic.Position), 1f.lit)
}

/** Billboard-particle depth caster. Color and atlas-frame streams are intentionally unused. */
val ParticleShadowDepthShader: AslShaderDefinition = shader("particle_shadow_depth") {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(ParticleUniformLayout)
    handles.value("mvp")
    val cameraRight = handles.value("cameraRight")
    val cameraUp = handles.value("cameraUp")
    val cascade = uniformBlock(
        "Cascade",
        group = SHADOW_CASCADE_PASS_GROUP,
        binding = 0,
    ).fieldsFrom(CascadePassUniformLayout).value("cascadeViewProjection")
    vertex {
        val ins = inputsFrom(VertexFormat.PositionUv)
        val position = ins.input(VertexSemantic.Position)
        val model = let("model", instanceModelMatrix(startLocation = 2))
        val center = column(model, 3).xyz
        val width = length(column(model, 0).xyz)
        val world = center + position.x * width * cameraRight.xyz + position.y * width * cameraUp.xyz
        returnPosition(cascade * vec4(world, 1f.lit))
    }
    fragment { }
}
