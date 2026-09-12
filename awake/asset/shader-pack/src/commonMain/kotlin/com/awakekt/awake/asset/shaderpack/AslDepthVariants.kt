/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.column
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.instanceModelMatrix
import com.awakekt.awake.asset.shaderdsl.length
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.storageArrayOfArrays
import com.awakekt.awake.asset.shaderdsl.times
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
        val model = instanceModelMatrix(if (skinned) 5 else 3)
        val animated = animatedShadowPosition(shadow, local.xyz)
        returnPosition(cascade * model * vec4(animated, 1f.lit))
    }
    fragment { }
}

/** Static instanced depth caster; the instance matrix starts after Position/Normal/Color. */
val InstancedShadowDepthShader: AslShaderDefinition =
    instancedDepth("instanced_shadow_depth", VertexFormat.PositionNormalColor, skinned = false)

/** Skinned-instanced depth caster; the joint palette remains in its dedicated storage group. */
val SkinnedInstancedShadowDepthShader: AslShaderDefinition =
    instancedDepth("skinned_instanced_shadow_depth", VertexFormat.PositionNormalColorSkin, skinned = true)

/** Non-instanced skinned depth caster; its palette is part of the material uniform block. */
val SkinnedShadowDepthShader: AslShaderDefinition = shader("skinned_shadow_depth") {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(SkinnedUniformLayout)
    val palette = handles.array("jointPalette")
    val mvp = handles.value("mvp")
    val cascade = uniformBlock(
        "Cascade",
        group = SHADOW_CASCADE_PASS_GROUP,
        binding = 0,
    ).fieldsFrom(CascadePassUniformLayout).value("cascadeViewProjection")
    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColorSkin)
        val joints = ins.input(VertexSemantic.JointIndices)
        val weights = ins.input(VertexSemantic.JointWeights)
        fun joint(slot: AslExpr): AslExpr = palette[slot]
        val skin = weights.x * joint(joints.x) + weights.y * joint(joints.y) +
            weights.z * joint(joints.z) + weights.w * joint(joints.w)
        val position = skin * vec4(ins.input(VertexSemantic.Position), 1f.lit)
        returnPosition(cascade * mvp * position)
    }
    fragment { }
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
