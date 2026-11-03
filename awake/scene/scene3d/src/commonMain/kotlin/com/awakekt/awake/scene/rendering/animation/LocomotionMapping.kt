/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

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
)
