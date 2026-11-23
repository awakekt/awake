/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.director

import com.awakekt.awake.core.camera.CameraGround
import com.awakekt.awake.core.camera.CameraObstruction
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.ArcLengthSpline3f
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.lerp
import kotlin.math.PI

/**
 * Mutable per-frame camera output written by [DirectorPlayer] with zero per-frame allocations.
 *
 * @property eye World-space camera eye position.
 * @property center World-space camera look-at point.
 * @property up Unit camera up vector (incorporating any Dutch roll).
 * @property fovYRadians Vertical field of view in radians.
 * @property near Near clipping plane distance.
 * @property far Far clipping plane distance.
 * @property rollRadians Camera Dutch roll angle in radians.
 * @property hudVisible Whether the game's HUD/canvas overlay should be visible for this frame.
 * @property fadeBlack Full-frame fade-to-black amount (`0f` = clear image, `1f` = solid black).
 * @property letterboxAspect Optional target widescreen aspect ratio (e.g. `2.39f`) for letterboxing.
 */
class DirectedCameraPose(
    val eye: Vec3f = Vec3f(0f, DEFAULT_EYE_Y, DEFAULT_EYE_Z),
    val center: Vec3f = Vec3f(0f, 0f, 0f),
    val up: Vec3f = Vec3f(0f, 1f, 0f),
    var fovYRadians: Float = DEFAULT_FOV_Y_RADIANS,
    var near: Float = DEFAULT_NEAR,
    var far: Float = DEFAULT_FAR,
    var rollRadians: Float = 0f,
    var hudVisible: Boolean = false,
    var fadeBlack: Float = 0f,
    var letterboxAspect: Float? = null,
) {
    /** Copies every field from [other] in place without allocating. */
    fun copyFrom(other: DirectedCameraPose) {
        eye.set(other.eye)
        center.set(other.center)
        up.set(other.up)
        fovYRadians = other.fovYRadians
        near = other.near
        far = other.far
        rollRadians = other.rollRadians
        hudVisible = other.hudVisible
        fadeBlack = other.fadeBlack
        letterboxAspect = other.letterboxAspect
    }

    /**
     * Blends this pose linearly toward [target] by [fraction] in `0..1`, mutating this pose in
     * place.
     */
    fun blendToward(target: DirectedCameraPose, fraction: Float) {
        val t = fraction.coerceIn(0f, 1f)
        eye.lerp(target.eye, t)
        center.lerp(target.center, t)
        up.lerp(target.up, t).normalize()
        fovYRadians = lerp(fovYRadians, target.fovYRadians, t)
        near = lerp(near, target.near, t)
        far = lerp(far, target.far, t)
        rollRadians = lerp(rollRadians, target.rollRadians, t)
        hudVisible = if (t >= HALF) target.hudVisible else hudVisible
        fadeBlack = lerp(fadeBlack, target.fadeBlack, t)
        letterboxAspect = target.letterboxAspect ?: letterboxAspect
    }

    /** Resets every field back to neutral defaults. */
    fun reset() {
        eye.set(0f, DEFAULT_EYE_Y, DEFAULT_EYE_Z)
        center.set(0f, 0f, 0f)
        up.set(0f, 1f, 0f)
        fovYRadians = DEFAULT_FOV_Y_RADIANS
        near = DEFAULT_NEAR
        far = DEFAULT_FAR
        rollRadians = 0f
        hudVisible = false
        fadeBlack = 0f
        letterboxAspect = null
    }

    /** Default constants for [DirectedCameraPose]. */
    companion object {
        private const val DEFAULT_EYE_Y = 2f
        private const val DEFAULT_EYE_Z = 6f
        private const val DEFAULT_FOV_Y_RADIANS = (45.0 * PI / 180.0).toFloat()
        private const val DEFAULT_NEAR = 0.1f
        private const val DEFAULT_FAR = 500f
        private const val HALF = 0.5f
    }
}

/**
 * One pooled dynamic point light driven by the director (e.g. a camera-relative hero rim light, a
 * lightning storm flash, or a spell impact light burst).
 *
 * @property id Logical light slot identifier.
 * @property position World-space position of the light.
 * @property color Linear RGB color tint.
 * @property intensity Intensity multiplier (`>= 0f`).
 * @property range Attenuation radius in world units (`> 0f`).
 * @property active Whether this slot is active on the current frame.
 */
class DynamicDirectorLight(
    var id: String = "",
    val position: Vec3f = Vec3f(0f, 0f, 0f),
    val color: Vec3f = Vec3f(1f, 1f, 1f),
    var intensity: Float = 0f,
    var range: Float = DEFAULT_LIGHT_RANGE,
    var active: Boolean = false,
) {
    /** Clears this pooled slot for reuse on the next frame. */
    fun clear() {
        id = ""
        position.set(0f, 0f, 0f)
        color.set(1f, 1f, 1f)
        intensity = 0f
        range = DEFAULT_LIGHT_RANGE
        active = false
    }

    /** Defaults for [DynamicDirectorLight]. */
    companion object {
        private const val DEFAULT_LIGHT_RANGE = 16f
    }
}

/**
 * Mutable per-frame lighting, atmosphere, and color-grade overrides emitted by [DirectorPlayer].
 *
 * Each channel carries a `has*` flag so untouched properties leave the scene's authored values
 * undisturbed.
 */
class DirectedAtmosphereState {
    /** Whether [dayTime] overrides the scene's day cycle time. */
    var hasDayTime: Boolean = false

    /** Normalized time of day in `0..1` (`0` midnight, `0.25` sunrise, `0.5` noon, `0.75` sunset). */
    var dayTime: Float = DEFAULT_DAY_TIME

    /** Whether directional sun light properties are overridden. */
    var hasDirectionalLight: Boolean = false

    /** Overridden directional sun RGB color. */
    val sunColor: Vec3f = Vec3f(1f, 1f, 1f)

    /** Overridden directional sun intensity multiplier. */
    var sunIntensity: Float = 1f

    /** Whether [sunDirection] overrides the directional light direction. */
    var hasSunDirection: Boolean = false

    /** Overridden world-space vector pointing FROM the sun. */
    val sunDirection: Vec3f = Vec3f(DEFAULT_SUN_X, DEFAULT_SUN_Y, DEFAULT_SUN_Z)

    /** Optional overridden directional light ambient factor (`< 0f` leaves authored ambient). */
    var sunAmbient: Float = -1f

    /** Whether fog parameters are overridden. */
    var hasFog: Boolean = false

    /** Overridden fog enabled flag. */
    var fogEnabled: Boolean = true

    /** Overridden exponential fog density. */
    var fogDensity: Float = DEFAULT_FOG_DENSITY

    /** Overridden fog RGB color. */
    val fogColor: Vec3f = Vec3f(DEFAULT_FOG_R, DEFAULT_FOG_G, DEFAULT_FOG_B)

    /** Whether skybox colors/exposure are overridden. */
    var hasSky: Boolean = false

    /** Overridden sky horizon RGB color. */
    val skyHorizon: Vec3f = Vec3f(DEFAULT_HORIZON_R, DEFAULT_HORIZON_G, DEFAULT_HORIZON_B)

    /** Overridden sky zenith RGB color. */
    val skyZenith: Vec3f = Vec3f(DEFAULT_ZENITH_R, DEFAULT_ZENITH_G, DEFAULT_ZENITH_B)

    /** Overridden skybox exposure multiplier. */
    var skyExposure: Float = 1f

    /** Whether post-processing color grading / bloom parameters are overridden. */
    var hasGrade: Boolean = false

    /** Overridden exposure multiplier. */
    var exposure: Float = 1f

    /** Overridden color contrast. */
    var contrast: Float = 1f

    /** Overridden color saturation. */
    var saturation: Float = 1f

    /** Overridden white balance temperature in `-1..1`. */
    var whiteBalanceTemperature: Float = 0f

    /** Overridden white balance tint in `-1..1`. */
    var whiteBalanceTint: Float = 0f

    /** Overridden bloom intensity (`< 0f` leaves authored bloom unchanged). */
    var bloomIntensity: Float = -1f

    /** Overridden bloom bright-pass threshold (`< 0f` leaves authored threshold unchanged). */
    var bloomThreshold: Float = -1f

    /** Pre-allocated pool of active dynamic point lights (rim lights, lightning, impact flashes). */
    val dynamicLights: List<DynamicDirectorLight> = List(MAX_DYNAMIC_LIGHTS) { DynamicDirectorLight() }

    /** Number of active entries at the front of [dynamicLights] this frame. */
    var activeDynamicLightCount: Int = 0

    /** Resets all override flags and clears all dynamic light slots for a fresh evaluation frame. */
    fun reset() {
        hasDayTime = false
        dayTime = DEFAULT_DAY_TIME
        hasDirectionalLight = false
        sunColor.set(1f, 1f, 1f)
        sunIntensity = 1f
        hasSunDirection = false
        sunDirection.set(DEFAULT_SUN_X, DEFAULT_SUN_Y, DEFAULT_SUN_Z)
        sunAmbient = -1f
        hasFog = false
        fogEnabled = true
        fogDensity = DEFAULT_FOG_DENSITY
        fogColor.set(DEFAULT_FOG_R, DEFAULT_FOG_G, DEFAULT_FOG_B)
        hasSky = false
        skyHorizon.set(DEFAULT_HORIZON_R, DEFAULT_HORIZON_G, DEFAULT_HORIZON_B)
        skyZenith.set(DEFAULT_ZENITH_R, DEFAULT_ZENITH_G, DEFAULT_ZENITH_B)
        skyExposure = 1f
        hasGrade = false
        exposure = 1f
        contrast = 1f
        saturation = 1f
        whiteBalanceTemperature = 0f
        whiteBalanceTint = 0f
        bloomIntensity = -1f
        bloomThreshold = -1f
        for (i in 0 until activeDynamicLightCount) {
            dynamicLights[i].clear()
        }
        activeDynamicLightCount = 0
    }

    /** Allocates the next pooled [DynamicDirectorLight] slot, or returns `null` if the pool is full. */
    fun acquireDynamicLight(): DynamicDirectorLight? {
        if (activeDynamicLightCount >= MAX_DYNAMIC_LIGHTS) return null
        val light = dynamicLights[activeDynamicLightCount++]
        light.active = true
        return light
    }

    /** Converts [fogColor] to an immutable [Color] value. */
    fun fogAsColor(): Color = Color(fogColor.x, fogColor.y, fogColor.z, 1f)

    /** Converts [skyHorizon] to an immutable [Color] value. */
    fun skyHorizonAsColor(): Color = Color(skyHorizon.x, skyHorizon.y, skyHorizon.z, 1f)

    /** Converts [skyZenith] to an immutable [Color] value. */
    fun skyZenithAsColor(): Color = Color(skyZenith.x, skyZenith.y, skyZenith.z, 1f)

    /** Constants for [DirectedAtmosphereState]. */
    companion object {
        /** Maximum simultaneous dynamic director practical lights per frame. */
        const val MAX_DYNAMIC_LIGHTS: Int = 8

        private const val DEFAULT_DAY_TIME = 0.5f
        private const val DEFAULT_SUN_X = 0.4f
        private const val DEFAULT_SUN_Y = 0.8f
        private const val DEFAULT_SUN_Z = 0.4f
        private const val DEFAULT_FOG_DENSITY = 0.001f
        private const val DEFAULT_FOG_R = 0.55f
        private const val DEFAULT_FOG_G = 0.62f
        private const val DEFAULT_FOG_B = 0.70f
        private const val DEFAULT_HORIZON_R = 0.72f
        private const val DEFAULT_HORIZON_G = 0.80f
        private const val DEFAULT_HORIZON_B = 0.90f
        private const val DEFAULT_ZENITH_R = 0.28f
        private const val DEFAULT_ZENITH_G = 0.48f
        private const val DEFAULT_ZENITH_B = 0.78f
    }
}

/**
 * Mutable spatial description of a named actor, prop, or landmark on stage, reused across frames
 * with zero allocations.
 *
 * @property found Whether the requested subject exists on stage.
 * @property position World-space anchor position (typically entity origin / feet).
 * @property boundsMin World-space axis-aligned bounding box minimum corner.
 * @property boundsMax World-space axis-aligned bounding box maximum corner.
 * @property forward Unit horizontal/3D world-space facing direction of the subject.
 * @property velocity Estimated world-space velocity in units per second.
 */
class StageSubjectState(
    var found: Boolean = false,
    val position: Vec3f = Vec3f(0f, 0f, 0f),
    val boundsMin: Vec3f = Vec3f(-HALF_UNIT, 0f, -HALF_UNIT),
    val boundsMax: Vec3f = Vec3f(HALF_UNIT, DEFAULT_HEIGHT, HALF_UNIT),
    val forward: Vec3f = Vec3f(0f, 0f, -1f),
    val velocity: Vec3f = Vec3f(0f, 0f, 0f),
) {
    /** Total world-space height of this subject's bounding box. */
    val height: Float get() = (boundsMax.y - boundsMin.y).coerceAtLeast(MIN_EXTENT)

    /** Total world-space horizontal width (max of X and Z extents). */
    val width: Float
        get() = maxOf(boundsMax.x - boundsMin.x, boundsMax.z - boundsMin.z).coerceAtLeast(MIN_EXTENT)

    /** Writes the bounding box center into [out] without allocating. */
    fun boundsCenter(out: Vec3f): Vec3f = out.set(
        (boundsMin.x + boundsMax.x) * HALF_UNIT,
        (boundsMin.y + boundsMax.y) * HALF_UNIT,
        (boundsMin.z + boundsMax.z) * HALF_UNIT,
    )

    /** Expands this subject's bounding box to encompass [other] for multi-subject framing. */
    fun expandToInclude(other: StageSubjectState) {
        if (other.boundsMin.x < boundsMin.x) boundsMin.x = other.boundsMin.x
        if (other.boundsMin.y < boundsMin.y) boundsMin.y = other.boundsMin.y
        if (other.boundsMin.z < boundsMin.z) boundsMin.z = other.boundsMin.z
        if (other.boundsMax.x > boundsMax.x) boundsMax.x = other.boundsMax.x
        if (other.boundsMax.y > boundsMax.y) boundsMax.y = other.boundsMax.y
        if (other.boundsMax.z > boundsMax.z) boundsMax.z = other.boundsMax.z
    }

    /** Resets to an unfound neutral state. */
    fun reset() {
        found = false
        position.set(0f, 0f, 0f)
        boundsMin.set(-HALF_UNIT, 0f, -HALF_UNIT)
        boundsMax.set(HALF_UNIT, DEFAULT_HEIGHT, HALF_UNIT)
        forward.set(0f, 0f, -1f)
        velocity.set(0f, 0f, 0f)
    }

    /** Default subject dimensions when no mesh/collider bounds exist. */
    companion object {
        private const val HALF_UNIT = 0.5f
        private const val DEFAULT_HEIGHT = 1.8f
        private const val MIN_EXTENT = 0.1f
    }
}

/**
 * Reusable result struct for sightline raycasts performed by [StageQuery.castSightline].
 *
 * @property occluded Whether geometry obstructed the segment between camera eye and target point.
 * @property fraction Normalized hit distance along the ray in `0..1` (`1f` when unobstructed).
 * @property occluderName Optional name of the blocking entity, when known.
 */
class StageRayHit(
    var occluded: Boolean = false,
    var fraction: Float = 1f,
    var occluderName: String? = null,
) {
    /** Resets to an unobstructed hit state. */
    fun reset() {
        occluded = false
        fraction = 1f
        occluderName = null
    }
}

/**
 * Scene-agnostic read contract allowing [DirectorPlayer], [FramingSolver], and [FramingAnalyzer]
 * to query actors, rails, named cameras, terrain heights, and sightline occlusion without
 * depending on `:awake:scene*` or `:awake:ecs`.
 */
interface StageQuery {
    /**
     * Populates [out] with the spatial state of the named entity/marker [name].
     *
     * @param name Scene node or stage marker name.
     * @param out Mutable destination struct updated in place.
     * @return `true` if found, `false` otherwise.
     */
    fun readSubject(name: String, out: StageSubjectState): Boolean

    /** Returns a precomputed [ArcLengthSpline3f] for a named rail in the stage, or `null`. */
    fun readSpline(name: String): ArcLengthSpline3f? = null

    /** Populates [out] with a named camera's pose if one exists in the stage. */
    fun readNamedCamera(name: String, out: DirectedCameraPose): Boolean = false

    /** Ground/terrain height at world `(x, z)`, or [Float.NaN] when no ground exists there. */
    fun groundHeight(x: Float, z: Float): Float = Float.NaN

    /**
     * Casts a sightline ray from [from] to [to], ignoring [ignoreEntityName] if provided, and
     * writes the hit result into [outHit].
     *
     * @return `true` if an occluder blocked the ray before reaching [to].
     */
    fun castSightline(
        from: Vec3f,
        to: Vec3f,
        ignoreEntityName: String?,
        outHit: StageRayHit,
    ): Boolean {
        outHit.reset()
        return false
    }

    /** Terrain height probe adapter for [com.awakekt.awake.core.camera.CameraCollisionSolver]. */
    val cameraGround: CameraGround?
        get() = CameraGround { x, z -> groundHeight(x, z) }

    /** Physics obstruction sweep probe for [com.awakekt.awake.core.camera.CameraCollisionSolver]. */
    val cameraObstruction: CameraObstruction?
        get() = null

    /** Empty stage implementation returning nothing found. */
    companion object {
        /** A no-op [StageQuery] for tests or explicit world-coordinate-only scripts. */
        val Empty: StageQuery = object : StageQuery {
            override fun readSubject(name: String, out: StageSubjectState): Boolean {
                out.reset()
                return false
            }
        }
    }
}

/**
 * Callback interface receiving timed one-shot and stateful cues dispatched by [DirectorPlayer].
 *
 * Implemented by `:awake:scene:director`'s `DirectorSystem` to apply audio, animations, particle
 * bursts, actor staging, and input actions to a live ECS world.
 */
interface DirectorCueSink {
    /** Triggers a named input action override (`pressed`, `held`, or `released`). */
    fun onInputAction(action: String, active: Boolean, durationSeconds: Float) {}

    /** Stages a named actor toward a target world position or facing direction. */
    fun onActorCue(cue: ActionCueSpec.ActorMove) {}

    /** Triggers a skeletal animation clip on a named entity. */
    fun onAnimationCue(cue: ActionCueSpec.PlayAnimation) {}

    /** Toggles or triggers a burst on a named particle emitter. */
    fun onParticleCue(cue: ActionCueSpec.ParticleBurst) {}

    /** Plays a 2D or 3D sound effect cue with optional music ducking. */
    fun onPlaySfx(cue: AudioCueSpec.PlaySfx) {}

    /** Starts or updates the background music stream. */
    fun onMusicState(cue: AudioCueSpec.MusicControl) {}

    /** Starts the script's top-level soundtrack when playback begins. */
    fun onStartMusic(music: MusicTrackSpec) {}

    /** Notifies when HUD visibility changes for a shot. */
    fun onHudVisibility(visible: Boolean) {}

    /** No-op sink ignoring all dispatched cues. */
    companion object {
        /** A shared no-op [DirectorCueSink]. */
        val None: DirectorCueSink = object : DirectorCueSink {}
    }
}
