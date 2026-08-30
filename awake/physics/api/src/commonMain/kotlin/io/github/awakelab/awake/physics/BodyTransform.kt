/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics

import io.github.awakelab.awake.core.math.Vec3f

/** One body's read-back pose, as returned in bulk by [PhysicsWorld.syncTransforms] --
 * [rotation] is Euler angles (radians) rather than a quaternion, matching the rest of this
 * engine's `Transform`/`Camera` convention (see awake:scene's `Transform` component)
 * instead of introducing a new quaternion type into a coarse, backend-neutral API. */
data class BodyTransform(val handle: BodyHandle, val position: Vec3f, val rotation: Vec3f)
