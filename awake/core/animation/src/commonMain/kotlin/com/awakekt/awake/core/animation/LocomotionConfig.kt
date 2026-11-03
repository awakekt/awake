/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

/**
 * Clip names and thresholds for [LocomotionPlayer]. Units are the caller's world units and seconds.
 * Missing clip names (or clips absent from the library) preserve the previous playback. Ground
 * speed is exponentially smoothed with the existing 0.1-second time constant; a stop is recognized
 * after 0.1 seconds without motion. Optional reference speeds match the walk/run stride to travel.
 *
 * @property idle Clip while standing.
 * @property walk Clip above [walkAbove] ground speed.
 * @property run Clip above [runAbove] ground speed.
 * @property jump Clip in the air, or while rising when [fall] is supplied.
 * @property takeOff Clip played once when leaving the ground.
 * @property fall Clip while descending, falling back to [jump] if absent.
 * @property land Clip played once on landing, giving way to walking.
 * @property walkAbove Ground speed above which to walk.
 * @property runAbove Ground speed above which to run.
 * @property airborneAbove Vertical speed that starts airborne motion without a supplied ground contact.
 * @property crossFade Duration in seconds of a clip change.
 * @property walkReferenceSpeed Authored ground speed of the walk cycle; null keeps its authored rate.
 * @property runReferenceSpeed Authored ground speed of the run cycle; null keeps its authored rate.
 */
data class LocomotionConfig(
    val idle: String? = null,
    val walk: String? = null,
    val run: String? = null,
    val jump: String? = null,
    val takeOff: String? = null,
    val fall: String? = null,
    val land: String? = null,
    val walkAbove: Float = 0.1f,
    val runAbove: Float = 4f,
    val airborneAbove: Float = 2f,
    val crossFade: Float = 0.15f,
    val walkReferenceSpeed: Float? = null,
    val runReferenceSpeed: Float? = null,
)
