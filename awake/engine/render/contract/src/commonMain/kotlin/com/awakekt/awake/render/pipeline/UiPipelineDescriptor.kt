/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.BlendMode

/**
 * The flavors of 2D / UI draw primitive pipelines.
 */
enum class UiPipelineVariant {
    /** Solid colored flat rectangle pipeline. */
    Quad,
    /** Text glyph rasterization pipeline. */
    Glyph,
    /** Textured 2D quad pipeline. */
    Texture,
    /** Rounded rectangle pipeline with corner radius and antialiasing. */
    RoundedQuad,
    /** Offscreen render target compositing pipeline. */
    TargetComposite,
}

/**
 * Backend-neutral descriptor for a UI pipeline variant.
 * Translates shared UI draw decisions into native GPU pipeline states on Vulkan and WebGPU.
 */
data class UiPipelineDescriptor(
    /** The UI primitive pipeline variant. */
    val variant: UiPipelineVariant,
    /** The vertex format layout expected by this pipeline. */
    val vertexFormat: VertexFormat,
    /** The blend mode used for alpha compositing. */
    val blendMode: BlendMode = BlendMode.SourceOver,
    /** Whether texture colors are premultiplied by alpha. */
    val isPremultiplied: Boolean = false,
)
