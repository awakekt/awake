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
 * motion, smooths planar and 3D speeds, ignores teleport strides, tracks grounded/airborne phases
 * or 3D free-motion clips, and drives an [AnimationPlayer]. Thresholds and clip names come from
 * [config]; no movement is simulated here. The scene binding maps its authored settings to this
 * same capability.
 *
 * @property config Clip names, movement thresholds, crossfade and optional reference speeds.
 */
class LocomotionPlayer(val config: LocomotionConfig = LocomotionConfig()) {
    private val managedClips: Set<String> = setOfNotNull(
        config.idle,
        config.walk,
        config.run,
        config.jump,
        config.takeOff,
        config.fall,
        config.land,
        config.free?.idle,
        config.free?.move,
        config.free?.fast,
        config.free?.ascend,
        config.free?.descend,
    )

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var seen = false
    private var inFreeMotion = false

    /** Seconds since it was last seen to move. */
    private var sinceMoved = 0f

    /** Speed across the ground, smoothed over about [SPEED_SMOOTHING] seconds. */
    private var across = 0f

    /** Full 3D travel speed and signed vertical speed, smoothed over about [SPEED_SMOOTHING] seconds. */
    private var travelSpeed = 0f
    private var verticalSpeed = 0f

    /**
     * Speed above which one frame's move is a teleport rather than locomotion, far past any configured
     * run or fast travel: the greater of [LocomotionConfig.runAbove] and [FreeLocomotionConfig.fastAbove]
     * times [WARP_SPEED_MULTIPLE], and never below [MIN_WARP_SPEED] so a tiny threshold still lets real
     * movement through.
     */
    private val warpSpeed = maxOf(
        maxOf(config.runAbove, config.free?.fastAbove ?: 0f) * WARP_SPEED_MULTIPLE,
        MIN_WARP_SPEED,
    )

    /** Whether it has left the ground and not yet landed. */
    private var airborne = false
    private var falling = false
    private var heldHeight = 0f

    /** A take-off or landing clip playing once, and the seconds it has left. */
    private var once: String? = null
    private var onceLeft = 0f

    /** Latest playable locomotion clip chosen from tracked motion, retained across [STILL_AFTER] sub-steps. */
    private var desired: String? = null

    /** The clip it last chose, or null before it has moved or stood still. */
    var playing: String? = null
        private set

    private var playingAsOnce = false

    /**
     * Whether [playing] plays once and holds its last frame rather than looping: a take-off or
     * landing, or a clip for the air, whose last pose holds until it lands.
     */
    val playingOnce: Boolean
        get() = playingAsOnce

    /** Speed across the ground this frame, smoothed over about [SPEED_SMOOTHING] seconds. */
    val groundSpeed: Float get() = across

    /** Full 3D travel speed this frame, smoothed over about [SPEED_SMOOTHING] seconds. */
    val speed: Float get() = travelSpeed

    private val hasGroundedReferenceSpeed = config.walkReferenceSpeed != null || config.runReferenceSpeed != null
    private val hasFreeReferenceSpeed = config.free?.moveReferenceSpeed != null || config.free?.fastReferenceSpeed != null

    /** Whether speed-matched playback is configured on this component (any reference speed set). */
    private val matchesPlaybackToSpeed = hasGroundedReferenceSpeed || hasFreeReferenceSpeed

    /**
     * Chooses playback from the movement to ([x], [y], [z]) over [delta] seconds. The caller runs this
     * before advancing [player]'s clock. It selects only clips the library contains, crossfades on
     * changes and optionally scales movement playback by travel speed over authored reference speed.
     *
     * [grounded] is the caller's ground contact, or null to infer airborne motion from height. When
     * [freeMotion] is true, 3D travel and vertical speeds drive [LocomotionConfig.free] (or fall back
     * to [LocomotionConfig.idle], [LocomotionConfig.walk] and [LocomotionConfig.run] by 3D speed)
     * without entering airborne phases. A non-positive or non-finite step (or non-finite coordinates)
     * leaves all state and playback alone. The first observation initializes the position without
     * counting the spawn offset as movement. Keep one instance per moving actor. While an unfinished
     * clip outside [config] is active on [player], motion is still tracked so speed and phase stay
     * current, while clip selection and [AnimationPlayer.speed] yield until that clip finishes or
     * playback returns to a [config] clip. With any reference speed configured, this controls
     * [AnimationPlayer.speed] from [AnimationPlayer.activeClipId] whenever it is not yielding; leave
     * all reference speeds null when another system controls playback rate on locomotion clips too.
     */
    fun update(
        player: AnimationPlayer,
        x: Float,
        y: Float,
        z: Float,
        delta: Float,
        grounded: Boolean? = null,
        freeMotion: Boolean = false,
    ) {
        if (!isValidStep(x, y, z, delta)) return
        if (player.activeClipId != playing || (player.isFinished && !playingAsOnce)) {
            playing = null
            playingAsOnce = false
        }
        trackMotion(player, x, y, z, delta, grounded, freeMotion)
        val active = player.activeClipId
        if (active != null && active !in managedClips && !player.isFinished) return
        syncPlayback(player, freeMotion)
        applyPlaybackSpeed(player, freeMotion)
    }

    /** [update] as it was before `freeMotion`: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun update(
        player: AnimationPlayer,
        x: Float,
        y: Float,
        z: Float,
        delta: Float,
        grounded: Boolean? = null,
    ) {
        update(player, x, y, z, delta, grounded, freeMotion = false)
    }

    private fun syncPlayback(player: AnimationPlayer, freeMotion: Boolean) {
        val wanted = desired
        if (wanted == null || wanted !in player.clipEntries) return
        val isAirClip = airborne && (wanted == config.jump || wanted == config.fall)
        val wantedOnce = once == wanted || isAirClip
        val modeFlipped = wantedOnce != playingAsOnce
        if (wanted != playing || modeFlipped) {
            playing = wanted
            playingAsOnce = wantedOnce
            val playback = if (wantedOnce) AnimationPlayback.Once else AnimationPlayback.Loop
            if (player.activeClipId != wanted) {
                val fade = if (freeMotion) config.free?.crossFade ?: config.crossFade else config.crossFade
                player.crossFadeTo(wanted, fade, playback)
            } else if (player.isFinished || modeFlipped) {
                player.play(wanted, playback)
            }
        }
    }

    private fun applyPlaybackSpeed(player: AnimationPlayer, freeMotion: Boolean) {
        if (!matchesPlaybackToSpeed) return
        val free = if (freeMotion) config.free else null
        val slow = if (free != null) free.move else config.walk
        val fast = if (free != null) free.fast else config.run
        val slowRef = if (free != null) free.moveReferenceSpeed else config.walkReferenceSpeed
        val fastRef = if (free != null) free.fastReferenceSpeed else config.runReferenceSpeed
        val reference = when (val clip = player.activeClipId) {
            null -> null
            slow -> slowRef ?: fastRef.takeIf { clip == fast }
            fast -> fastRef
            else -> null
        }
        val measured = if (freeMotion) travelSpeed else across
        player.speed = if (reference != null) measured / reference else 1f
    }

    /**
     * Tracks motion to world ([x], [y], [z]) over the last [delta] seconds and updates [desired]
     * whenever the frame carries a new motion sample or a mode switch beyond the initial observation
     * or a brief pause between a fixed-rate mover's steps.
     */
    private fun trackMotion(
        player: AnimationPlayer,
        x: Float,
        y: Float,
        z: Float,
        delta: Float,
        grounded: Boolean? = null,
        freeMotion: Boolean = false,
    ) {
        if (!seen) {
            inFreeMotion = freeMotion
            lastX = x
            lastY = y
            lastZ = z
            seen = true
            return
        }
        val modeChanged = freeMotion != inFreeMotion
        inFreeMotion = freeMotion
        val elapsed = sinceMoved + delta
        val dx = x - lastX
        val dy = y - lastY
        val dz = z - lastZ
        val stayedPut = x == lastX && y == lastY && z == lastZ
        val holdingStep = stayedPut && elapsed < STILL_AFTER
        if (holdingStep) {
            sinceMoved = elapsed
            if (!modeChanged) return
        } else {
            lastX = x
            lastY = y
            lastZ = z
            sinceMoved = 0f
            updateSmoothedSpeeds(dx, dy, dz, elapsed, stayedPut)
        }
        val vertical = if (holdingStep) verticalSpeed else dy / elapsed
        val chosen = if (freeMotion) chooseFree() else chooseGrounded(vertical, elapsed, grounded, modeChanged, player)
        if (chosen == null || chosen in player.clipEntries) desired = chosen
    }

    private fun updateSmoothedSpeeds(dx: Float, dy: Float, dz: Float, elapsed: Float, stayedPut: Boolean) {
        // Staying put past [STILL_AFTER] is a stop, which the smoothing would only blur into movement.
        if (stayedPut) {
            across = 0f
            travelSpeed = 0f
            verticalSpeed = 0f
            return
        }
        // A single frame's move too fast to be locomotion is a teleport: a controller reconcile, a
        // respawn, a leash home. Folding it into the smoothed speeds would switch clips — and, under
        // speed-matched playback, race them — while the spike bleeds off. Ignore it; lastX/Y/Z already
        // advanced to where it landed, so the next frame measures real speed from there. Planar speed
        // checks planar distance so a fast terminal-velocity fall never rejects horizontal motion.
        val maxStep = warpSpeed * elapsed
        val maxStep2 = maxStep * maxStep
        val groundDelta2 = dx * dx + dz * dz
        if (groundDelta2 <= maxStep2) {
            across = damp(across, sqrt(groundDelta2) / elapsed, SPEED_HALF_LIFE, elapsed)
        }
        val delta3d2 = groundDelta2 + dy * dy
        if (delta3d2 <= maxStep2) {
            travelSpeed = damp(travelSpeed, sqrt(delta3d2) / elapsed, SPEED_HALF_LIFE, elapsed)
            verticalSpeed = damp(verticalSpeed, dy / elapsed, SPEED_HALF_LIFE, elapsed)
        }
    }

    private fun chooseFree(): String? {
        airborne = false
        falling = false
        heldHeight = 0f
        once = null
        onceLeft = 0f
        val free = config.free ?: return when {
            travelSpeed > config.runAbove -> config.run
            travelSpeed > config.walkAbove -> config.walk
            else -> config.idle
        }
        val verticalClip = if (verticalSpeed > 0f) free.ascend else free.descend
        return when {
            free.fast != null && travelSpeed > free.fastAbove -> free.fast
            verticalClip != null && abs(verticalSpeed) > free.verticalAbove && abs(verticalSpeed) >= across -> verticalClip
            travelSpeed > free.moveAbove -> free.move ?: free.idle
            else -> free.idle
        }
    }

    private fun chooseGrounded(
        vertical: Float,
        elapsed: Float,
        grounded: Boolean?,
        modeChanged: Boolean,
        player: AnimationPlayer,
    ): String? {
        if (modeChanged) {
            once = null
            onceLeft = 0f
            heldHeight = 0f
            airborne = if (grounded != null) !grounded else abs(vertical) > config.airborneAbove
            falling = airborne && vertical < -STILL
            return loopFor(vertical)
        }
        val wasAirborne = airborne
        if (grounded != null) airborne = !grounded else trackAirborne(vertical, elapsed)
        if (airborne != wasAirborne) {
            val transition = if (airborne) config.takeOff else config.land
            val duration = player.clipEntries[transition]?.duration ?: 0f
            once = transition.takeIf { duration > 0f }
            onceLeft = duration
        } else {
            onceLeft -= elapsed
        }
        // A landing gives way as soon as it walks off; a take-off plays out while it rises.
        val walkingAway = !airborne && across > config.walkAbove
        if (onceLeft <= 0f || walkingAway) once = null
        return once ?: loopFor(vertical)
    }

    private fun loopFor(vertical: Float): String? = when {
        airborne && vertical < 0f -> config.fall ?: config.jump
        airborne -> config.jump
        across > config.runAbove -> config.run
        across > config.walkAbove -> config.walk
        else -> config.idle
    }

    /** Leaves the ground above [LocomotionConfig.airborneAbove]; lands when a fall stops or its height holds. */
    private fun trackAirborne(vertical: Float, elapsed: Float) {
        if (abs(vertical) > config.airborneAbove) airborne = true
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

private fun isValidStep(x: Float, y: Float, z: Float, delta: Float): Boolean {
    val finitePosition = x.isFinite() && y.isFinite() && z.isFinite()
    return finitePosition && delta.isFinite() && delta > 0f
}
