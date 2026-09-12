/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.instanceModelMatrix
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.storageArrayOfArrays
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.render.passes.uniforms.InstancedUniformLayout
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.MAX_JOINTS
import com.awakekt.awake.render.renderer.SkinnedUniformLayout

@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun meshVariant(name: String, instanced: Boolean, skinned: Boolean): AslShaderDefinition =
    shader(name) {
        val u = uniformBlock(
            "Uniforms",
            group = BindingLayout.Standard.slot(BindingSemantic.Material),
            binding = 0,
        )
        val handles = u.fieldsFrom(if (skinned && !instanced) SkinnedUniformLayout else InstancedUniformLayout)
        val camera = handles.value("mvp")
        val lightDirection = if (instanced) handles.value("lightDirection") else null
        val lightColor = if (instanced) handles.value("lightColor") else null
        val uniformPalette = if (skinned && !instanced) handles.array("jointPalette") else null
        val storagePalettes = if (skinned && instanced) {
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

        val out = varyings("VertexOutput")
        val color by out.varying(GpuDataShape.Vec3, location = 0)
        val normal by out.varying(GpuDataShape.Vec3, location = 1)

        vertex {
            val instance = if (storagePalettes != null) instanceIndex() else null
            val ins = inputsFrom(if (skinned) VertexFormat.PositionNormalColorSkin else VertexFormat.PositionNormalColor)
            val inPosition = ins.input(VertexSemantic.Position)
            val inNormal = ins.input(VertexSemantic.Normal)

            val position: AslExpr
            val outNormal: AslExpr
            if (skinned) {
                val joints = ins.input(VertexSemantic.JointIndices)
                val weights = ins.input(VertexSemantic.JointWeights)
                fun palette(slot: AslExpr): AslExpr = storagePalettes?.element(instance!!, slot)
                    ?: uniformPalette!![slot]
                val skinMatrix = let(
                    "skinMatrix",
                    weights.x * palette(joints.x) + weights.y * palette(joints.y) +
                        weights.z * palette(joints.z) + weights.w * palette(joints.w),
                )
                position = let("skinnedPosition", skinMatrix * vec4(inPosition, 1f.lit))
                outNormal = let("skinnedNormal", skinMatrix * vec4(inNormal, 0f.lit))
            } else {
                position = vec4(inPosition, 1f.lit)
                outNormal = inNormal
            }

            if (instanced) {
                val model = let("model", instanceModelMatrix(startLocation = if (skinned) 5 else 3))
                out.position set (camera * model * position)
                normal set if (skinned) (model * outNormal).xyz else outNormal
            } else {
                out.position set (camera * position)
                normal set if (skinned) outNormal.xyz else outNormal
            }
            color set ins.input(VertexSemantic.Color)
        }

        val lightVector = lightDirection?.xyz
            ?: const("LIGHT_DIRECTION", vec3(0.4f.lit, 0.8f.lit, 0.4f.lit))
        val ambient = const("AMBIENT_STRENGTH", 0.35f)

        fragment {
            val n = let("n", normalize(normal))
            val l = let("l", normalize(lightVector))
            val diffuse = let("diffuse", max(dot(n, l), 0f.lit))
            val shade = let("shade", ambient + (1f.lit - ambient) * diffuse)
            val lit = if (lightColor != null) color * shade * lightColor.xyz else color * shade
            colorOutput(vec4(lit, 1f.lit))
        }
    }

private fun skinnedTextured(): AslShaderDefinition = shader("skinned_textured") {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(SkinnedUniformLayout)
    val camera = handles.value("mvp")
    val uniformPalette = handles.array("jointPalette")

    val baseColorTexture by texture2d(
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 1,
    )
    val baseColorSampler by sampler(
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 2,
    )

    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec3, location = 0)
    val normal by out.varying(GpuDataShape.Vec3, location = 1)
    val uv by out.varying(GpuDataShape.Vec2, location = 2)

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColorUvSkin)
        val inPosition = ins.input(VertexSemantic.Position)
        val inNormal = ins.input(VertexSemantic.Normal)
        val joints = ins.input(VertexSemantic.JointIndices)
        val weights = ins.input(VertexSemantic.JointWeights)

        val skinMatrix = let(
            "skinMatrix",
            weights.x * uniformPalette[joints.x] + weights.y * uniformPalette[joints.y] +
                weights.z * uniformPalette[joints.z] + weights.w * uniformPalette[joints.w],
        )
        val position = let("skinnedPosition", skinMatrix * vec4(inPosition, 1f.lit))
        val outNormal = let("skinnedNormal", skinMatrix * vec4(inNormal, 0f.lit))

        out.position set (camera * position)
        normal set outNormal.xyz
        color set ins.input(VertexSemantic.Color)
        val inUv = ins.input(VertexSemantic.Uv)
        uv set inUv
    }

    val lightVector = const("LIGHT_DIRECTION", vec3(0.4f.lit, 0.8f.lit, 0.4f.lit))
    val ambient = const("AMBIENT_STRENGTH", 0.35f)

    fragment {
        val n = let("n", normalize(normal))
        val l = let("l", normalize(lightVector))
        val diffuse = let("diffuse", max(dot(n, l), 0f.lit))
        val shade = let("shade", ambient + (1f.lit - ambient) * diffuse)
        val texColor = let("texColor", textureSample(baseColorTexture, baseColorSampler, uv))
        val lit = texColor.rgb * color * shade
        colorOutput(vec4(lit, texColor.a))
    }
}

val InstancedShader: AslShaderDefinition = meshVariant("instanced", instanced = true, skinned = false)

val SkinnedShader: AslShaderDefinition = meshVariant("skinned", instanced = false, skinned = true)

val SkinnedTexturedShader: AslShaderDefinition = skinnedTextured()

val SkinnedInstancedShader: AslShaderDefinition =
    meshVariant("skinned_instanced", instanced = true, skinned = true)
