/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.ContactPhase

/**
 * One contact from the last physics step, with the entities its bodies belong to.
 *
 * [a] and [b] are unordered, as in `ContactEvent`. [entityA] and [entityB] are `null` for a body
 * [PhysicsSystem] did not build, such as a character controller's, and for one whose entity has
 * been destroyed.
 */
data class PhysicsContact(
    val a: BodyHandle,
    val b: BodyHandle,
    val entityA: Entity?,
    val entityB: Entity?,
    val phase: ContactPhase,
)
