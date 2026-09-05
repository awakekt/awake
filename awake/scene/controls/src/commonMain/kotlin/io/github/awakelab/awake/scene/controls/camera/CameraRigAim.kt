/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.controls.camera

import io.github.awakelab.awake.core.math.Vec3f
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Points an orbit rig at an already-composed shot, so turning the rig on does not move the camera.
 *
 * The exact inverse of the forward vector [CameraSystem] builds from `yaw` and `pitch`, and it
 * lives here so the two cannot drift apart. Deriving it by hand at a call site is easy to get
 * subtly wrong and hard to see: `CameraSystem` places the eye *behind* the pivot
 * (`eye = center - forward * distance`), so the vector from centre to eye is the **negation** of
 * the forward vector, and both angles pick up a sign from that. Get it wrong and the rig aims at
 * the mirror image of the shot it was handed -- which, with the orbit mode's smoothing, is not a
 * jump but a slow glide to somewhere under the floor.
 *
 * [CameraRigAimTest] round-trips this against `CameraSystem` itself rather than against the
 * algebra, so a change to either side has to keep them agreeing.
 */
fun CameraRig.aimAt(eye: Vec3f, center: Vec3f) {
    val toEyeX = eye.x - center.x
    val toEyeY = eye.y - center.y
    val toEyeZ = eye.z - center.z
    val length = max(sqrt(toEyeX * toEyeX + toEyeY * toEyeY + toEyeZ * toEyeZ), MINIMUM_DISTANCE)

    distance = length
    // Negated, both of them: `toEye` is `-forward * distance`.
    pitch = asin((-toEyeY / length).coerceIn(-1f, 1f)).coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
    yaw = atan2(-toEyeX, toEyeZ)
}

/**
 * The steepest a rig will look, matching what `CameraSystem` clamps a drag to.
 *
 * Clamped here as well as there, because a shot authored steeper than this would otherwise sit at
 * an angle the rig cannot hold: the first drag would snap it to the limit, which is a jump the
 * viewer did not ask for at the moment they touched the mouse. Reframing at the limit up front is
 * the smaller surprise.
 */
private val PITCH_LIMIT = (85.0 * PI / 180.0).toFloat()

/** Guards the degenerate case: an eye sitting exactly on its pivot has no direction to derive. */
private const val MINIMUM_DISTANCE = 0.0001f
