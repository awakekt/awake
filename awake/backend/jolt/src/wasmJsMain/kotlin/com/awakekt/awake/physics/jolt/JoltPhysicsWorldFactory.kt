/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.PhysicsWorld

/** The one target that actually suspends: `jolt-physics` is an Emscripten module and its
 * bootstrap `Promise` has to resolve before a world exists. */
actual suspend fun createJoltPhysicsWorld(
    gravity: Vec3f,
    layers: CollisionLayers,
): PhysicsWorld = JoltPhysicsWorld.create(gravity, layers)
