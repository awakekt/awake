/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import io.github.awakelab.awake.core.geometry.GpuDataShape

// The WGSL texture builtins. Each checks its texture's type, because sampling a depth or array
// texture through the wrong one produces WGSL that naga rejects with a message about the
// generated source rather than the ASL that wrote it.

/**
 * WGSL `textureSampleLevel` builtin for color textures.
 *
 * @param texture The color texture expression.
 * @param sampler The sampler expression.
 * @param uv The texture coordinates.
 * @param level The mip level (float).
 * @return The sampled `vec4f` value.
 */
fun textureSampleLevel(
    texture: AslExpr,
    sampler: AslExpr,
    uv: AslExpr,
    level: AslExpr,
): AslExpr {
    if (texture.type != AslType.Texture2dF32 || sampler.type != AslType.Sampler) {
        throw AslDefinitionException(
            "textureSampleLevel needs (texture, sampler), got ${texture.type}/${sampler.type}.",
        )
    }
    return AslCall(
        "textureSampleLevel",
        listOf(texture, sampler, uv, level),
        AslType.Data(GpuDataShape.Vec4),
    )
}

/**
 * WGSL `textureSampleLevel` builtin for 2D array textures.
 *
 * [layer] selects which layer of [texture] to read; it filters within that layer, never across
 * layers.
 *
 * @param texture The array texture expression.
 * @param sampler The sampler expression.
 * @param uv The texture coordinates.
 * @param layer The array layer index (must be integer).
 * @param level The mip level (float).
 * @return The sampled `vec4f` value.
 */
fun textureSampleArrayLevel(
    texture: AslExpr,
    sampler: AslExpr,
    uv: AslExpr,
    layer: AslExpr,
    level: AslExpr,
): AslExpr {
    if (texture.type != AslType.Texture2dArrayF32 || sampler.type != AslType.Sampler) {
        throw AslDefinitionException(
            "textureSampleArrayLevel needs (array texture, sampler), got " +
                "${texture.type}/${sampler.type}.",
        )
    }
    if (layer.type != AslType.I32 && layer.type != AslType.U32) {
        throw AslDefinitionException("A layer index is an integer, got ${layer.type}.")
    }
    return AslCall(
        "textureSampleLevel",
        listOf(texture, sampler, uv, layer, level),
        AslType.Data(GpuDataShape.Vec4),
    )
}

/**
 * WGSL `textureSampleLevel` builtin for depth textures.
 *
 * Returns the stored depth as `f32` directly.
 *
 * @param texture The depth texture expression.
 * @param sampler The sampler expression.
 * @param uv The texture coordinates.
 * @param level The mip level (must be integer).
 * @return The sampled depth value.
 */
fun textureSampleLevelDepth(
    texture: AslExpr,
    sampler: AslExpr,
    uv: AslExpr,
    level: AslExpr,
): AslExpr {
    if (texture.type != AslType.TextureDepth2d || sampler.type != AslType.Sampler) {
        throw AslDefinitionException(
            "textureSampleLevelDepth needs (depth texture, sampler), got ${'$'}{texture.type}/${'$'}{sampler.type}.",
        )
    }
    if (level.type != AslType.I32 && level.type != AslType.U32) {
        throw AslDefinitionException("Depth sampling takes an integer level, got ${'$'}{level.type}.")
    }
    return AslCall("textureSampleLevel", listOf(texture, sampler, uv, level), F32)
}

/**
 * WGSL `textureDimensions` builtin.
 *
 * @param texture The texture expression.
 * @return The texture dimensions as `vec2<u32>`.
 */
fun textureDimensions(texture: AslExpr): AslExpr {
    if (texture.type != AslType.Texture2dF32 && texture.type != AslType.TextureDepth2d) {
        throw AslDefinitionException("textureDimensions needs a texture, got ${texture.type}.")
    }
    return AslCall("textureDimensions", listOf(texture), AslType.Vec2U)
}

/**
 * WGSL `textureSample` builtin.
 *
 * Implicit-LOD sample -- only legal in uniform control flow (a fragment body outside loops).
 *
 * @param texture The color texture expression.
 * @param sampler The sampler expression.
 * @param uv The texture coordinates.
 * @return The sampled `vec4f` value.
 */
fun textureSample(texture: AslExpr, sampler: AslExpr, uv: AslExpr): AslExpr {
    if (texture.type != AslType.Texture2dF32 || sampler.type != AslType.Sampler) {
        throw AslDefinitionException(
            "textureSample needs (texture, sampler), got ${'$'}{texture.type}/${'$'}{sampler.type}.",
        )
    }
    return AslCall("textureSample", listOf(texture, sampler, uv), AslType.Data(GpuDataShape.Vec4))
}
