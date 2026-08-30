/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics

import io.github.awakelab.awake.core.math.Vec3f

data class RaycastHit(val handle: BodyHandle, val point: Vec3f, val distance: Float)
