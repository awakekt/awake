/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.a
import io.github.awakelab.awake.asset.shaderdsl.column
import io.github.awakelab.awake.asset.shaderdsl.div
import io.github.awakelab.awake.asset.shaderdsl.dot
import io.github.awakelab.awake.asset.shaderdsl.fieldsFrom
import io.github.awakelab.awake.asset.shaderdsl.gt
import io.github.awakelab.awake.asset.shaderdsl.inputsFrom
import io.github.awakelab.awake.asset.shaderdsl.instanceModelMatrix
import io.github.awakelab.awake.asset.shaderdsl.length
import io.github.awakelab.awake.asset.shaderdsl.lit
import io.github.awakelab.awake.asset.shaderdsl.plus
import io.github.awakelab.awake.asset.shaderdsl.rgb
import io.github.awakelab.awake.asset.shaderdsl.sampler
import io.github.awakelab.awake.asset.shaderdsl.shader
import io.github.awakelab.awake.asset.shaderdsl.texture2d
import io.github.awakelab.awake.asset.shaderdsl.textureSample
import io.github.awakelab.awake.asset.shaderdsl.times
import io.github.awakelab.awake.asset.shaderdsl.unaryMinus
import io.github.awakelab.awake.asset.shaderdsl.vec2
import io.github.awakelab.awake.asset.shaderdsl.vec4
import io.github.awakelab.awake.asset.shaderdsl.x
import io.github.awakelab.awake.asset.shaderdsl.xyz
import io.github.awakelab.awake.asset.shaderdsl.y
import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.geometry.VertexSemantic
import io.github.awakelab.awake.render.renderer.ParticleUniformLayout

/**
 * GPU-instanced camera-facing billboard for `VertexFormat.PositionUv` (shared unit quad).
 * Instance locations continue after PositionUv's 0/1: model columns at 2..5, per-particle
 * RGBA at 6, sprite-strip frame index at 7 -- `RenderPipeline.vertexInputState` computes the
 * same firstLocation from the format, so these literals must match that arithmetic. The
 * uniform struct derives from [ParticleUniformLayout] (the layout names the camera matrix
 * "mvp"; the hand-written file said "viewProjection").
 *
 * The instance matrix is read sparsely: column 3 is the particle's world center, column 0's
 * length its uniform scale, and column 1 is reused by `ParticleVisual.stretchWithVelocity` to
 * carry an optional world-space stretch vector (zero = plain symmetric billboard) rather than
 * adding a whole per-instance buffer for one optional capability.
 */
@Suppress("LongMethod")
private fun particle(): AslShaderDefinition = shader("particle") {
    val u = uniformBlock(
        "Uniforms",
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 0,
    )
    val handles = u.fieldsFrom(ParticleUniformLayout)
    val camera = handles.value("mvp")
    // World-space camera basis, CPU-computed once per frame -- not derivable from the
    // view-projection without an inverse, and every instance needs the same pair.
    val cameraRight = handles.value("cameraRight")
    val cameraUp = handles.value("cameraUp")
    // x = the sprite atlas's frame count; frame CHOICE is per-particle via inFrame.
    val frameInfo = handles.value("frameInfo")
    val particleTexture by texture2d(
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 1,
    )
    val particleSampler by sampler(
        group = BindingLayout.Standard.slot(BindingSemantic.Material),
        binding = 2,
    )

    val out = varyings("VertexOutput")
    val uv by out.varying(GpuDataShape.Vec2, location = 0)
    val color by out.varying(GpuDataShape.Vec4, location = 1)

    val stretchEpsilon = const("STRETCH_EPSILON", 1e-5f)

    vertex {
        val ins = inputsFrom(VertexFormat.PositionUv)
        val inPosition = ins.input(VertexSemantic.Position)
        val inUv = ins.input(VertexSemantic.Uv)
        val model = let("model", instanceModelMatrix(startLocation = 2))
        val inColor by input(GpuDataShape.Vec4, location = 6)
        val inFrame by input(GpuDataShape.Float, location = 7)

        // Translation-only read: this system never writes rotation into an instance matrix,
        // so column 3 is exactly the world center and column 0's length the uniform scale.
        val center = let("center", column(model, 3).xyz)
        val width = let("width", length(column(model, 0).xyz))
        // Velocity-stretch rides in column 1; zero falls through to the symmetric quad.
        val stretch = let("stretch", column(model, 1).xyz)
        val stretchScreen = let(
            "stretchScreen",
            vec2(dot(stretch, cameraRight.xyz), dot(stretch, cameraUp.xyz)),
        )
        val stretchLength = let("stretchLength", length(stretchScreen))
        val longAxis = variable("longAxis", cameraUp.xyz)
        val crossAxis = variable("crossAxis", cameraRight.xyz)
        val lengthScale = variable("lengthScale", width)
        iff(stretchLength gt stretchEpsilon) {
            val dir = let("dir", stretchScreen / stretchLength)
            assign(longAxis, cameraRight.xyz * dir.x + cameraUp.xyz * dir.y)
            assign(crossAxis, cameraRight.xyz * -dir.y + cameraUp.xyz * dir.x)
            assign(lengthScale, width + stretchLength)
        }
        val worldPos = let(
            "worldPos",
            center + (inPosition.x * width) * crossAxis + (inPosition.y * lengthScale) * longAxis,
        )
        out.position set (camera * vec4(worldPos, 1f.lit))
        val frameCount = let("frameCount", frameInfo.x)
        // Horizontal sprite strip: this instance's own frame picks a 1/frameCount-wide slice.
        uv set vec2((inUv.x + inFrame) / frameCount, inUv.y)
        color set inColor
    }

    fragment {
        val sampled = let("sampled", textureSample(particleTexture, particleSampler, uv))
        colorOutput(vec4(sampled.rgb * color.rgb, sampled.a * color.a))
    }
}

val ParticleShader: AslShaderDefinition = particle()
