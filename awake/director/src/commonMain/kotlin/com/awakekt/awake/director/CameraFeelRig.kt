/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.director

import com.awakekt.awake.core.camera.CameraOptics
import com.awakekt.awake.core.math.DampedFloat
import com.awakekt.awake.core.math.DampedVec3
import com.awakekt.awake.core.math.Noise
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zero-allocation procedural camera feel processor adding critically damped follow inertia,
 * organic handheld breathing, and trauma-squared impact camera shake on top of any shot pose.
 *
 * Uses Awake's deterministic [Noise.fractalNoise2D] so re-rendering the same trailer script
 * produces identical micro-shake frames every run.
 *
 * @param seed Deterministic noise seed for handheld drift and trauma shake.
 */
class CameraFeelRig(
    private val seed: Int = DEFAULT_SEED,
) {
    /** Current normalized shake trauma in `0..1`; effective shake intensity is `trauma * trauma`. */
    var trauma: Float = 0f
        private set

    /** Linear trauma decay rate per second. */
    var traumaDecayPerSecond: Float = DEFAULT_TRAUMA_DECAY

    /** Maximum yaw shake excursion in radians at `trauma = 1f`. */
    var maxShakeYawRadians: Float = DEFAULT_MAX_ANGLE_RAD

    /** Maximum pitch shake excursion in radians at `trauma = 1f`. */
    var maxShakePitchRadians: Float = DEFAULT_MAX_ANGLE_RAD

    /** Maximum roll shake excursion in radians at `trauma = 1f`. */
    var maxShakeRollRadians: Float = DEFAULT_MAX_ROLL_RAD

    /** Maximum translational shake excursion in world units at `trauma = 1f`. */
    var maxShakeOffset: Float = DEFAULT_MAX_OFFSET

    private var springInitialized: Boolean = false
    private val dampedEye = DampedVec3()
    private val dampedCenter = DampedVec3()
    private val dampedFov = DampedFloat()
    private val dampedRoll = DampedFloat()

    // Hoisted scratch vectors for zero per-frame allocations.
    private val forward = Vec3f()
    private val right = Vec3f()
    private val up = Vec3f()
    private val shakenForward = Vec3f()

    /**
     * Adds an impact trauma burst clamped to `0..1`.
     *
     * @param amount Normalized trauma impulse (e.g. `0.35f` footsteps/hit, `0.85f` explosion).
     * @param decaySeconds Optional override for how long full trauma takes to decay to zero.
     */
    fun addTrauma(amount: Float, decaySeconds: Float? = null) {
        if (amount <= 0f || !amount.isFinite()) return
        trauma = (trauma + amount).coerceIn(0f, 1f)
        if (decaySeconds != null && decaySeconds > 0f && decaySeconds.isFinite()) {
            traumaDecayPerSecond = 1f / decaySeconds
        }
    }

    /** Clears spring velocities and snaps immediately on hard camera cuts. */
    fun snapTo(pose: DirectedCameraPose) {
        dampedEye.snapTo(pose.eye)
        dampedCenter.snapTo(pose.center)
        dampedFov.snapTo(pose.fovYRadians)
        dampedRoll.snapTo(pose.rollRadians)
        springInitialized = true
    }

    /** Resets all trauma and damping state completely. */
    fun reset() {
        trauma = 0f
        traumaDecayPerSecond = DEFAULT_TRAUMA_DECAY
        springInitialized = false
    }

    /**
     * Applies spring damping, handheld operator breathing, and trauma-squared shake directly into
     * [pose] in place without allocating.
     *
     * @param pose Target camera pose mutated in place.
     * @param unscaledDelta Unscaled frame step in seconds.
     * @param timelineSeconds Monotonic timeline clock in seconds for deterministic noise sampling.
     * @param smoothTime Critically damped spring smooth time (`0f` = no damping lag).
     * @param handheldIntensity Organic handheld breathing strength (`0f` = locked tripod, `1f` = strong handheld).
     * @param handheldFrequency Handheld breathing frequency in Hz (default `0.7f`).
     * @param baseTrauma Minimum ambient trauma floor for this shot (e.g. engine rumble or earthquake).
     */
    @Suppress("LongParameterList", "LongMethod")
    fun apply(
        pose: DirectedCameraPose,
        unscaledDelta: Float,
        timelineSeconds: Float,
        smoothTime: Float = 0f,
        handheldIntensity: Float = 0f,
        handheldFrequency: Float = DEFAULT_HANDHELD_FREQ,
        baseTrauma: Float = 0f,
    ) {
        // 1. Critically damped spring follow inertia (optional).
        if (smoothTime > 0f && unscaledDelta > 0f) {
            if (!springInitialized) {
                snapTo(pose)
            } else {
                pose.eye.set(dampedEye.smoothDamp(pose.eye, smoothTime, unscaledDelta))
                pose.center.set(dampedCenter.smoothDamp(pose.center, smoothTime, unscaledDelta))
                pose.fovYRadians = dampedFov.smoothDamp(pose.fovYRadians, smoothTime, unscaledDelta)
                pose.rollRadians = dampedRoll.smoothDamp(pose.rollRadians, smoothTime, unscaledDelta)
            }
        } else {
            snapTo(pose)
        }

        // 2. Decay trauma linearly, evaluate shake quadratically (trauma^2 feels explosive yet settles crisply).
        val effectiveTrauma = maxOf(trauma, baseTrauma.coerceIn(0f, 1f))
        if (unscaledDelta > 0f && trauma > 0f) {
            trauma = (trauma - traumaDecayPerSecond * unscaledDelta).coerceAtLeast(0f)
        }
        val shake = effectiveTrauma * effectiveTrauma
        val handheld = handheldIntensity.coerceAtLeast(0f)

        if (shake <= EPSILON && handheld <= EPSILON) {
            CameraOptics.cameraBasis(pose.eye, pose.center, pose.rollRadians, forward, right, up)
            pose.up.set(up)
            return
        }

        CameraOptics.cameraBasis(pose.eye, pose.center, pose.rollRadians, forward, right, up)
        val focusDist = pose.eye.distanceTo(pose.center).coerceAtLeast(MIN_FOCUS_DIST)

        var yawNoise = 0f
        var pitchNoise = 0f
        var rollNoise = 0f
        var offsetRight = 0f
        var offsetUp = 0f

        if (handheld > EPSILON) {
            val ht = timelineSeconds * handheldFrequency.coerceAtLeast(MIN_HANDHELD_FREQ)
            yawNoise += Noise.fractalNoise2D(ht, 0.13f, seed + SEED_HH_YAW, octaves = 2) * HANDHELD_YAW_RAD * handheld
            pitchNoise += Noise.fractalNoise2D(ht, 1.37f, seed + SEED_HH_PITCH, octaves = 2) * HANDHELD_PITCH_RAD * handheld
            rollNoise += Noise.fractalNoise2D(ht, 2.71f, seed + SEED_HH_ROLL, octaves = 2) * HANDHELD_ROLL_RAD * handheld
            offsetRight += Noise.fractalNoise2D(ht, 4.19f, seed + SEED_HH_X, octaves = 2) * HANDHELD_OFFSET * handheld
            offsetUp += Noise.fractalNoise2D(ht, 5.83f, seed + SEED_HH_Y, octaves = 2) * HANDHELD_OFFSET * handheld
        }

        if (shake > EPSILON) {
            val st = timelineSeconds * SHAKE_FREQUENCY_HZ
            yawNoise += Noise.fractalNoise2D(st, 0.5f, seed + SEED_SH_YAW, octaves = 3) * maxShakeYawRadians * shake
            pitchNoise += Noise.fractalNoise2D(st, 1.5f, seed + SEED_SH_PITCH, octaves = 3) * maxShakePitchRadians * shake
            rollNoise += Noise.fractalNoise2D(st, 2.5f, seed + SEED_SH_ROLL, octaves = 3) * maxShakeRollRadians * shake
            offsetRight += Noise.fractalNoise2D(st, 3.5f, seed + SEED_SH_X, octaves = 3) * maxShakeOffset * shake
            offsetUp += Noise.fractalNoise2D(st, 4.5f, seed + SEED_SH_Y, octaves = 3) * maxShakeOffset * shake
        }

        // Apply small translational shake in camera plane.
        pose.eye.x += right.x * offsetRight + up.x * offsetUp
        pose.eye.y += right.y * offsetRight + up.y * offsetUp
        pose.eye.z += right.z * offsetRight + up.z * offsetUp

        // Apply small angular yaw/pitch perturbation to forward direction.
        val cosY = cos(yawNoise)
        val sinY = sin(yawNoise)
        val cosP = cos(pitchNoise)
        val sinP = sin(pitchNoise)
        shakenForward.set(
            forward.x * cosY * cosP + right.x * sinY + up.x * sinP,
            forward.y * cosY * cosP + right.y * sinY + up.y * sinP,
            forward.z * cosY * cosP + right.z * sinY + up.z * sinP,
        ).normalize()

        pose.center.set(
            pose.eye.x + shakenForward.x * focusDist,
            pose.eye.y + shakenForward.y * focusDist,
            pose.eye.z + shakenForward.z * focusDist,
        )
        pose.rollRadians += rollNoise
        CameraOptics.rolledUp(shakenForward, pose.rollRadians, pose.up)
    }

    /** Constants for [CameraFeelRig]. */
    companion object {
        private const val DEFAULT_SEED = 1337
        private const val DEFAULT_TRAUMA_DECAY = 1.6f
        private const val DEFAULT_MAX_ANGLE_RAD = (4.0 * PI / 180.0).toFloat()
        private const val DEFAULT_MAX_ROLL_RAD = (6.0 * PI / 180.0).toFloat()
        private const val DEFAULT_MAX_OFFSET = 0.25f
        private const val DEFAULT_HANDHELD_FREQ = 0.7f
        private const val MIN_HANDHELD_FREQ = 0.05f
        private const val SHAKE_FREQUENCY_HZ = 18f
        private const val HANDHELD_YAW_RAD = (1.2 * PI / 180.0).toFloat()
        private const val HANDHELD_PITCH_RAD = (0.9 * PI / 180.0).toFloat()
        private const val HANDHELD_ROLL_RAD = (1.5 * PI / 180.0).toFloat()
        private const val HANDHELD_OFFSET = 0.08f
        private const val MIN_FOCUS_DIST = 0.1f
        private const val EPSILON = 1e-5f

        private const val SEED_HH_YAW = 11
        private const val SEED_HH_PITCH = 23
        private const val SEED_HH_ROLL = 37
        private const val SEED_HH_X = 53
        private const val SEED_HH_Y = 71
        private const val SEED_SH_YAW = 101
        private const val SEED_SH_PITCH = 131
        private const val SEED_SH_ROLL = 163
        private const val SEED_SH_X = 197
        private const val SEED_SH_Y = 229
    }
}
