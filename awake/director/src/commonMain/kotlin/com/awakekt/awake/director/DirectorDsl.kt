/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.director

import com.awakekt.awake.core.math.EasingCurve

/**
 * Idiomatic Kotlin DSL builder for constructing [DirectorScript] instances cleanly in game code,
 * trailer harnesses, and tests.
 */
class DirectorScriptBuilder(
    private val name: String,
) {
    /** Tempo in beats per minute for beat-synced timing. */
    var bpm: Float = BeatClock.DEFAULT_BPM

    /** Beats per bar (default `4`). */
    var beatsPerBar: Int = BeatClock.DEFAULT_BEATS_PER_BAR

    /** Timeline offset in seconds where beat `0` lands. */
    var beatOffsetSeconds: Float = 0f

    /** Deterministic seed for handheld breathing and shake noise. */
    var seed: Int = DEFAULT_SEED

    /** Optional relative path to a recorded `*.take.json` gameplay input take. */
    var inputTake: String? = null

    /** Optional fixed framing aspect ratio (`width / height`). */
    var aspectRatio: Float? = null

    private var musicTrack: MusicTrackSpec? = null
    private val shots = mutableListOf<ShotSpec>()
    private val lighting = mutableListOf<LightingCueSpec>()
    private val audio = mutableListOf<AudioCueSpec>()
    private val actions = mutableListOf<ActionCueSpec>()

    /** Configures the script's background soundtrack. */
    fun music(
        path: String,
        volume: Float = 0.65f,
        startOffsetSeconds: Float = 0f,
        fadeInSeconds: Float = 0.5f,
        fadeOutSeconds: Float = 1.0f,
        loop: Boolean = true,
    ) {
        musicTrack = MusicTrackSpec(
            path = path,
            volume = volume,
            startOffsetSeconds = startOffsetSeconds,
            fadeInSeconds = fadeInSeconds,
            fadeOutSeconds = fadeOutSeconds,
            loop = loop,
        )
    }

    /** Appends a configured [ShotSpec] to the shot timeline. */
    fun shot(id: String, block: ShotBuilder.() -> Unit) {
        shots += ShotBuilder(id).apply(block).build()
    }

    /** Appends a lighting or atmosphere cue to [DirectorScript.lightingTrack]. */
    fun lighting(cue: LightingCueSpec) {
        lighting += cue
    }

    /** Appends an audio or music control cue to [DirectorScript.audioTrack]. */
    fun audio(cue: AudioCueSpec) {
        audio += cue
    }

    /** Appends an action, choreography, trauma, or speed-ramp cue to [DirectorScript.actionTrack]. */
    fun action(cue: ActionCueSpec) {
        actions += cue
    }

    /** Builds the immutable [DirectorScript]. */
    fun build(): DirectorScript = DirectorScript(
        name = name,
        clock = BeatClock(bpm = bpm, beatsPerBar = beatsPerBar, offsetSeconds = beatOffsetSeconds),
        seed = seed,
        inputTake = inputTake,
        aspectRatio = aspectRatio,
        music = musicTrack,
        shots = shots.toList(),
        lightingTrack = lighting.toList(),
        audioTrack = audio.toList(),
        actionTrack = actions.toList(),
    )

    private companion object {
        const val DEFAULT_SEED = 1337
    }
}

/**
 * Builder for an individual [ShotSpec] inside [directorScript].
 */
class ShotBuilder(
    private val id: String,
) {
    /** Shot duration (seconds or beats). */
    var duration: DurationRef = DurationRef.seconds(3f)

    /** Camera rig behavior. */
    var rig: ShotRigSpec = ShotRigSpec.Static()

    /** Optical lens settings. */
    var lens: LensSpec = LensSpec()

    /** Handheld and trauma feel settings. */
    var feel: CameraFeelSpec = CameraFeelSpec()

    /** Transition into this shot. */
    var transitionIn: ShotTransitionSpec = ShotTransitionSpec.HardCut

    /** Optional terrain/obstacle collision guard. */
    var collision: ShotCollisionSpec? = ShotCollisionSpec()

    /** Whether HUD canvas renders during this shot (`false` = clean cinematic feed). */
    var hudVisible: Boolean = false

    /** Optional letterbox aspect ratio (e.g. `2.39f`). */
    var letterboxAspect: Float? = null

    /** Optional per-shot atmosphere/lighting mood override. */
    var atmosphere: ShotAtmosphereSpec? = null

    /** Optional director note. */
    var notes: String? = null

    /** Sets shot duration in seconds. */
    fun seconds(value: Float) {
        duration = DurationRef.seconds(value)
    }

    /** Sets shot duration in musical beats. */
    fun beats(value: Float) {
        duration = DurationRef.beats(value)
    }

    /** Configures an orbital shot around [subject]. */
    @Suppress("LongParameterList")
    fun orbit(
        subject: String,
        shotSize: ShotSize = ShotSize.Medium,
        angle: AnglePreset = AnglePreset.LowHero,
        startAzimuthDeg: Float = -35f,
        endAzimuthDeg: Float = 35f,
        startRadiusMultiplier: Float = 1f,
        endRadiusMultiplier: Float = 1f,
        composition: ScreenComposition = ScreenComposition.Center,
        easing: EasingCurve = EasingCurve.EaseInOutSine,
    ) {
        rig = ShotRigSpec.Orbit(
            framing = FramingSpec(
                subjects = listOf(subject),
                shotSize = shotSize,
                angle = angle,
                composition = composition,
            ),
            startAzimuthDeg = startAzimuthDeg,
            endAzimuthDeg = endAzimuthDeg,
            startRadiusMultiplier = startRadiusMultiplier,
            endRadiusMultiplier = endRadiusMultiplier,
            easing = easing,
        )
    }

    /** Configures a rising or descending crane shot over [subject]. */
    fun crane(
        subject: String,
        shotSize: ShotSize = ShotSize.Wide,
        startHeightDelta: Float = -1f,
        endHeightDelta: Float = 5f,
        startAzimuthDeg: Float = -20f,
        endAzimuthDeg: Float = 20f,
        easing: EasingCurve = EasingCurve.EaseInOutCubic,
    ) {
        rig = ShotRigSpec.Crane(
            framing = FramingSpec(subjects = listOf(subject), shotSize = shotSize),
            startHeightDelta = startHeightDelta,
            endHeightDelta = endHeightDelta,
            startAzimuthDeg = startAzimuthDeg,
            endAzimuthDeg = endAzimuthDeg,
            easing = easing,
        )
    }

    /** Configures a classic Vertigo / dolly-zoom shot on [subject]. */
    fun dollyZoom(
        subject: String,
        startFocalLengthMm: Float = 28f,
        endFocalLengthMm: Float = 85f,
        shotSize: ShotSize = ShotSize.Medium,
        easing: EasingCurve = EasingCurve.EaseInOutCubic,
    ) {
        rig = ShotRigSpec.DollyZoom(
            framing = FramingSpec(subjects = listOf(subject), shotSize = shotSize),
            startFocalLengthMm = startFocalLengthMm,
            endFocalLengthMm = endFocalLengthMm,
            easing = easing,
        )
    }

    /** Builds the immutable [ShotSpec]. */
    fun build(): ShotSpec = ShotSpec(
        id = id,
        duration = duration,
        rig = rig,
        lens = lens,
        feel = feel,
        transitionIn = transitionIn,
        collision = collision,
        hudVisible = hudVisible,
        letterboxAspect = letterboxAspect,
        atmosphere = atmosphere,
        notes = notes,
    )
}

/**
 * Top-level DSL entry point constructing a [DirectorScript].
 */
fun directorScript(name: String = "trailer", block: DirectorScriptBuilder.() -> Unit): DirectorScript =
    DirectorScriptBuilder(name).apply(block).build()
