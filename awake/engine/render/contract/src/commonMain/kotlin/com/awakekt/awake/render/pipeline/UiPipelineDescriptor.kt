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
    Quad,
    Glyph,
    Texture,
    RoundedQuad,
    TargetComposite,
}

/**
 * Backend-neutral descriptor for a UI pipeline variant.
 * Translates shared UI draw decisions into native GPU pipeline states on Vulkan and WebGPU.
 */
data class UiPipelineDescriptor(
    val variant: UiPipelineVariant,
    val vertexFormat: VertexFormat,
    val blendMode: BlendMode = BlendMode.SourceOver,
    val isPremultiplied: Boolean = false,
)
