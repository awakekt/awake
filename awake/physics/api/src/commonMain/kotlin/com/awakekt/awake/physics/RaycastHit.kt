/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import com.awakekt.awake.core.math.Vec3f

/**
 * What a ray hit first, from [PhysicsWorld.raycast].
 *
 * [distance] is measured along the ray from its origin, not from anything else, so a caller can
 * compare two hits without knowing where either ray started. There is no normal here, unlike
 * [ShapeCastHit]: a ray is used to ask *what* is there, and anything that needs to slide along the
 * surface it found wants a shape cast instead.
 *
 * @property handle The physics body that was struck by the ray.
 * @property point World-space position where the ray intersection occurred.
 * @property distance Distance from the ray origin to [point] in world units.
 */
data class RaycastHit(val handle: BodyHandle, val point: Vec3f, val distance: Float)
