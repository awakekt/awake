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
 *
 * @property a The first colliding body handle (unordered).
 * @property b The second colliding body handle (unordered).
 * @property entityA The ECS entity owning body [a], or `null` if unowned or destroyed.
 * @property entityB The ECS entity owning body [b], or `null` if unowned or destroyed.
 * @property phase Whether contact began or ended during this step.
 */
data class PhysicsContact(
    val a: BodyHandle,
    val b: BodyHandle,
    val entityA: Entity?,
    val entityB: Entity?,
    val phase: ContactPhase,
)
