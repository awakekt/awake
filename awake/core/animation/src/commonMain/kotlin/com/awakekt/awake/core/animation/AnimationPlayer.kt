/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

/** Immutable skeletal-animation data shared by every instance of one animated asset. */
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

enum class AnimationPlayback { Loop, Once }

/** Per-instance playback state over one immutable [AnimationLibrary].
 *
 * The player is deliberately format-neutral: glTF, an offline FBX cooker, or an editor can
 * construct the same [AnimationLibrary] without exposing source-format types to playback code.
 */
class AnimationPlayer(
    private val library: AnimationLibrary,
) {
    private val currentPose = AnimationPose(library.skeleton)
    private val outgoingPose = AnimationPose(library.skeleton)
    private val blendedPose = AnimationPose(library.skeleton)

    private var outgoingActive = false
    private var blendElapsedSeconds = 0f
    private var blendDurationSeconds = 0f
    private var elapsedSeconds = 0f
    private var playback = AnimationPlayback.Loop

    var speed: Float = 1f
        set(value) {
            require(value >= 0f) { "Animation speed must be non-negative." }
            field = value
        }

    var activeClipId: String? = null
        private set

    var isPlaying: Boolean = false
        private set

    val isFinished: Boolean
        get() = !isPlaying && activeClipId != null && playback == AnimationPlayback.Once

    val time: Float get() = elapsedSeconds

    val currentClip: AnimationClip? get() = activeClipId?.let { library.clips[it] }

    val clips: List<AnimationClip> get() = library.clips.values.toList()

    val clipEntries: Map<String, AnimationClip> get() = library.clips

    fun pause() {
        isPlaying = false
    }

    fun resume() {
        if (activeClipId != null) {
            isPlaying = true
        }
    }

    fun seek(timeSeconds: Float) {
        val clipId = activeClipId ?: return
        val clip = clip(clipId)
        elapsedSeconds = timeSeconds.coerceIn(0f, clip.duration)
    }

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

    /** Advances once and returns a reusable pose owned by this player. The caller must consume
     * it before its next [update] call. */
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
