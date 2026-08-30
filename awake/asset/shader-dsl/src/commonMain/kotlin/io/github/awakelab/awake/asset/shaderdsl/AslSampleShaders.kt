/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.geometry.VertexSemantic

/**
 * The module's two proof definitions -- the acceptance fixtures from the plan doc, kept in
 * main (not test) source because the CLI preview renders them and consumers can read them as
 * the canonical usage reference. Production shader definitions belong in the consuming
 * sample/game, not here.
 */

/**
 * Regenerates `samples/studio`'s hand-written `triangle.wgsl` (minus its prose comments).
 */
val TriangleShader: AslShaderDefinition = shader("triangle") {
    val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
    val mvp by uniforms.field(GpuDataShape.Mat4)
    // vec4, not vec3 -- sidesteps WGSL's 16-byte alignment padding, same reasoning as the
    // hand-written file documents.
    val lightDirection by uniforms.field(GpuDataShape.Vec4)
    val lightColor by uniforms.field(GpuDataShape.Vec4)

    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec3, location = 0)
    val normal by out.varying(GpuDataShape.Vec3, location = 1)

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColor)
        out.position set (mvp * vec4(ins.input(VertexSemantic.Position), 1f.lit))
        color set ins.input(VertexSemantic.Color)
        normal set ins.input(VertexSemantic.Normal)
    }

    val ambient = const("AMBIENT_STRENGTH", 0.08f)

    fragment {
        val n = let("n", normalize(normal))
        val l = let("l", normalize(lightDirection.xyz))
        val diffuse = let("diffuse", max(dot(n, l), 0f.lit))
        val shade = let("shade", ambient + (1f.lit - ambient) * diffuse)
        val lit = let("lit", color * shade * lightColor.xyz)
        val mapped = let("mapped", lit / (lit + vec3(1f.lit)))
        colorOutput(vec4(mapped, 1f.lit))
    }
}

/**
 * A shader no hand-written file exists for -- the new-content proof: UV-space checkerboard
 * blending two uniform colors.
 */
val CheckerShader: AslShaderDefinition = shader("checker") {
    val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
    val tiles by uniforms.field(GpuDataShape.Vec4)
    val colorA by uniforms.field(GpuDataShape.Vec4)
    val colorB by uniforms.field(GpuDataShape.Vec4)

    val out = varyings("VertexOutput")
    val uv by out.varying(GpuDataShape.Vec2, location = 0)

    vertex {
        val inPosition by input(GpuDataShape.Vec3, location = 0)
        val inUv by input(GpuDataShape.Vec2, location = 1)

        out.position set vec4(inPosition, 1f.lit)
        uv set inUv
    }

    fragment {
        val cell = let("cell", floor(uv * tiles.x))
        val parity = let("parity", fract((cell.x + cell.y) * 0.5f.lit) * 2f.lit)
        colorOutput(mix(colorA, colorB, parity))
    }
}
