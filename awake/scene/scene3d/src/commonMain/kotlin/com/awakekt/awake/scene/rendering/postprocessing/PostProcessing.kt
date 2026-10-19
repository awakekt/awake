/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.postprocessing

import com.awakekt.awake.render.effects.PostProcessingEffect

/**
 * Camera post-processing stack component.
 *
 * Holds an ordered list of [PostProcessingEffect] descriptors executed per frame after the 3D scene
 * pass and before UI presentation.
 *
 * @property enabled Whether this camera's post-processing stack is active.
 * @property effects Ordered list of post-processing effects.
 */
data class PostProcessing(
    var enabled: Boolean = true,
    var effects: List<PostProcessingEffect> = emptyList(),
)
