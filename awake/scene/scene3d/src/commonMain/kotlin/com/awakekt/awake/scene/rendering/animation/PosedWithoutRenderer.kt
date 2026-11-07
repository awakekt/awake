/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.ecs.EcsTag

/**
 * Marks a skinned model whose bones the game reads on a host with no renderer, such as a hit volume,
 * a muzzle or a grab point that follows a bone on a game server. An [AnimationSystem] built with
 * `hasRenderer = false` poses such a model every frame, as a host that draws does; it leaves every
 * other model's [SkinnedPose] unposed. A [SocketAttachmentComponent]'s target needs no tag: it is
 * posed while an enabled socket follows it.
 */
data object PosedWithoutRenderer : EcsTag
