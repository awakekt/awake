/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics.jolt

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.CollisionLayers
import io.github.awakelab.awake.physics.PhysicsWorld

/** Completes immediately -- this binding's world has a plain constructor. */
actual suspend fun createJoltPhysicsWorld(
    gravity: Vec3f,
    layers: CollisionLayers,
): PhysicsWorld = JoltPhysicsWorld(gravity, layers)
