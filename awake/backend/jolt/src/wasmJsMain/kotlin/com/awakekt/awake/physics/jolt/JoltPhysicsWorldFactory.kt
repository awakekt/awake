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

/** The one target that actually suspends: `jolt-physics` is an Emscripten module and its
 * bootstrap `Promise` has to resolve before a world exists. */
actual suspend fun createJoltPhysicsWorld(
    gravity: Vec3f,
    layers: CollisionLayers,
): PhysicsWorld = JoltPhysicsWorld.create(gravity, layers)

/** Suspends while initializing the Emscripten module and builds a web [JoltPhysicsWorld]. */
actual suspend fun createJoltPhysicsWorld(
    gravity: Vec3f,
    layers: CollisionLayers,
    config: PhysicsWorldConfig,
): PhysicsWorld = JoltPhysicsWorld.create(gravity, layers)
