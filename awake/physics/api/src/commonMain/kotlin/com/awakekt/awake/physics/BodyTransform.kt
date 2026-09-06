/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f

/**
 * One body's read-back pose, as returned in bulk by [PhysicsWorld.syncTransforms].
 *
 * [rotation] is a quaternion because that is what every backend's solver actually holds. It
 * used to be Euler angles, to match `Transform`, which meant converting on the way out of all
 * four backends and gave a tumbling body gimbal lock in a value nothing had asked to be
 * Euler. `Transform` still stores Euler and still converts -- but once, at the ECS boundary,
 * where the engine's convention genuinely changes.
 */
data class BodyTransform(val handle: BodyHandle, val position: Vec3f, val rotation: Quat)
