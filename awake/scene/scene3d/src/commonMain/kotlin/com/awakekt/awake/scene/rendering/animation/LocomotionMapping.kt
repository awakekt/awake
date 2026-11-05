/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.animation.FreeLocomotionConfig
import com.awakekt.awake.core.animation.LocomotionConfig

/** All authored locomotion options cross the scene/capability boundary here. */
internal fun SceneLocomotionAnimation.toLocomotionConfig(): LocomotionConfig = LocomotionConfig(
    idle = idle,
    walk = walk,
    run = run,
    jump = jump,
    takeOff = takeOff,
    fall = fall,
    land = land,
    walkAbove = walkAbove,
    runAbove = runAbove,
    airborneAbove = airborneAbove,
    crossFade = crossFade,
    walkReferenceSpeed = walkReferenceSpeed,
    runReferenceSpeed = runReferenceSpeed,
    free = free?.toFreeLocomotionConfig(),
)

/** Maps authored 3D free-motion locomotion options to the scene-independent capability config. */
internal fun SceneFreeLocomotionAnimation.toFreeLocomotionConfig(): FreeLocomotionConfig = FreeLocomotionConfig(
    idle = idle,
    move = move,
    fast = fast,
    ascend = ascend,
    descend = descend,
    moveAbove = moveAbove,
    fastAbove = fastAbove,
    verticalAbove = verticalAbove,
    crossFade = crossFade,
    moveReferenceSpeed = moveReferenceSpeed,
    fastReferenceSpeed = fastReferenceSpeed,
)
