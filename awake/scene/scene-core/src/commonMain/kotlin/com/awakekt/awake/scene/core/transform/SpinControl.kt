/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.ecs.Poolable

/**
 * Stores the current pose for an entity whose [Transform.worldMatrix] is a translate-then-
 * rotate-around-Y composition instead of the usual position/rotation/scale TRS
 * ([TransformSystem] deliberately doesn't touch an entity that has this component -- see
 * [SpinSystem]). Gameplay/UI code (a slider, an auto-play clock, anything) sets [radians]
 * directly every frame it wants a new angle; [SpinSystem] only composes the matrix.
 */
class SpinControl : Poolable {
    /** Current rotation angle in radians around the Y axis. */
    var radians: Float = 0f

    /** Rotation speed multiplier applied when automatically spinning. */
    var speed: Float = 1f

    override fun reset() {
        radians = 0f
        speed = 1f
    }
}
