/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.PhysicsWorld

/**
 * Builds this target's [PhysicsWorld].
 *
 * `JoltPhysicsWorld` is a separate class per target rather than one `expect` class -- the three
 * bindings share no code, so there is nothing to declare in common. That left common code with
 * no way to obtain a world at all, which is why `:awake:physics:api` deliberately has no
 * factory and yet nothing outside this module had ever constructed one. This is that seam, and
 * it belongs here, in the backend, not in the neutral contract.
 *
 * `suspend` because of wasmJs alone: `jolt-physics` is an Emscripten module whose bootstrap
 * returns a `Promise`, so its world cannot exist until that resolves. The other three targets
 * complete immediately. Callers are scene `onReady` blocks, which are already `suspend`.
 *
 * `layers` is the world's collision matrix and cannot be changed afterwards -- Jolt builds its
 * broadphase and filter tables from it at construction.
 */
expect suspend fun createJoltPhysicsWorld(
    gravity: Vec3f = Vec3f(0f, -9.81f, 0f),
    layers: CollisionLayers = CollisionLayers.Default,
): PhysicsWorld
