/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.motion

/**
 * Whether an entity stands on something or moves in 3D free motion, kept by whatever moves it (a
 * character controller) for whatever reacts to it, such as locomotion animation choosing
 * grounded/airborne or free-motion clips.
 *
 * @property grounded `true` if the entity currently maintains physical contact with the ground.
 * @property freeMotion `true` if the entity moves freely in 3D without ground or gravity phases.
 */
class GroundContact(
    var grounded: Boolean = true,
    var freeMotion: Boolean = false,
) {
    /** [GroundContact] as it was before [freeMotion]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(grounded: Boolean = true) : this(grounded = grounded, freeMotion = false)
}
