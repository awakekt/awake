/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.director

import com.awakekt.awake.core.camera.CameraCollision
import com.awakekt.awake.core.camera.CameraOptics
import com.awakekt.awake.core.math.EasingCurve
import com.awakekt.awake.core.math.Vec3f
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.PI

/**
 * Serializable 3D vector for director scripts, independent of `:awake:scene:document`.
 *
 * @property x X coordinate.
 * @property y Y coordinate.
 * @property z Z coordinate.
 */
@Serializable
data class DirectorVec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
) {
    /** Copies this vector into mutable [out] without allocating. */
    fun copyTo(out: Vec3f): Vec3f = out.set(x, y, z)

    /** Allocates a mutable [Vec3f] copy for setup/spline construction. */
    fun toVec3f(): Vec3f = Vec3f(x, y, z)

    /** Common vector constants. */
    companion object {
        /** Zero vector `(0, 0, 0)`. */
        val Zero: DirectorVec3 = DirectorVec3(0f, 0f, 0f)
    }
}

/**
 * Serializable linear RGB color for lighting, fog, and sky choreography.
 *
 * @property r Red channel (`>= 0f`).
 * @property g Green channel (`>= 0f`).
 * @property b Blue channel (`>= 0f`).
 */
@Serializable
data class DirectorColor(
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
) {
    /** Copies RGB channels into mutable [out]. */
    fun copyTo(out: Vec3f): Vec3f = out.set(r, g, b)
}

/**
 * Lens optics configuration for a camera shot.
 *
 * Supply either [focalLengthMm] (preferred — physical 35mm cinema lens equivalent such as `24`,
 * `35`, `50`, `85`) OR [fovYDegrees]. When both are omitted, defaults to a cinematic `35mm` lens.
 *
 * @property focalLengthMm Equivalent 35mm full-frame lens focal length in millimeters.
 * @property fovYDegrees Explicit vertical field of view in degrees (used if [focalLengthMm] is null).
 * @property near Near clipping plane distance (`> 0f`).
 * @property far Far clipping plane distance (`> near`).
 * @property rollDeg Dutch camera roll angle in degrees.
 */
@Serializable
data class LensSpec(
    val focalLengthMm: Float? = DEFAULT_FOCAL_LENGTH_MM,
    val fovYDegrees: Float? = null,
    val near: Float = DEFAULT_NEAR,
    val far: Float = DEFAULT_FAR,
    val rollDeg: Float = 0f,
) {
    /** Resolves vertical field of view in radians. */
    fun resolvedFovYRadians(): Float = when {
        focalLengthMm != null -> CameraOptics.focalLengthMmToFovYRadians(focalLengthMm)
        fovYDegrees != null -> fovYDegrees * DEG_TO_RAD
        else -> CameraOptics.focalLengthMmToFovYRadians(DEFAULT_FOCAL_LENGTH_MM)
    }

    /** Defaults for [LensSpec]. */
    companion object {
        /** Default cinematic focal length (35mm). */
        const val DEFAULT_FOCAL_LENGTH_MM: Float = 35f
        private const val DEFAULT_NEAR = 0.1f
        private const val DEFAULT_FAR = 500f
        private const val DEG_TO_RAD = (PI / 180.0).toFloat()
    }
}

/**
 * Procedural camera feel configuration applied on top of a shot rig.
 *
 * @property handheldIntensity Organic handheld breathing amount (`0f` = locked tripod, `1f` = strong handheld).
 * @property handheldFrequency Breathing oscillation frequency in Hz.
 * @property baseTrauma Continuous ambient camera shake trauma (`0f..1f`).
 * @property smoothTime Critically damped spring smooth time in seconds (`0f` = direct).
 */
@Serializable
data class CameraFeelSpec(
    val handheldIntensity: Float = 0f,
    val handheldFrequency: Float = DEFAULT_HANDHELD_FREQ,
    val baseTrauma: Float = 0f,
    val smoothTime: Float = 0f,
) {
    /** Defaults for [CameraFeelSpec]. */
    companion object {
        private const val DEFAULT_HANDHELD_FREQ = 0.7f
    }
}

/**
 * Optional collision guard preventing automated camera shots from clipping into terrain or walls.
 *
 * @property enabled Whether collision solving is active for this shot.
 * @property ground Keep camera above ground/terrain height.
 * @property groundMargin Minimum vertical clearance above terrain in world units.
 * @property terrain Pull camera forward when terrain rises between pivot and eye.
 * @property radius Swept sphere radius against obstacle layers.
 * @property obstacleLayers Physics layers that obstruct the camera.
 * @property easeOutSeconds Ease-out recovery half-life in seconds.
 */
@Serializable
data class ShotCollisionSpec(
    val enabled: Boolean = true,
    val ground: Boolean = true,
    val groundMargin: Float = DEFAULT_GROUND_MARGIN,
    val terrain: Boolean = true,
    val radius: Float = DEFAULT_RADIUS,
    val obstacleLayers: List<Int> = emptyList(),
    val easeOutSeconds: Float = DEFAULT_EASE_OUT,
) {
    /** Converts into a core [CameraCollision] configuration when [enabled], else `null`. */
    fun toCameraCollision(): CameraCollision? = if (!enabled) {
        null
    } else {
        CameraCollision(
            ground = ground,
            groundMargin = groundMargin,
            terrain = terrain,
            radius = radius,
            easeOutSeconds = easeOutSeconds,
            obstacleLayers = obstacleLayers,
        )
    }

    /** Defaults for [ShotCollisionSpec]. */
    companion object {
        private const val DEFAULT_GROUND_MARGIN = 0.35f
        private const val DEFAULT_RADIUS = 0.25f
        private const val DEFAULT_EASE_OUT = 0.15f
    }
}

/**
 * How a shot transitions in from the previous shot.
 */
@Serializable
sealed interface ShotTransitionSpec {
    /** Instant frame-accurate hard cut (standard for beat-locked trailer editing). */
    @Serializable
    @SerialName("cut")
    data object HardCut : ShotTransitionSpec

    /**
     * Smooth spatial and optical blend from the outgoing shot over [duration].
     *
     * @property duration Blend duration in seconds or beats.
     * @property easing Interpolation curve applied to the blend weight.
     */
    @Serializable
    @SerialName("blend")
    data class SmoothBlend(
        val duration: DurationRef = DurationRef.seconds(DEFAULT_BLEND_SECONDS),
        val easing: EasingCurve = EasingCurve.EaseInOutCubic,
    ) : ShotTransitionSpec

    /**
     * Dip to black at the cut boundary, then fade back up into the incoming shot.
     *
     * @property duration Total fade-in duration from black into this shot.
     * @property fadeOutPreviousDuration Optional fade-out duration at the tail of the previous shot.
     */
    @Serializable
    @SerialName("fade_black")
    data class FadeThroughBlack(
        val duration: DurationRef = DurationRef.seconds(DEFAULT_FADE_SECONDS),
        val fadeOutPreviousDuration: DurationRef = DurationRef.seconds(DEFAULT_FADE_SECONDS),
    ) : ShotTransitionSpec

    /** Default transition durations. */
    companion object {
        private const val DEFAULT_BLEND_SECONDS = 0.6f
        private const val DEFAULT_FADE_SECONDS = 0.4f
    }
}

/**
 * How a spline rail camera aims along its path.
 */
@Serializable
enum class SplineLookMode {
    /** Look continuously at the named subject(s) in [ShotRigSpec.SplineRail.framing]. */
    @SerialName("subject")
    Subject,

    /** Look ahead along the spline's forward tangent (rollercoaster / flythrough feel). */
    @SerialName("forward_tangent")
    ForwardTangent,

    /** Look continuously at a fixed world-space point. */
    @SerialName("fixed_point")
    FixedPoint,
}

/**
 * Polymorphic camera rig behaviors defining how a shot moves over its normalized progress `0..1`.
 */
@Serializable
sealed interface ShotRigSpec {
    /**
     * Locked or tripod shot framed either semantically via [framing] or explicitly via [eye]/[center].
     */
    @Serializable
    @SerialName("static")
    data class Static(
        val framing: FramingSpec? = null,
        val eye: DirectorVec3? = null,
        val center: DirectorVec3? = null,
    ) : ShotRigSpec

    /**
     * Smooth orbital arc around the framed subject(s) or anchor point.
     *
     * @property framing Base semantic framing.
     * @property startAzimuthDeg Relative starting azimuth delta in degrees.
     * @property endAzimuthDeg Relative ending azimuth delta in degrees.
     * @property startElevationDeg Relative starting elevation delta in degrees.
     * @property endElevationDeg Relative ending elevation delta in degrees.
     * @property startRadiusMultiplier Distance multiplier at shot start (`1f` = exact ShotSize).
     * @property endRadiusMultiplier Distance multiplier at shot end (`< 1f` pushes in, `> 1f` pulls out).
     * @property easing Motion easing curve.
     */
    @Serializable
    @SerialName("orbit")
    data class Orbit(
        val framing: FramingSpec = FramingSpec(),
        val startAzimuthDeg: Float = -35f,
        val endAzimuthDeg: Float = 35f,
        val startElevationDeg: Float = 0f,
        val endElevationDeg: Float = 0f,
        val startRadiusMultiplier: Float = 1f,
        val endRadiusMultiplier: Float = 1f,
        val easing: EasingCurve = EasingCurve.EaseInOutSine,
    ) : ShotRigSpec

    /**
     * Linear camera push-in, pull-out, or lateral truck move in either world or subject-framed space.
     */
    @Serializable
    @SerialName("dolly")
    data class Dolly(
        val framing: FramingSpec? = null,
        val fromEye: DirectorVec3? = null,
        val toEye: DirectorVec3? = null,
        val fromCenter: DirectorVec3? = null,
        val toCenter: DirectorVec3? = null,
        val startOffset: DirectorVec3 = DirectorVec3.Zero,
        val endOffset: DirectorVec3 = DirectorVec3(0f, 0f, -2f),
        val easing: EasingCurve = EasingCurve.EaseInOutCubic,
    ) : ShotRigSpec

    /**
     * Vertical crane / jib sweep rising or descending while optionally arcing around the subject.
     */
    @Serializable
    @SerialName("crane")
    data class Crane(
        val framing: FramingSpec = FramingSpec(shotSize = ShotSize.Wide),
        val startHeightDelta: Float = -1.2f,
        val endHeightDelta: Float = 6.0f,
        val startAzimuthDeg: Float = -20f,
        val endAzimuthDeg: Float = 20f,
        val startRadiusMultiplier: Float = 0.85f,
        val endRadiusMultiplier: Float = 1.35f,
        val easing: EasingCurve = EasingCurve.EaseInOutCubic,
    ) : ShotRigSpec

    /**
     * Constant-speed or eased dolly along a centripetal 3D Catmull-Rom spline (either inline
     * [waypoints] or a named `camera_rail` entity via [railName]).
     */
    @Serializable
    @SerialName("spline_rail")
    data class SplineRail(
        val railName: String? = null,
        val waypoints: List<DirectorVec3> = emptyList(),
        val closed: Boolean = false,
        val lookMode: SplineLookMode = SplineLookMode.Subject,
        val framing: FramingSpec = FramingSpec(),
        val fixedLookPoint: DirectorVec3? = null,
        val startFraction: Float = 0f,
        val endFraction: Float = 1f,
        val lookAheadFraction: Float = 0.05f,
        val bankDegrees: Float = 0f,
        val easing: EasingCurve = EasingCurve.EaseInOutSine,
    ) : ShotRigSpec

    /**
     * Dynamic pursuit / chase camera following a moving subject with velocity look-ahead and
     * damped inertia.
     */
    @Serializable
    @SerialName("chase_follow")
    data class ChaseFollow(
        val framing: FramingSpec = FramingSpec(
            shotSize = ShotSize.Cowboy,
            angle = AnglePreset.OverShoulderRight,
            lookAheadSeconds = 0.35f,
        ),
        val followSmoothTime: Float = 0.18f,
    ) : ShotRigSpec

    /**
     * Classic Vertigo / dolly-zoom effect: simultaneously ramps focal length from
     * [startFocalLengthMm] to [endFocalLengthMm] while dollying along the view axis so the subject
     * holds constant screen size as the background stretches or compresses dramatically.
     */
    @Serializable
    @SerialName("dolly_zoom")
    data class DollyZoom(
        val framing: FramingSpec = FramingSpec(shotSize = ShotSize.Medium),
        val startFocalLengthMm: Float = 28f,
        val endFocalLengthMm: Float = 85f,
        val easing: EasingCurve = EasingCurve.EaseInOutCubic,
    ) : ShotRigSpec

    /**
     * High-energy whip-pan snapping rapidly from [fromSubject]/[startAzimuthDeg] to
     * [toSubject]/[endAzimuthDeg] with optional settle overshoot.
     */
    @Serializable
    @SerialName("whip_pan")
    data class WhipPan(
        val framing: FramingSpec = FramingSpec(),
        val toSubject: String? = null,
        val startAzimuthDeg: Float = -75f,
        val endAzimuthDeg: Float = 15f,
        val easing: EasingCurve = EasingCurve.EaseOutBack,
    ) : ShotRigSpec

    /**
     * Adopts the pose of a named `camera` or `virtual_camera` node authored directly in the scene.
     */
    @Serializable
    @SerialName("named_camera")
    data class NamedCamera(
        val cameraName: String,
    ) : ShotRigSpec
}

/**
 * Per-shot optional atmosphere/lighting baseline blended smoothly across shot transitions.
 */
@Serializable
data class ShotAtmosphereSpec(
    val dayTime: Float? = null,
    val sunColor: DirectorColor? = null,
    val sunIntensity: Float? = null,
    val sunAmbient: Float? = null,
    val fogEnabled: Boolean? = null,
    val fogDensity: Float? = null,
    val fogColor: DirectorColor? = null,
    val skyHorizon: DirectorColor? = null,
    val skyZenith: DirectorColor? = null,
    val skyExposure: Float? = null,
    val exposure: Float? = null,
    val contrast: Float? = null,
    val saturation: Float? = null,
    val whiteBalanceTemperature: Float? = null,
    val whiteBalanceTint: Float? = null,
    val bloomIntensity: Float? = null,
    val bloomThreshold: Float? = null,
)

/**
 * One cinematic shot in a [DirectorScript].
 *
 * @property id Unique shot identifier (shown on storyboard contact sheets and telemetry).
 * @property duration Duration of this shot in seconds or musical beats.
 * @property rig Camera motion behavior for this shot.
 * @property lens Optical focal length / FOV, clip planes, and Dutch roll.
 * @property feel Handheld operator drift, ambient trauma, and spring smoothing.
 * @property transitionIn How this shot enters from the previous shot.
 * @property collision Optional collision/terrain clearance guard.
 * @property hudVisible Whether the game's HUD canvas renders during this shot (`false` = clean feed).
 * @property letterboxAspect Optional target widescreen aspect ratio (e.g. `2.39f`).
 * @property atmosphere Optional lighting/fog/color-grade mood for this shot.
 * @property notes Optional director notes (helpful for AI vibe-directing commentary).
 */
@Serializable
data class ShotSpec(
    val id: String,
    val duration: DurationRef = DurationRef.seconds(DEFAULT_SHOT_SECONDS),
    val rig: ShotRigSpec = ShotRigSpec.Static(),
    val lens: LensSpec = LensSpec(),
    val feel: CameraFeelSpec = CameraFeelSpec(),
    val transitionIn: ShotTransitionSpec = ShotTransitionSpec.HardCut,
    val collision: ShotCollisionSpec? = ShotCollisionSpec(),
    val hudVisible: Boolean = false,
    val letterboxAspect: Float? = null,
    val atmosphere: ShotAtmosphereSpec? = null,
    val notes: String? = null,
) {
    /** Defaults for [ShotSpec]. */
    companion object {
        private const val DEFAULT_SHOT_SECONDS = 3f
    }
}

/**
 * Parallel lighting and atmospheric choreography cues timed in seconds or beats.
 */
@Serializable
sealed interface LightingCueSpec {
    /** When this cue begins on the timeline. */
    val at: TimeRef

    /** Smoothly sweeps the scene's `day_cycle` time of day from [fromTime] to [toTime] (e.g., golden-hour sunset). */
    @Serializable
    @SerialName("day_cycle_sweep")
    data class DayCycleSweep(
        override val at: TimeRef = TimeRef.seconds(0f),
        val duration: DurationRef = DurationRef.seconds(4f),
        val fromTime: Float = 0.25f,
        val toTime: Float = 0.75f,
        val easing: EasingCurve = EasingCurve.EaseInOutSine,
    ) : LightingCueSpec

    /** Smoothly animates directional sun color, intensity, elevation/azimuth, and ambient fill. */
    @Serializable
    @SerialName("directional_light")
    data class DirectionalLight(
        override val at: TimeRef = TimeRef.seconds(0f),
        val duration: DurationRef = DurationRef.seconds(2f),
        val color: DirectorColor = DirectorColor(1f, 0.95f, 0.85f),
        val intensity: Float = 1.4f,
        val elevationDeg: Float? = null,
        val azimuthDeg: Float? = null,
        val ambient: Float? = null,
        val easing: EasingCurve = EasingCurve.EaseInOutSine,
    ) : LightingCueSpec

    /**
     * Camera-relative dynamic practical rim/key/fill point light tracking a named [subject] so its
     * silhouette pops dramatically against dark or backlit environments.
     */
    @Serializable
    @SerialName("hero_rim_light")
    data class HeroRimLight(
        override val at: TimeRef = TimeRef.seconds(0f),
        val duration: DurationRef = DurationRef.seconds(4f),
        val subject: String,
        val color: DirectorColor = DirectorColor(1f, 0.72f, 0.45f),
        val intensity: Float = 3.5f,
        val range: Float = 14f,
        val azimuthFromCameraDeg: Float = 140f,
        val heightOffset: Float = 2.4f,
        val distance: Float = 3.2f,
        val fadeInSeconds: Float = 0.3f,
        val fadeOutSeconds: Float = 0.3f,
    ) : LightingCueSpec

    /**
     * Procedural lightning storm flash (multi-peak jagged electrical flicker) or spell/explosion
     * light burst at [position] or above [subject].
     */
    @Serializable
    @SerialName("light_flash")
    data class LightFlash(
        override val at: TimeRef,
        val duration: DurationRef = DurationRef.seconds(0.65f),
        val subject: String? = null,
        val position: DirectorVec3 = DirectorVec3(0f, 8f, 0f),
        val color: DirectorColor = DirectorColor(0.75f, 0.88f, 1.0f),
        val peakIntensity: Float = 8f,
        val range: Float = 40f,
        val flickerPeaks: Int = 3,
        val boostSunIntensity: Float = 0f,
    ) : LightingCueSpec

    /** Smoothly animates distance fog density and color. */
    @Serializable
    @SerialName("fog_ramp")
    data class FogRamp(
        override val at: TimeRef = TimeRef.seconds(0f),
        val duration: DurationRef = DurationRef.seconds(3f),
        val enabled: Boolean = true,
        val fromDensity: Float = 0.001f,
        val toDensity: Float = 0.015f,
        val fromColor: DirectorColor = DirectorColor(0.55f, 0.62f, 0.70f),
        val toColor: DirectorColor = DirectorColor(0.18f, 0.22f, 0.32f),
        val easing: EasingCurve = EasingCurve.EaseInOutSine,
    ) : LightingCueSpec

    /** Smoothly transitions post-processing color grading and bloom intensity over [duration]. */
    @Serializable
    @SerialName("color_grade")
    data class ColorGrade(
        override val at: TimeRef = TimeRef.seconds(0f),
        val duration: DurationRef = DurationRef.seconds(2f),
        val exposure: Float = 1.05f,
        val contrast: Float = 1.12f,
        val saturation: Float = 1.1f,
        val whiteBalanceTemperature: Float = 0.1f,
        val whiteBalanceTint: Float = 0f,
        val bloomIntensity: Float? = null,
        val bloomThreshold: Float? = null,
        val easing: EasingCurve = EasingCurve.EaseInOutSine,
    ) : LightingCueSpec
}

/**
 * Background soundtrack configuration synced to [DirectorScript.clock].
 */
@Serializable
data class MusicTrackSpec(
    val path: String,
    val volume: Float = 0.65f,
    val startOffsetSeconds: Float = 0f,
    val fadeInSeconds: Float = 0.5f,
    val fadeOutSeconds: Float = 1.0f,
    val loop: Boolean = true,
)

/**
 * Timed 2D/3D sound effect and dynamic music control cues.
 */
@Serializable
sealed interface AudioCueSpec {
    /** When this cue triggers on the timeline. */
    val at: TimeRef

    /** Plays a 2D stinger/riser/voice clip or 3D positional SFX attached to [entity] or [position]. */
    @Serializable
    @SerialName("play_sfx")
    data class PlaySfx(
        override val at: TimeRef,
        val path: String,
        val volume: Float = 1f,
        val pitch: Float = 1f,
        val entity: String? = null,
        val position: DirectorVec3? = null,
        val maxDistance: Float = 0f,
        val duckMusicGain: Float? = null,
        val duckHoldSeconds: Float = 0.4f,
    ) : AudioCueSpec

    /** Adjusts music volume, fade, or manual ducking mid-trailer. */
    @Serializable
    @SerialName("music_control")
    data class MusicControl(
        override val at: TimeRef,
        val targetVolume: Float? = null,
        val fadeSeconds: Float = 0.5f,
        val duckGain: Float? = null,
        val duckHoldSeconds: Float = 0.5f,
        val stop: Boolean = false,
    ) : AudioCueSpec
}

/**
 * Timed gameplay staging, actor choreography, speed-ramp, and impact trauma cues.
 */
@Serializable
sealed interface ActionCueSpec {
    /** When this cue triggers on the timeline. */
    val at: TimeRef

    /** Holds or pulses a named `InputActions` verb (e.g. `"move_forward"`, `"sprint"`, `"jump"`). */
    @Serializable
    @SerialName("input_action")
    data class InputAction(
        override val at: TimeRef,
        val action: String,
        val duration: DurationRef = DurationRef.seconds(0.5f),
    ) : ActionCueSpec

    /** Stages a named actor smoothly to [targetPosition] or [targetMarker], optionally facing [faceTarget]. */
    @Serializable
    @SerialName("actor_move")
    data class ActorMove(
        override val at: TimeRef,
        val entity: String,
        val targetPosition: DirectorVec3? = null,
        val targetMarker: String? = null,
        val faceTarget: String? = null,
        val duration: DurationRef = DurationRef.seconds(1.5f),
        val easing: EasingCurve = EasingCurve.EaseInOutSine,
    ) : ActionCueSpec

    /** Triggers a named skeletal animation clip on [entity]'s `Animator`. */
    @Serializable
    @SerialName("play_animation")
    data class PlayAnimation(
        override val at: TimeRef,
        val entity: String,
        val clip: String,
        val crossfadeSeconds: Float = 0.2f,
        val loop: Boolean = true,
        val speed: Float = 1f,
    ) : ActionCueSpec

    /** Enables/disables or triggers a burst on a named particle emitter entity. */
    @Serializable
    @SerialName("particle_burst")
    data class ParticleBurst(
        override val at: TimeRef,
        val entity: String,
        val enabled: Boolean = true,
        val burstCount: Int = 0,
    ) : ActionCueSpec

    /**
     * Smooth simulation speed ramp (bullet-time slow-mo or timelapse) while the camera continues
     * moving on unscaled time.
     */
    @Serializable
    @SerialName("time_scale")
    data class TimeScaleRamp(
        override val at: TimeRef,
        val targetScale: Float = 0.25f,
        val rampInSeconds: Float = 0.15f,
        val holdSeconds: Float = 1.0f,
        val rampOutSeconds: Float = 0.25f,
        val hitstopSeconds: Float = 0f,
        val pitchShiftAudio: Boolean = true,
    ) : ActionCueSpec

    /** Fires an impact camera-shake trauma burst on the active camera. */
    @Serializable
    @SerialName("trauma")
    data class CameraTrauma(
        override val at: TimeRef,
        val amount: Float = 0.6f,
        val decaySeconds: Float = 0.6f,
    ) : ActionCueSpec
}

/**
 * Validation issue discovered in a [DirectorScript] or [TrailerReel].
 *
 * @property path Dotted path to the offending element (e.g. `shots[2].lens.focalLengthMm`).
 * @property message Human- and agent-readable explanation of the issue.
 */
@Serializable
data class DirectorValidationIssue(
    val path: String,
    val message: String,
)

/**
 * Complete declarative camera, lighting, audio, and action script (`*.director.json`) for directing
 * a single scene's trailer sequence or in-game cutscene.
 *
 * @property version Format version (currently [CURRENT_VERSION]).
 * @property name Human-readable sequence title.
 * @property clock Musical beat clock governing beat-referenced timings.
 * @property seed Deterministic noise seed for handheld drift and shake repeatability.
 * @property inputTake Optional project-relative path to a recorded `*.take.json` gameplay replay.
 * @property aspectRatio Optional default framing aspect ratio (e.g. `16f / 9f`).
 * @property music Optional background music track specification.
 * @property shots Ordered sequence of camera shots.
 * @property lightingTrack Parallel complex lighting, day-cycle, fog, rim-light, flash, and grade cues.
 * @property audioTrack Parallel 2D/3D SFX and music modulation cues.
 * @property actionTrack Parallel gameplay input, actor staging, animation, particle, speed-ramp, and trauma cues.
 */
@Serializable
data class DirectorScript(
    val version: Int = CURRENT_VERSION,
    val name: String = "untitled",
    val clock: BeatClock = BeatClock(),
    val seed: Int = DEFAULT_SEED,
    val inputTake: String? = null,
    val aspectRatio: Float? = null,
    val music: MusicTrackSpec? = null,
    val shots: List<ShotSpec> = emptyList(),
    val lightingTrack: List<LightingCueSpec> = emptyList(),
    val audioTrack: List<AudioCueSpec> = emptyList(),
    val actionTrack: List<ActionCueSpec> = emptyList(),
) {
    /** Total resolved duration of all [shots] in seconds. */
    fun totalDurationSeconds(): Float {
        var total = 0f
        for (shot in shots) {
            total += clock.resolveDuration(shot.duration)
        }
        return total
    }

    /** Validates structural, optical, and timing invariants, returning all issues found. */
    @Suppress("CyclomaticComplexMethod")
    fun validate(): List<DirectorValidationIssue> = buildList {
        if (clock.bpm <= 0f || !clock.bpm.isFinite()) {
            add(DirectorValidationIssue("clock.bpm", "bpm must be finite and positive, got ${clock.bpm}"))
        }
        if (clock.beatsPerBar <= 0) {
            add(DirectorValidationIssue("clock.beatsPerBar", "beatsPerBar must be positive, got ${clock.beatsPerBar}"))
        }
        if (shots.isEmpty()) {
            add(DirectorValidationIssue("shots", "director script must contain at least one shot"))
        }
        val seenIds = HashSet<String>()
        shots.forEachIndexed { idx, shot ->
            val prefix = "shots[$idx]"
            if (shot.id.isBlank()) {
                add(DirectorValidationIssue("$prefix.id", "shot id must not be blank"))
            } else if (!seenIds.add(shot.id)) {
                add(DirectorValidationIssue("$prefix.id", "duplicate shot id '${shot.id}'"))
            }
            val dur = clock.resolveDuration(shot.duration)
            if (dur <= 0f || !dur.isFinite()) {
                add(DirectorValidationIssue("$prefix.duration", "shot '${shot.id}' duration must be > 0 seconds"))
            }
            val mm = shot.lens.focalLengthMm
            if (mm != null && (mm <= 0f || !mm.isFinite())) {
                add(DirectorValidationIssue("$prefix.lens.focalLengthMm", "focalLengthMm must be > 0"))
            }
            val fov = shot.lens.fovYDegrees
            val invalidFov = fov != null && (!fov.isFinite() || fov !in MIN_VALID_FOV_DEG..MAX_VALID_FOV_DEG)
            if (invalidFov) {
                add(DirectorValidationIssue("$prefix.lens.fovYDegrees", "fovYDegrees must be in (0, 180)"))
            }
            if (shot.lens.near <= 0f || shot.lens.far <= shot.lens.near) {
                add(DirectorValidationIssue("$prefix.lens", "near must be > 0 and far must be > near"))
            }
            val rig = shot.rig
            if (rig is ShotRigSpec.SplineRail && rig.railName.isNullOrBlank() && rig.waypoints.size < 2) {
                add(DirectorValidationIssue("$prefix.rig", "spline_rail needs either railName or at least 2 waypoints"))
            }
            if (rig is ShotRigSpec.NamedCamera && rig.cameraName.isBlank()) {
                add(DirectorValidationIssue("$prefix.rig.cameraName", "named_camera requires a non-blank cameraName"))
            }
            if (rig is ShotRigSpec.DollyZoom && (rig.startFocalLengthMm <= 0f || rig.endFocalLengthMm <= 0f)) {
                add(DirectorValidationIssue("$prefix.rig", "dolly_zoom focal lengths must be > 0"))
            }
        }
        actionTrack.forEachIndexed { idx, cue ->
            if (cue is ActionCueSpec.TimeScaleRamp && (cue.targetScale < 0f || !cue.targetScale.isFinite())) {
                add(DirectorValidationIssue("actionTrack[$idx].targetScale", "targetScale must be finite and >= 0"))
            }
        }
    }

    /** Constants and JSON serializer for [DirectorScript]. */
    companion object {
        /** Current [DirectorScript] schema version. */
        const val CURRENT_VERSION: Int = 1

        private const val DEFAULT_SEED = 1337
        private const val MIN_VALID_FOV_DEG = 0.01f
        private const val MAX_VALID_FOV_DEG = 179.99f

        /** Configured kotlinx.serialization JSON codec for director scripts and reels. */
        val json: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = false
            classDiscriminator = "type"
        }

        /** Decodes a [DirectorScript] from JSON text. */
        fun decode(text: String): DirectorScript = json.decodeFromString(serializer(), text)

        /** Encodes this [DirectorScript] to formatted JSON text. */
        fun encode(script: DirectorScript): String = json.encodeToString(serializer(), script)
    }
}

/**
 * One scene segment inside a multi-scene [TrailerReel].
 *
 * @property id Unique segment identifier.
 * @property scene Project-relative scene path (e.g. `scenes/harbor.awakescene`).
 * @property directorPath Optional project-relative path to a `*.director.json` script.
 * @property inlineDirector Optional inline [DirectorScript] (used when [directorPath] is null).
 * @property inputTake Optional project-relative path to a `*.take.json` gameplay replay overriding the script's.
 * @property transitionIn Transition from the previous reel segment (`HardCut` or `FadeThroughBlack`).
 */
@Serializable
data class ReelSegmentSpec(
    val id: String,
    val scene: String,
    val directorPath: String? = null,
    val inlineDirector: DirectorScript? = null,
    val inputTake: String? = null,
    val transitionIn: ShotTransitionSpec = ShotTransitionSpec.HardCut,
)

/**
 * Multi-scene gameplay trailer edit decision list (`*.reel.json`) stitching multiple scenes and
 * director scripts together under one continuous soundtrack and beat grid.
 *
 * @property version Format version.
 * @property name Trailer reel title.
 * @property clock Master soundtrack beat clock.
 * @property music Optional continuous master soundtrack mixed seamlessly across all segments.
 * @property segments Ordered list of scene segments to capture and assemble.
 */
@Serializable
data class TrailerReel(
    val version: Int = CURRENT_VERSION,
    val name: String = "trailer",
    val clock: BeatClock = BeatClock(),
    val music: MusicTrackSpec? = null,
    val segments: List<ReelSegmentSpec> = emptyList(),
) {
    /** Validates reel structure before capture starts. */
    fun validate(): List<DirectorValidationIssue> = buildList {
        if (segments.isEmpty()) {
            add(DirectorValidationIssue("segments", "trailer reel must contain at least one segment"))
        }
        segments.forEachIndexed { idx, seg ->
            if (seg.scene.isBlank()) {
                add(DirectorValidationIssue("segments[$idx].scene", "segment scene path must not be blank"))
            }
            if (seg.directorPath.isNullOrBlank() && seg.inlineDirector == null) {
                add(DirectorValidationIssue("segments[$idx]", "segment '${seg.id}' must specify directorPath or inlineDirector"))
            }
            seg.inlineDirector?.validate()?.forEach { issue ->
                add(DirectorValidationIssue("segments[$idx].inlineDirector.${issue.path}", issue.message))
            }
        }
    }

    /** Serialization helpers for [TrailerReel]. */
    companion object {
        /** Current [TrailerReel] schema version. */
        const val CURRENT_VERSION: Int = 1

        /** Decodes a [TrailerReel] from JSON text. */
        fun decode(text: String): TrailerReel = DirectorScript.json.decodeFromString(serializer(), text)

        /** Encodes a [TrailerReel] to formatted JSON text. */
        fun encode(reel: TrailerReel): String = DirectorScript.json.encodeToString(serializer(), reel)
    }
}
