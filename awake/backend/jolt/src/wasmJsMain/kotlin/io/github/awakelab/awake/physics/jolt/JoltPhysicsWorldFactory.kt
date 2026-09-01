/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics.jolt

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.CollisionLayers
import io.github.awakelab.awake.physics.PhysicsWorld

/** The one target that actually suspends: `jolt-physics` is an Emscripten module and its
 * bootstrap `Promise` has to resolve before a world exists. */
actual suspend fun createJoltPhysicsWorld(
    gravity: Vec3f,
    layers: CollisionLayers,
): PhysicsWorld = JoltPhysicsWorld.create(gravity, layers)
