/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.animation.Skin

/** Scene bridge for one independently-playing skeletal-animation instance. Rendering consumes
 * [SkinnedPose]; this component deliberately carries no renderer or source-format type. */
data class Animator(
    val player: AnimationPlayer,
    val skin: Skin,
)
