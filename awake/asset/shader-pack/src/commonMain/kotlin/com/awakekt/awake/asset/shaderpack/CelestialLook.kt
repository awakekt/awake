/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.math.Vec3f

/**
 * How the sun and moon look this frame, read every frame so a scene can change them without reloading.
 *
 * @property moonPhase 0 is a new moon, 0.25 a first quarter lit on the right, 0.5 a full moon (the
 *   whole disc, as the procedural sky draws it) and 0.75 a last quarter lit on the left; 1 wraps to new.
 * @property sunVisibility How visible the sun is, 0 hidden to 1 shown.
 * @property moonVisibility How visible the moon is, 0 hidden to 1 shown.
 * @property followsDirection Whether [sunDirection] is the direction toward the sun. False takes the
 *   pass's light direction instead, for a scene with no day cycle.
 */
class CelestialLook(
    var moonPhase: Float = FULL_MOON,
    var sunVisibility: Float = 1f,
    var moonVisibility: Float = 1f,
    var followsDirection: Boolean = false,
) {
    /** The direction toward the sun, used while [followsDirection]; the moon is opposite. */
    val sunDirection: Vec3f = Vec3f(0f, 1f, 0f)

    /** Constants of the look. */
    companion object {
        /** A full moon: the whole disc. */
        const val FULL_MOON = 0.5f
    }
}
