/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

import com.awakekt.awake.core.math.damp
import com.awakekt.awake.core.math.halfLifeForRate
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Position-driven locomotion animation, usable without an ECS world, scene or renderer. Measures
 * motion, smooths ground speed, ignores teleport strides, tracks take-off/rise/fall/landing phases,
 * and drives an [AnimationPlayer]. Thresholds and clip names come from [config]; no movement is
 * simulated here. The scene binding maps its authored settings to this same capability.
 *
 * @property config Clip names, movement thresholds, crossfade and optional reference speeds.
 */
class LocomotionPlayer(val config: LocomotionConfig = LocomotionConfig()) {
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var seen = false

    /** Seconds since it was last seen to move. */
    private var sinceMoved = 0f

    /** Speed across the ground, smoothed over about [SPEED_SMOOTHING] seconds. */
    private var across = 0f

    /**
     * Ground speed above which one frame's move is a teleport rather than a stride, far past any
     * run: [LocomotionConfig.runAbove] times [WARP_SPEED_MULTIPLE], and never below
     * [MIN_WARP_SPEED] so a tiny [LocomotionConfig.runAbove] still lets real movement through.
     */
    private val warpGroundSpeed = maxOf(config.runAbove * WARP_SPEED_MULTIPLE, MIN_WARP_SPEED)

    /** Whether it has left the ground and not yet landed. */
    private var airborne = false
    private var falling = false
    private var heldHeight = 0f

    /** A take-off or landing clip playing once, and the seconds it has left. */
    private var once: String? = null
    private var onceLeft = 0f

    /** The clip it last chose, or null before it has moved or stood still. */
    var playing: String? = null
        private set

    /**
     * Whether [playing] plays once and holds its last frame rather than looping: a take-off or
     * landing, or a clip for the air, whose last pose holds until it lands.
     */
    val playingOnce: Boolean
        get() = (once != null && once == playing) || (airborne && playing != null && (playing == config.jump || playing == config.fall))

    /** Speed across the ground this frame, smoothed over about [SPEED_SMOOTHING] seconds. */
    val groundSpeed: Float get() = across

    /** Whether speed-matched playback is configured on this component (either reference speed set). */
    private val matchesPlaybackToSpeed: Boolean
        get() = config.walkReferenceSpeed != null || config.runReferenceSpeed != null

    /** The authored reference speed for [clip] under speed-matched playback, or null when it plays at its authored rate. */
    private fun referenceSpeedFor(clip: String?): Float? = when {
        clip == null -> null
        clip == config.walk -> config.walkReferenceSpeed
        clip == config.run -> config.runReferenceSpeed
        else -> null
    }

    /**
     * Chooses playback from the movement to ([x], [y], [z]) over [delta] seconds. The caller runs this
     * before advancing [player]'s clock. It selects only clips the library contains, crossfades on
     * changes and optionally scales walk/run playback by ground speed over authored reference speed.
     *
     * [grounded] is the caller's ground contact, or null to infer airborne motion from height. A
     * non-positive step leaves all state and playback alone. The first observation initializes the
     * position without counting the spawn offset as movement. Keep one instance per moving actor.
     */
    fun update(player: AnimationPlayer, x: Float, y: Float, z: Float, delta: Float, grounded: Boolean? = null) {
        if (delta <= 0f) return
        val wanted = clipFor(player, x, y, z, delta, grounded)
        if (wanted != null && wanted != playing && wanted in player.clipEntries) {
            playing = wanted
            val playback = if (playingOnce) AnimationPlayback.Once else AnimationPlayback.Loop
            player.crossFadeTo(wanted, config.crossFade, playback)
        }
        if (matchesPlaybackToSpeed) {
            val reference = referenceSpeedFor(playing)
            player.speed = if (reference != null) groundSpeed / reference else 1f
        }
    }

    /**
     * The clip for how it moved to world ([x], [y], [z]) over the last [delta] seconds, or null when
     * this frame tells nothing new: its first, or a pause between a fixed-rate mover's steps.
     */
    private fun clipFor(
        player: AnimationPlayer,
        x: Float,
        y: Float,
        z: Float,
        delta: Float,
        grounded: Boolean? = null,
    ): String? {
        val elapsed = sinceMoved + delta
        val stayedPut = x == lastX && y == lastY && z == lastZ
        if (seen && stayedPut && elapsed < STILL_AFTER) {
            sinceMoved = elapsed
            return null
        }
        val first = !seen
        val dx = x - lastX
        val dz = z - lastZ
        val vertical = (y - lastY) / elapsed
        lastX = x
        lastY = y
        lastZ = z
        sinceMoved = 0f
        seen = true
        if (!first) {
            val groundDelta2 = dx * dx + dz * dz
            across = when {
                // Staying put this long is a stop, which the smoothing would only blur into a walk.
                stayedPut -> 0f
                // A single frame's move too fast to be a stride is a teleport, not locomotion: a
                // controller reconcile, a respawn, a leash home. Folding it into the smoothed speed
                // would switch the clip to a run — and, under speed-matched playback, race it — for
                // the fraction of a second the smoothing takes to bleed the spike off. Ignore it;
                // lastX/Y/Z already advanced to where it landed, so the next frame measures the real
                // speed from there. Compared as distance against [warpGroundSpeed] times [elapsed] so a
                // vanishing step never divides.
                groundDelta2 > (warpGroundSpeed * elapsed) * (warpGroundSpeed * elapsed) -> across
                else -> damp(across, sqrt(groundDelta2) / elapsed, SPEED_HALF_LIFE, elapsed)
            }
        }
        return if (first) null else choose(across, vertical, elapsed, grounded, player)
    }

    private fun choose(across: Float, vertical: Float, elapsed: Float, grounded: Boolean?, player: AnimationPlayer): String? {
        val wasAirborne = airborne
        if (grounded != null) airborne = !grounded else trackAirborne(vertical, config.airborneAbove, elapsed)
        when {
            airborne && !wasAirborne -> playOnce(config.takeOff, player)
            !airborne && wasAirborne -> playOnce(config.land, player)
            else -> onceLeft -= elapsed
        }
        // A landing gives way as soon as it walks off; a take-off plays out while it rises.
        val walkingAway = !airborne && across > config.walkAbove
        if (onceLeft <= 0f || walkingAway) once = null
        return once ?: loopFor(across, vertical)
    }

    private fun loopFor(across: Float, vertical: Float): String? = when {
        airborne && vertical < 0f -> config.fall ?: config.jump
        airborne -> config.jump
        across > config.runAbove -> config.run
        across > config.walkAbove -> config.walk
        else -> config.idle
    }

    private fun playOnce(clip: String?, player: AnimationPlayer) {
        val duration = player.clipEntries[clip]?.duration ?: 0f
        once = clip.takeIf { duration > 0f }
        onceLeft = duration
    }

    /** Leaves the ground above [airborneAbove]; lands when a fall stops or its height holds. */
    private fun trackAirborne(vertical: Float, airborneAbove: Float, elapsed: Float) {
        if (abs(vertical) > airborneAbove) airborne = true
        if (!airborne) return
        if (vertical < -STILL) falling = true
        heldHeight = if (abs(vertical) < STILL) heldHeight + elapsed else 0f
        if ((falling && vertical >= -STILL) || heldHeight >= LANDED_AFTER) {
            airborne = false
            falling = false
            heldHeight = 0f
        }
    }
}

/** Seconds without motion before a frame counts as standing still. */
private const val STILL_AFTER = 0.1f

/** Seconds over which the speed across the ground is smoothed: two frames slower stay a run. */
private const val SPEED_SMOOTHING = 0.1f

/** Equivalent to the original exp(-elapsed / SPEED_SMOOTHING), computed once. */
private val SPEED_HALF_LIFE = halfLifeForRate(1f / SPEED_SMOOTHING)

/** Multiple of [LocomotionConfig.runAbove] above which one frame's move is a teleport, not a stride. */
private const val WARP_SPEED_MULTIPLE = 4f

/** Floor for the teleport threshold, in units per second, far past any character's run. */
private const val MIN_WARP_SPEED = 30f

/** Vertical units per second below which it neither rises nor falls. */
private const val STILL = 0.1f

/** Seconds holding its height that count as landed. */
private const val LANDED_AFTER = 0.25f
