/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.PhysicsWorldConfig

/** Completes immediately -- defaults to [PhysicsWorldConfig.Mobile] budgets on Android. */
actual suspend fun createJoltPhysicsWorld(
    gravity: Vec3f,
    layers: CollisionLayers,
): PhysicsWorld = JoltPhysicsWorld(gravity, layers, PhysicsWorldConfig.Mobile)

/** Completes immediately -- builds an Android [JoltPhysicsWorld] with explicit [config]. */
actual suspend fun createJoltPhysicsWorld(
    gravity: Vec3f,
    layers: CollisionLayers,
    config: PhysicsWorldConfig,
): PhysicsWorld = JoltPhysicsWorld(gravity, layers, config)
