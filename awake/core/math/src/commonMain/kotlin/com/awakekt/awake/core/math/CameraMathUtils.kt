/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mutable state representing an orbit/gameplay/editor camera pose.
 */
data class CameraPoseState(
    var yaw: Float = 0f,
    var pitch: Float = -0.4f,
    var distance: Float = 50f,
    val center: Vec3f = Vec3f(),
    val eye: Vec3f = Vec3f(),
) {
    /** Extracts yaw, pitch, distance, center, and eye from [lens]. */
    fun syncFromLens(lens: Lens) {
        center.set(lens.center)
        val dx = lens.eye.x - lens.center.x
        val dy = lens.eye.y - lens.center.y
        val dz = lens.eye.z - lens.center.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        distance = if (dist > 0.01f) dist else 50f
        pitch = if (dist > 0.01f) asin((-dy / dist).coerceIn(-1f, 1f)) else -0.4f
        yaw = atan2(-dx, dz)
        recomputeEye()
    }

    /** Recomputes [eye] position around [center] given current [yaw], [pitch], and [distance]. */
    fun recomputeEye(): Vec3f = CameraMathUtils.computeOrbitEye(center, yaw, pitch, distance, eye)
}

/**
 * Pure, zero-allocation math utilities and loud invariant guards for 3D cameras.
 *
 * Prevents GPU black-screen rendering, NaN matrix poisoning, and gimbal lock singularities.
 */
object CameraMathUtils {

    /** Maximum allowed pitch in radians (+/- 89 degrees) to prevent pitch singularity. */
    val PITCH_LIMIT_RAD: Float = (89.0 * PI / 180.0).toFloat()

    /** Minimum squared distance between camera eye and center target. */
    const val MIN_EYE_CENTER_DISTANCE_SQ: Float = 1e-6f

    /**
     * Loudly validates lens configuration parameters.
     *
     * @throws IllegalArgumentException if any parameter is non-finite or violates frustum constraints.
     */
    fun validateLensParameters(
        fovYDegrees: Float,
        near: Float,
        far: Float,
        aspect: Float = 1.0f,
    ) {
        require(fovYDegrees.isFinite() && fovYDegrees in 1.0f..179.0f) {
            "Camera vertical FOV must be in (1.0..179.0) degrees; was $fovYDegrees."
        }
        require(near.isFinite() && near > 0.0001f) {
            "Camera near clipping plane must be finite and positive (> 0.0001); was $near."
        }
        require(far.isFinite() && far > near) {
            "Camera far clipping plane ($far) must be finite and greater than near ($near)."
        }
        require(aspect.isFinite() && aspect > 0.0001f) {
            "Camera aspect ratio must be finite and positive; was $aspect."
        }
    }

    /**
     * Computes the normalized forward aim vector from Euler [yaw] and [pitch] (in radians).
     *
     * Clamps pitch to `[-PITCH_LIMIT_RAD, +PITCH_LIMIT_RAD]` and writes into [out] without allocating.
     * Convention: yaw = 0 faces -Z, +yaw turns right (+X), +pitch looks up (+Y).
     */
    fun forwardVector(yaw: Float, pitch: Float, out: Vec3f): Vec3f {
        require(yaw.isFinite() && pitch.isFinite()) {
            "Camera yaw and pitch must be finite; was yaw=$yaw, pitch=$pitch."
        }
        val clampedPitch = pitch.coerceIn(-PITCH_LIMIT_RAD, PITCH_LIMIT_RAD)
        val cp = cos(clampedPitch)
        out.set(
            sin(yaw) * cp,
            sin(clampedPitch),
            -cos(yaw) * cp,
        )
        return out
    }

    /**
     * Loudly validates [eye], [center], and [up], and writes a look-at view matrix into [out].
     *
     * Automatically heals collinear forward/up vectors (e.g. looking straight up) by substituting
     * an orthogonal secondary up-vector, preventing matrix zero-collapse and NaN poisoning.
     *
     * @throws IllegalArgumentException if eye/center are non-finite or coincident.
     */
    fun safeLookAt(
        eye: Vec3f,
        center: Vec3f,
        up: Vec3f = Vec3f.UP,
        out: Mat4 = Mat4(),
    ): Mat4 {
        require(eye.isAllFinite()) { "Camera eye position must be finite; was $eye." }
        require(center.isAllFinite()) { "Camera center position must be finite; was $center." }
        require(up.isAllFinite() && up.length3() > 0.0001f) { "Camera up vector must be non-zero and finite; was $up." }

        val dx = center.x - eye.x
        val dy = center.y - eye.y
        val dz = center.z - eye.z
        val distSq = dx * dx + dy * dy + dz * dz

        require(distSq >= MIN_EYE_CENTER_DISTANCE_SQ) {
            "Camera eye ($eye) and center ($center) cannot be coincident (distSq=$distSq < $MIN_EYE_CENTER_DISTANCE_SQ)."
        }

        val invDist = 1.0f / sqrt(distSq)
        val fx = dx * invDist
        val fy = dy * invDist
        val fz = dz * invDist

        // Check collinearity with up vector
        val upLen = up.length3()
        val invUpLen = 1.0f / upLen
        var ux = up.x * invUpLen
        var uy = up.y * invUpLen
        var uz = up.z * invUpLen

        val dot = fx * ux + fy * uy + fz * uz
        if (abs(dot) > 0.999f) {
            if (abs(fy) > 0.9f) {
                ux = 0f
                uy = 0f
                uz = 1f
            } else {
                ux = 0f
                uy = 1f
                uz = 0f
            }
        }

        // Side = forward x up
        var sx = fy * uz - fz * uy
        var sy = fz * ux - fx * uz
        var sz = fx * uy - fy * ux
        val sideLen = sqrt(sx * sx + sy * sy + sz * sz)
        val invSideLen = if (sideLen > 0f) 1.0f / sideLen else 1.0f
        sx *= invSideLen
        sy *= invSideLen
        sz *= invSideLen

        // Up = side x forward
        val realUx = sy * fz - sz * fy
        val realUy = sz * fx - sx * fz
        val realUz = sx * fy - sy * fx

        out.apply {
            m00 = sx
            m01 = sy
            m02 = sz
            m10 = realUx
            m11 = realUy
            m12 = realUz
            m20 = -fx
            m21 = -fy
            m22 = -fz
            m03 = -(sx * eye.x + sy * eye.y + sz * eye.z)
            m13 = -(realUx * eye.x + realUy * eye.y + realUz * eye.z)
            m23 = (fx * eye.x + fy * eye.y + fz * eye.z)
            m30 = 0f
            m31 = 0f
            m32 = 0f
            m33 = 1f
        }
        return out
    }

    /**
     * Calculates the eye position orbiting [center] at [distance] with Euler [yaw] and [pitch].
     */
    fun computeOrbitEye(
        center: Vec3f,
        yaw: Float,
        pitch: Float,
        distance: Float,
        outEye: Vec3f,
    ): Vec3f {
        require(distance > 0.0001f && distance.isFinite()) {
            "Orbit distance must be finite and positive; was $distance."
        }
        val forward = Vec3f()
        forwardVector(yaw, pitch, forward)
        outEye.set(
            center.x - forward.x * distance,
            center.y - forward.y * distance,
            center.z - forward.z * distance,
        )
        return outEye
    }

    /**
     * Calculates eye and center for a third-person camera tracking [targetPos] with [targetOffset].
     */
    fun computeThirdPersonPose(
        targetPos: Vec3f,
        targetOffset: Vec3f,
        yaw: Float,
        pitch: Float,
        distance: Float,
        outCenter: Vec3f,
        outEye: Vec3f,
    ) {
        outCenter.set(
            targetPos.x + targetOffset.x,
            targetPos.y + targetOffset.y,
            targetPos.z + targetOffset.z,
        )
        computeOrbitEye(outCenter, yaw, pitch, distance, outEye)
    }

    /**
     * Applies drag orbit rotation to [state] given pointer delta [dx] and [dy].
     */
    fun applyOrbitDrag(
        state: CameraPoseState,
        dx: Float,
        dy: Float,
        sensitivity: Float = 0.005f,
    ): Boolean {
        if (dx == 0f && dy == 0f) return false
        state.yaw += dx * sensitivity
        state.pitch = (state.pitch - dy * sensitivity).coerceIn(-PITCH_LIMIT_RAD, PITCH_LIMIT_RAD)
        state.recomputeEye()
        return true
    }

    /**
     * Applies exponential scroll or drag zoom to [state], keeping distance within `[minDist, maxDist]`.
     *
     * Uses scale factor `exp(-zoomDelta * rate)` to feel equally smooth at close (3m) or distant (1000m) ranges.
     */
    fun applyZoom(
        state: CameraPoseState,
        zoomDelta: Float,
        rate: Float = 0.1f,
        minDistance: Float = 3.0f,
        maxDistance: Float = 3000.0f,
    ): Boolean {
        if (zoomDelta == 0f) return false
        val newDist = (state.distance * exp(-zoomDelta * rate)).coerceIn(minDistance, maxDistance)
        if (abs(newDist - state.distance) < 1e-4f) return false
        state.distance = newDist
        state.recomputeEye()
        return true
    }

    /**
     * Applies horizontal translation pan to [state.center] along current camera orientation.
     *
     * @param state The pose this moves; its centre and eye are both updated.
     * @param strafeX +1 moves Right, -1 moves Left
     * @param liftY +1 moves Up, -1 moves Down
     * @param forwardZ +1 moves Forward, -1 moves Backward
     * @param step World units per unit of input.
     */
    fun applyHorizontalPan(
        state: CameraPoseState,
        strafeX: Float,
        liftY: Float,
        forwardZ: Float,
        step: Float,
    ): Boolean {
        if (strafeX == 0f && liftY == 0f && forwardZ == 0f) return false
        val forwardH = Vec3f(sin(state.yaw), 0f, -cos(state.yaw))
        val rightH = Vec3f(cos(state.yaw), 0f, sin(state.yaw))

        state.center.set(
            state.center.x + (forwardH.x * forwardZ + rightH.x * strafeX) * step,
            state.center.y + liftY * step,
            state.center.z + (forwardH.z * forwardZ + rightH.z * strafeX) * step,
        )
        state.recomputeEye()
        return true
    }

    /**
     * Applies 2D screen-space panning to [state.center] parallel to the camera view plane.
     *
     * @param state The pose this moves; its centre and eye are both updated.
     * @param screenDx Screen movement in X (Right/Left)
     * @param screenDy Screen movement in Y (Up/Down)
     * @param panScale Units per screen pixel (typically proportional to distance)
     */
    fun applyScreenSpacePan(
        state: CameraPoseState,
        screenDx: Float,
        screenDy: Float,
        panScale: Float = 0.002f,
    ): Boolean {
        if (screenDx == 0f && screenDy == 0f) return false
        val fwd = Vec3f()
        forwardVector(state.yaw, state.pitch, fwd)
        // (-fwd.z, 0, fwd.x), not (fwd.z, 0, -fwd.x): the latter is the LEFT vector under this
        // file's own convention (yaw = 0 faces -Z, +yaw turns toward +X), and it flipped `up`
        // with it, so panning was inverted on both axes. Facing -Z, right must be +X.
        val right = Vec3f(-fwd.z, 0f, fwd.x).normalize()
        val up = right.cross(fwd).normalize()

        val scale = panScale * state.distance
        state.center.set(
            state.center.x - right.x * screenDx * scale + up.x * screenDy * scale,
            state.center.y - right.y * screenDx * scale + up.y * screenDy * scale,
            state.center.z - right.z * screenDx * scale + up.z * screenDy * scale,
        )
        state.recomputeEye()
        return true
    }

    /**
     * Computes 6-DOF WASD free flight translation step along camera orientation.
     */
    fun computeFreeFlyStep(
        currentPos: Vec3f,
        yaw: Float,
        pitch: Float,
        moveInput: Vec3f,
        speed: Float,
        dt: Float,
        outPos: Vec3f,
    ): Vec3f {
        require(speed.isFinite() && speed > 0f) { "FreeFly speed must be positive and finite; was $speed." }
        require(dt.isFinite() && dt >= 0f) { "Delta time dt must be non-negative and finite; was $dt." }

        val fwd = Vec3f()
        forwardVector(yaw, pitch, fwd)

        val right = Vec3f(fwd.z, 0f, -fwd.x).normalize()
        val up = Vec3f.UP

        val step = speed * dt
        outPos.set(
            currentPos.x + (right.x * moveInput.x + up.x * moveInput.y + fwd.x * moveInput.z) * step,
            currentPos.y + (right.y * moveInput.x + up.y * moveInput.y + fwd.y * moveInput.z) * step,
            currentPos.z + (right.z * moveInput.x + up.z * moveInput.y + fwd.z * moveInput.z) * step,
        )
        return outPos
    }

    private fun Vec3f.isAllFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
}
