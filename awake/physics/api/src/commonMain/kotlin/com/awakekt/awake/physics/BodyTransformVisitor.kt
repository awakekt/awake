/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f

/**
 * Receives each body's pose from [PhysicsWorld.visitBodyTransforms].
 *
 * The handle arrives as its raw [BodyHandle.id], a `Long`, so a visit allocates nothing: a
 * [BodyHandle] passed through a Kotlin function type, as [PhysicsWorld.forEachBodyTransform] takes,
 * is boxed on every call, once per body per step (#1428). Wrap it again with `BodyHandle(handleId)`
 * inside [visit], which stays unboxed as a local or an argument to a function that takes [BodyHandle].
 *
 * [visit]'s position and rotation are scratch, reused between bodies, exactly as
 * [PhysicsWorld.forEachBodyTransform]'s are.
 */
fun interface BodyTransformVisitor {
    /**
     * One body's pose.
     *
     * @param handleId The body's [BodyHandle.id].
     * @param position The body's position; scratch, overwritten by the next body.
     * @param rotation The body's rotation; scratch, overwritten by the next body.
     */
    fun visit(handleId: Long, position: Vec3f, rotation: Quat)
}
