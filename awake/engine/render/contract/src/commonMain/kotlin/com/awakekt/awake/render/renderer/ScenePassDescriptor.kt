/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.math.Lens

/**
 * Descriptor encapsulating the complete frame pass inputs for scene rendering.
 *
 * Encapsulates the camera lens, draw calls, lighting, and environmental pass uniforms so that
 * the hardware [Renderer] remains a decoupled GPU execution pipeline rather than accumulating
 * individual game-aesthetic parameters or mutable state.
 */
data class ScenePassDescriptor(
    val camera: Lens,
    val drawCalls: List<DrawCall>,
    val light: SceneLight = DEFAULT_SCENE_LIGHT,
    val environment: EnvironmentUniforms = EnvironmentUniforms.Default,
)
