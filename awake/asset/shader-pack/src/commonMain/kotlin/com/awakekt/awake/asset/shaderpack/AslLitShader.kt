/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic

/**
 * Unshadowed untextured lit mesh shader: reads the draw's model transform from its own block and
 * the view-projection and directional light from the shared frame block.
 */
val LitShader: AslShaderDefinition = shader("triangle") {
    val group = BindingLayout.Standard.slot(BindingSemantic.Material)
    val draw = uniformBlock("Uniforms", group = group, binding = 0).fieldsFrom(MaterialUniformLayouts.LitDraw)
    val frame = frameUniforms()
    val model = draw.value("model")
    val viewProjection = frame.value("viewProjection")
    val lightDirection = frame.value("lightDirection")
    val lightColor = frame.value("lightColor")
    val cameraPosition = frame.value("cameraPosition")
    val fogColor = frame.value("fogColor")
    val fogRange = frame.value("fogRange")
    val surfaceDeposit = surfaceDepositUniforms(frame)
    val drawFade = draw.value("drawFade")

    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec3, location = 0)
    val normal by out.varying(GpuDataShape.Vec3, location = 1)
    val worldPos by out.varying(GpuDataShape.Vec3, location = 2)

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColor)
        val placed = let("placed", model * vec4(ins.input(VertexSemantic.Position), 1f.lit))
        out.position set (viewProjection * placed)
        color set ins.input(VertexSemantic.Color)
        normal set ins.input(VertexSemantic.Normal)
        worldPos set placed.xyz
    }

    val ambient = const("AMBIENT_STRENGTH", 0.08f)
    val depositNoise = declareSurfaceDepositNoise()

    fragment {
        val n = let("n", normalize(normal))
        val l = let("l", normalize(lightDirection.xyz))
        val diffuse = let("diffuse", max(dot(n, l), 0f.lit))
        val shade = let("shade", ambient + (1f.lit - ambient) * diffuse)
        val baseRgb = variable("baseRgb", color)
        applyUnlitSurfaceDeposit(baseRgb, n, worldPos, cameraPosition, surfaceDeposit, drawFade, simplexFn = depositNoise)
        val lit = let("lit", baseRgb * shade * lightColor.xyz)
        val mapped = let("mapped", lit / (lit + vec3(1f.lit)))
        val fogged = applySceneFog(mapped, cameraPosition, worldPos, fogColor, fogRange)
        colorOutput(vec4(fogged, 1f.lit))
    }
}
