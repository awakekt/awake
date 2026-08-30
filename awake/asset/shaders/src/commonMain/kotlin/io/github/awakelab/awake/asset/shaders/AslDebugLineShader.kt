/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaders

import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.lit
import io.github.awakelab.awake.asset.shaderdsl.shader
import io.github.awakelab.awake.asset.shaderdsl.times
import io.github.awakelab.awake.asset.shaderdsl.vec4
import io.github.awakelab.awake.core.geometry.GpuDataShape

/**
 * Debug lines: MVP transform, vertex colour straight through, no lighting.
 *
 * The last hand-written WGSL among the engine's own shaders. Ported so [EngineShaderSets] is
 * uniformly ASL and needs no shader resource on any platform.
 */
val DebugLineShader: AslShaderDefinition = shader("debug_line") {
    val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
    val mvp by uniforms.field(GpuDataShape.Mat4)

    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec4, location = 0)

    vertex {
        val inPos by input(GpuDataShape.Vec3, location = 0)
        val inColor by input(GpuDataShape.Vec4, location = 1)

        out.position set mvp * vec4(inPos, 1f.lit)
        color set inColor
    }

    fragment {
        colorOutput(color)
    }
}
