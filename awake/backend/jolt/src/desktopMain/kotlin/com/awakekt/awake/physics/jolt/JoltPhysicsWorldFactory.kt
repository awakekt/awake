/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.PhysicsWorld

/** Completes immediately -- this binding's world has a plain constructor. */
actual suspend fun createJoltPhysicsWorld(
    gravity: Vec3f,
    layers: CollisionLayers,
): PhysicsWorld = JoltPhysicsWorld(gravity, layers)
