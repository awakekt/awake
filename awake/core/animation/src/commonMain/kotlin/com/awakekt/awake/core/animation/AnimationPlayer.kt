/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

/**
 * Immutable skeletal-animation data shared by every instance of one animated asset.
 *
 * @property skeleton The shared bone hierarchy defining bone structure and bind poses.
 * @property clips Map of clip identifiers to their corresponding [AnimationClip] data.
 */
class AnimationLibrary(
    val skeleton: Skeleton,
    val clips: Map<String, AnimationClip>,
) {
    init {
        require(clips.isNotEmpty()) { "An AnimationLibrary needs at least one clip." }
        clips.forEach { (name, clip) ->
            require(name.isNotBlank()) { "Animation clip IDs must not be blank." }
            require(clip.channels.all { it.targetBone in skeleton.bones.indices }) {
                "Animation clip '$name' targets a bone outside this library's skeleton."
            }
        }
    }
}

/**
 * Playback repetition mode for animation clips.
 */
enum class AnimationPlayback {
    /** Repeats the animation continuously from the beginning upon reaching its duration. */
    Loop,

    /** Plays the animation once and stops at the final frame. */
    Once,
}

/**
 * Per-instance playback state over one immutable [AnimationLibrary].
 *
 * The player is deliberately format-neutral: glTF, an offline FBX cooker, or an editor can
 * construct the same [AnimationLibrary] without exposing source-format types to playback code.
 *
 * @param library Animation library providing the shared skeleton and animation clips.
 */
class AnimationPlayer(
    private val library: AnimationLibrary,
) {
    /** The immutable skeleton shared by this player's animation poses. */
    val skeleton: Skeleton get() = library.skeleton

    private val currentPose = AnimationPose(library.skeleton)
    private val outgoingPose = AnimationPose(library.skeleton)
    private val blendedPose = AnimationPose(library.skeleton)

    private var outgoingActive = false
    private var blendElapsedSeconds = 0f
    private var blendDurationSeconds = 0f
    private var elapsedSeconds = 0f
    private var playback = AnimationPlayback.Loop

    /** Playback rate multiplier where 1.0 represents standard real-time playback. */
    var speed: Float = 1f
        set(value) {
            require(value >= 0f) { "Animation speed must be non-negative." }
            field = value
        }

    /** The identifier of the currently active animation clip, or `null` if no clip is selected. */
    var activeClipId: String? = null
        private set

    /** Whether the animation player is currently advancing playback time. */
    var isPlaying: Boolean = false
        private set

    /** Whether single-shot playback has completed its duration. */
    val isFinished: Boolean
        get() = !isPlaying && activeClipId != null && playback == AnimationPlayback.Once

    /** Current playback position in seconds within the active clip. */
    val time: Float get() = elapsedSeconds

    /** The currently active [AnimationClip], or `null` if no clip is selected. */
    val currentClip: AnimationClip? get() = activeClipId?.let { library.clips[it] }

    /** List of all animation clips available in the underlying library. */
    val clips: List<AnimationClip> get() = library.clips.values.toList()

    /** Map of clip names to animation clips available in the underlying library. */
    val clipEntries: Map<String, AnimationClip> get() = library.clips

    /** Pauses animation playback while maintaining the current playback position. */
    fun pause() {
        isPlaying = false
    }

    /** Resumes animation playback from the current position if a clip is active. */
    fun resume() {
        if (activeClipId != null) {
            isPlaying = true
        }
    }

    /**
     * Seeks playback to a specific timestamp within the currently active clip.
     *
     * @param timeSeconds Target playback position in seconds, clamped between 0 and clip duration.
     */
    fun seek(timeSeconds: Float) {
        val clipId = activeClipId ?: return
        val clip = clip(clipId)
        elapsedSeconds = timeSeconds.coerceIn(0f, clip.duration)
    }

    /**
     * Starts playback of the animation clip identified by [clipId].
     *
     * @param clipId Identifier of the animation clip to play.
     * @param playback Playback repetition mode (looping or single-shot).
     * @param restart If `false` and [clipId] is already active, continues playback without restarting.
     */
    fun play(
        clipId: String,
        playback: AnimationPlayback = AnimationPlayback.Loop,
        restart: Boolean = true,
    ) {
        val sameClip = activeClipId == clipId
        clip(clipId)
        if (sameClip && !restart) return
        activeClipId = clipId
        this.playback = playback
        elapsedSeconds = 0f
        outgoingActive = false
        isPlaying = true
    }

    /**
     * Crossfades from the current pose to a target animation clip over [durationSeconds].
     *
     * @param clipId Identifier of the target animation clip to fade into.
     * @param durationSeconds Transition duration in seconds over which the blend occurs.
     * @param playback Playback repetition mode for the incoming clip.
     */
    fun crossFadeTo(
        clipId: String,
        durationSeconds: Float,
        playback: AnimationPlayback = AnimationPlayback.Loop,
    ) {
        require(durationSeconds >= 0f) { "Crossfade duration must be non-negative." }
        if (activeClipId == null || durationSeconds == 0f) {
            play(clipId, playback)
            return
        }
        if (activeClipId == clipId) return
        clip(clipId)
        if (outgoingActive) {
            outgoingPose.copyFrom(blendedPose)
        } else {
            outgoingPose.copyFrom(currentPose)
        }
        outgoingActive = true
        blendElapsedSeconds = 0f
        blendDurationSeconds = durationSeconds
        activeClipId = clipId
        this.playback = playback
        elapsedSeconds = 0f
        isPlaying = true
    }

    /**
     * Advances once and returns a reusable pose owned by this player.
     * The caller must consume it before its next [update] call.
     *
     * @param deltaSeconds Elapsed time step in seconds.
     * @return Current sampled and blended [AnimationPose].
     */
    fun update(deltaSeconds: Float): AnimationPose {
        require(deltaSeconds >= 0f) { "Animation delta must be non-negative." }
        val clipId = activeClipId ?: return currentPose
        val clip = clip(clipId)
        if (isPlaying) elapsedSeconds = advanceTime(clip, deltaSeconds * speed)

        currentPose.resetToBindPose()
        currentPose.sample(clip, elapsedSeconds)

        return if (!outgoingActive) {
            currentPose
        } else {
            blendElapsedSeconds = (blendElapsedSeconds + deltaSeconds * speed).coerceAtMost(blendDurationSeconds)
            val weight = blendElapsedSeconds / blendDurationSeconds
            blendedPose.copyFrom(outgoingPose)
            blendedPose.blend(currentPose, weight)
            if (weight >= 1f) outgoingActive = false
            blendedPose
        }
    }

    private fun advanceTime(clip: AnimationClip, deltaSeconds: Float): Float {
        val duration = clip.duration
        if (duration <= 0f) {
            isPlaying = playback == AnimationPlayback.Loop
            return 0f
        }
        val next = elapsedSeconds + deltaSeconds
        return when (playback) {
            AnimationPlayback.Loop -> next % duration
            AnimationPlayback.Once -> {
                if (next >= duration) {
                    isPlaying = false
                    duration
                } else {
                    next
                }
            }
        }
    }

    private fun clip(id: String): AnimationClip =
        requireNotNull(library.clips[id]) {
            "Unknown animation clip '$id'. Available clips: ${library.clips.keys.sorted().joinToString()}."
        }
}
