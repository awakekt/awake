/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

import com.awakekt.awake.core.math.Vec3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pure optical and camera-basis math for cinematic lenses, focal-length conversions, dolly-zoom
 * (Vertigo) coupling, and Dutch camera roll.
 *
 * Every vector operation writes into caller-provided `out` vectors and allocates nothing, so it is
 * safe on per-frame camera director paths.
 */
object CameraOptics {
    /** Standard 35mm full-frame vertical sensor gate height in millimeters (36x24 mm). */
    const val FULL_FRAME_SENSOR_HEIGHT_MM: Float = 24f

    /** Minimum supported vertical field of view in radians (~1 degree). */
    const val MIN_FOV_Y_RADIANS: Float = (1.0 * PI / 180.0).toFloat()

    /** Maximum supported vertical field of view in radians (~175 degrees). */
    const val MAX_FOV_Y_RADIANS: Float = (175.0 * PI / 180.0).toFloat()

    /**
     * Converts a physical lens [focalLengthMm] (e.g. `24f`, `35f`, `50f`, `85f`) to vertical field
     * of view in radians for a sensor of [sensorHeightMm].
     *
     * @param focalLengthMm Lens focal length in millimeters (must be positive).
     * @param sensorHeightMm Vertical gate height in millimeters (default 24mm full-frame).
     * @return Vertical field of view in radians, clamped to `[MIN_FOV_Y_RADIANS, MAX_FOV_Y_RADIANS]`.
     */
    fun focalLengthMmToFovYRadians(
        focalLengthMm: Float,
        sensorHeightMm: Float = FULL_FRAME_SENSOR_HEIGHT_MM,
    ): Float {
        require(focalLengthMm > 0f && focalLengthMm.isFinite()) {
            "focalLengthMm must be finite and positive, got $focalLengthMm."
        }
        require(sensorHeightMm > 0f && sensorHeightMm.isFinite()) {
            "sensorHeightMm must be finite and positive, got $sensorHeightMm."
        }
        val fov = 2f * atan(sensorHeightMm / (2f * focalLengthMm))
        return fov.coerceIn(MIN_FOV_Y_RADIANS, MAX_FOV_Y_RADIANS)
    }

    /**
     * Converts vertical field of view [fovYRadians] back to an equivalent focal length in
     * millimeters for a sensor of [sensorHeightMm].
     *
     * @param fovYRadians Vertical field of view in radians in `(0, pi)`.
     * @param sensorHeightMm Vertical gate height in millimeters (default 24mm full-frame).
     * @return Equivalent focal length in millimeters.
     */
    fun fovYRadiansToFocalLengthMm(
        fovYRadians: Float,
        sensorHeightMm: Float = FULL_FRAME_SENSOR_HEIGHT_MM,
    ): Float {
        require(fovYRadians > 0f && fovYRadians < PI_F && fovYRadians.isFinite()) {
            "fovYRadians must be finite and in (0, pi), got $fovYRadians."
        }
        require(sensorHeightMm > 0f && sensorHeightMm.isFinite()) {
            "sensorHeightMm must be finite and positive, got $sensorHeightMm."
        }
        return sensorHeightMm / (2f * tan(fovYRadians * HALF))
    }

    /**
     * Computes the camera distance required at [targetFovYRadians] so a subject at [baseDistance]
     * under [baseFovYRadians] keeps the exact same projected screen height (dolly-zoom / Vertigo).
     *
     * @param baseDistance Starting distance from camera eye to subject plane.
     * @param baseFovYRadians Starting vertical field of view in radians.
     * @param targetFovYRadians New vertical field of view in radians.
     * @return Coupled camera distance in world units.
     */
    fun dollyZoomDistance(
        baseDistance: Float,
        baseFovYRadians: Float,
        targetFovYRadians: Float,
    ): Float {
        require(baseDistance > 0f && baseDistance.isFinite()) {
            "baseDistance must be finite and positive, got $baseDistance."
        }
        val baseHalfHeight = baseDistance * tan(baseFovYRadians.coerceIn(MIN_FOV_Y_RADIANS, MAX_FOV_Y_RADIANS) * HALF)
        val targetTan = tan(targetFovYRadians.coerceIn(MIN_FOV_Y_RADIANS, MAX_FOV_Y_RADIANS) * HALF)
        return baseHalfHeight / targetTan
    }

    /**
     * Writes into [out] a unit camera up vector perpendicular to unit [forward], rolled clockwise
     * around [forward] by [rollRadians] (Dutch tilt).
     *
     * Falls back safely to `+Z` as the reference up when looking straight up or down, preventing
     * degenerate cross products.
     *
     * @param forward Normalized camera view direction (eye -> center).
     * @param rollRadians Roll angle in radians (`+` tilts the camera clockwise looking along forward).
     * @param out Destination vector mutated in place.
     * @return [out], for chaining.
     */
    fun rolledUp(forward: Vec3f, rollRadians: Float, out: Vec3f): Vec3f {
        val refX: Float
        val refY: Float
        val refZ: Float
        if (abs(forward.y) > VERTICAL_LIMIT) {
            refX = 0f
            refY = 0f
            refZ = 1f
        } else {
            refX = 0f
            refY = 1f
            refZ = 0f
        }
        // right = normalize(forward x refUp)
        var rx = forward.y * refZ - forward.z * refY
        var ry = forward.z * refX - forward.x * refZ
        var rz = forward.x * refY - forward.y * refX
        val rLen = sqrt(rx * rx + ry * ry + rz * rz)
        if (rLen <= EPSILON) {
            return out.set(0f, 1f, 0f)
        }
        val invRLen = 1f / rLen
        rx *= invRLen
        ry *= invRLen
        rz *= invRLen

        // levelUp = right x forward
        val ux = ry * forward.z - rz * forward.y
        val uy = rz * forward.x - rx * forward.z
        val uz = rx * forward.y - ry * forward.x

        return if (rollRadians == 0f) {
            out.set(ux, uy, uz).normalize()
        } else {
            val c = cos(rollRadians)
            val s = sin(rollRadians)
            out.set(
                ux * c + rx * s,
                uy * c + ry * s,
                uz * c + rz * s,
            ).normalize()
        }
    }

    /**
     * Writes an orthonormal right-handed camera basis ([outForward], [outRight], [outUp]) for a
     * camera at [eye] looking at [center] with Dutch [rollRadians], allocating nothing.
     *
     * @param eye World-space camera eye position.
     * @param center World-space camera look-at point.
     * @param rollRadians Camera roll angle in radians.
     * @param outForward Destination for unit forward direction (eye -> center).
     * @param outRight Destination for unit right direction.
     * @param outUp Destination for unit up direction.
     */
    @Suppress("LongParameterList")
    fun cameraBasis(
        eye: Vec3f,
        center: Vec3f,
        rollRadians: Float,
        outForward: Vec3f,
        outRight: Vec3f,
        outUp: Vec3f,
    ) {
        outForward.set(center).sub(eye)
        if (outForward.length3() <= EPSILON) {
            outForward.set(0f, 0f, -1f)
        } else {
            outForward.normalize()
        }
        rolledUp(outForward, rollRadians, outUp)
        outRight.set(
            outForward.y * outUp.z - outForward.z * outUp.y,
            outForward.z * outUp.x - outForward.x * outUp.z,
            outForward.x * outUp.y - outForward.y * outUp.x,
        ).normalize()
    }

    private const val HALF = 0.5f
    private const val PI_F = PI.toFloat()
    private const val VERTICAL_LIMIT = 0.99f
    private const val EPSILON = 1e-6f
}
