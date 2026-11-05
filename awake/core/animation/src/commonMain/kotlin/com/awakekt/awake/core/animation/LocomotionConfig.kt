/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

/**
 * Clip names and thresholds for [LocomotionPlayer]. Units are the caller's world units and seconds.
 * Missing clip names (or clips absent from the library) preserve the previous playback. Ground and
 * 3D speeds are exponentially smoothed with a 0.1-second time constant; a stop is recognized
 * after 0.1 seconds without motion. Optional reference speeds match cycle playback to travel.
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
 * @property free Optional clip set and thresholds for 3D free-motion playback; null reuses [idle], [walk] and [run] against 3D speed.
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
    val free: FreeLocomotionConfig? = null,
) {
    /** [LocomotionConfig] as it was before [free]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        idle: String? = null,
        walk: String? = null,
        run: String? = null,
        jump: String? = null,
        takeOff: String? = null,
        fall: String? = null,
        land: String? = null,
        walkAbove: Float = 0.1f,
        runAbove: Float = 4f,
        airborneAbove: Float = 2f,
        crossFade: Float = 0.15f,
        walkReferenceSpeed: Float? = null,
        runReferenceSpeed: Float? = null,
    ) : this(
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
        free = null,
    )

    init {
        require(walkAbove.isFinite() && walkAbove >= 0f) { "walkAbove must be finite and non-negative" }
        require(runAbove.isFinite() && runAbove >= walkAbove) { "runAbove must be finite and at least walkAbove" }
        require(airborneAbove.isFinite() && airborneAbove > 0f) { "airborneAbove must be finite and positive" }
        require(crossFade.isFinite() && crossFade >= 0f) { "crossFade must be finite and non-negative" }
        require(walkReferenceSpeed == null || (walkReferenceSpeed.isFinite() && walkReferenceSpeed > 0f)) {
            "walkReferenceSpeed must be null or finite and positive"
        }
        require(runReferenceSpeed == null || (runReferenceSpeed.isFinite() && runReferenceSpeed > 0f)) {
            "runReferenceSpeed must be null or finite and positive"
        }
    }

    /** [copy] as it was before [free]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        idle: String? = this.idle,
        walk: String? = this.walk,
        run: String? = this.run,
        jump: String? = this.jump,
        takeOff: String? = this.takeOff,
        fall: String? = this.fall,
        land: String? = this.land,
        walkAbove: Float = this.walkAbove,
        runAbove: Float = this.runAbove,
        airborneAbove: Float = this.airborneAbove,
        crossFade: Float = this.crossFade,
        walkReferenceSpeed: Float? = this.walkReferenceSpeed,
        runReferenceSpeed: Float? = this.runReferenceSpeed,
    ): LocomotionConfig = copy(
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
        free = this.free,
    )
}

/**
 * Clip names and thresholds for [LocomotionPlayer] when free-motion playback is active (such as
 * swimming, flying or zero-gravity travel). Units are the caller's world units and seconds.
 * Missing clip names (or clips absent from the library) fall back or preserve the previous
 * playback. Full 3D and vertical speeds are exponentially smoothed over 0.1 seconds; optional
 * reference speeds match [move] and [fast] playback to 3D travel speed.
 *
 * @property idle Clip while hovering or stationary in 3D space.
 * @property move Clip above [moveAbove] 3D travel speed when neither [fast], [ascend] nor [descend] takes precedence.
 * @property fast Clip above [fastAbove] 3D travel speed.
 * @property ascend Optional clip when climbing faster than [verticalAbove] and at least as fast vertically as horizontally.
 * @property descend Optional clip when diving faster than [verticalAbove] downward and at least as fast vertically as horizontally.
 * @property moveAbove 3D speed above which to select [move] (falling back to [idle] when [move] is null).
 * @property fastAbove 3D speed above which to select [fast].
 * @property verticalAbove Vertical speed magnitude above which [ascend] or [descend] can apply.
 * @property crossFade Optional duration in seconds of a clip change while in free motion; null uses [LocomotionConfig.crossFade].
 * @property moveReferenceSpeed Authored 3D speed of the [move] cycle; null keeps its authored rate.
 * @property fastReferenceSpeed Authored 3D speed of the [fast] cycle; null keeps its authored rate.
 */
data class FreeLocomotionConfig(
    val idle: String? = null,
    val move: String? = null,
    val fast: String? = null,
    val ascend: String? = null,
    val descend: String? = null,
    val moveAbove: Float = 0.1f,
    val fastAbove: Float = 4f,
    val verticalAbove: Float = 0.5f,
    val crossFade: Float? = null,
    val moveReferenceSpeed: Float? = null,
    val fastReferenceSpeed: Float? = null,
) {
    init {
        require(moveAbove.isFinite() && moveAbove >= 0f) { "moveAbove must be finite and non-negative" }
        require(fastAbove.isFinite() && fastAbove >= moveAbove) { "fastAbove must be finite and at least moveAbove" }
        require(verticalAbove.isFinite() && verticalAbove > 0f) { "verticalAbove must be finite and positive" }
        require(crossFade == null || (crossFade.isFinite() && crossFade >= 0f)) {
            "crossFade must be null or finite and non-negative"
        }
        require(moveReferenceSpeed == null || (moveReferenceSpeed.isFinite() && moveReferenceSpeed > 0f)) {
            "moveReferenceSpeed must be null or finite and positive"
        }
        require(fastReferenceSpeed == null || (fastReferenceSpeed.isFinite() && fastReferenceSpeed > 0f)) {
            "fastReferenceSpeed must be null or finite and positive"
        }
    }
}
