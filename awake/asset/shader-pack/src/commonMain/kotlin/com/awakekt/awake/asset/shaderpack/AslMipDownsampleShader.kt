/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.core.geometry.GpuDataShape

/**
 * One mip level from the level above it: a full-target triangle whose fragment samples the larger
 * level with a linear sampler at its own texel centre, the 2x2 box average a CPU mip chain takes.
 * A backend draws it once per level to build a texture's mips on the GPU, such as for an image
 * uploaded without CPU pixels (awakekt/awake#1184).
 *
 * Group 0 binds the source level at 0 and its sampler at 1. Vertex inputs: clip position (vec2) at
 * location 0 and UV (vec2) at location 1, as [MIP_DOWNSAMPLE_TRIANGLE] lays them out.
 */
val MipDownsampleShader = shader("mip_downsample") {
    val source by texture2d(group = 0, binding = 0)
    val sourceSampler by sampler(group = 0, binding = 1)
    val out = varyings("VertexOutput")
    val uv by out.varying(GpuDataShape.Vec2, location = 0)
    vertex {
        val inPos by input(GpuDataShape.Vec2, location = 0)
        val inUv by input(GpuDataShape.Vec2, location = 1)
        out.position set vec4(inPos, 0f.lit, 1f.lit)
        uv set inUv
    }
    fragment {
        colorOutput(textureSample(source, sourceSampler, uv))
    }
}

/** [MipDownsampleShader]'s WGSL, emitted once. */
val MipDownsampleWgsl: String by lazy { MipDownsampleShader.emitWgsl() }

/**
 * Interleaved `x, y, u, v` of one triangle covering the whole target, UV `(0, 0)` at its top-left
 * texel as WebGPU addresses textures: three vertices instead of a quad's four and two triangles.
 */
val MIP_DOWNSAMPLE_TRIANGLE: FloatArray = floatArrayOf(
    -1f, -1f, 0f, 1f,
    3f, -1f, 2f, 1f,
    -1f, 3f, 0f, -1f,
)
