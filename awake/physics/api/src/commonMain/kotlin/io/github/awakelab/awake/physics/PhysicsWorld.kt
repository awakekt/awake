/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics

import io.github.awakelab.awake.core.math.Vec3f

/**
 * Jolt Physics integration, slice 1 (see docs/reference/decision-log.md): mirrors
 * `awake:engine:render-api`/`awake:backend:vulkan`'s own module split -- a neutral
 * commonMain-only interface here, a concrete implementation per native binding in its own
 * backend module (`awake:backend:jolt` today). Its desktop/Android implementation uses
 * jolt-jni, iOS uses JoltC cinterop, and wasmJs uses JoltPhysics.js. Bindings remain
 * target-specific; this common contract deliberately exposes no backend factory.
 *
 * There is deliberately no factory function in this module -- platform bootstrap code
 * (a game's `Application`/`View` implementation) constructs the concrete
 * `JoltPhysicsWorld` from `awake:backend:jolt` directly, the same way `Renderer`'s concrete
 * type is constructed by platform bootstrap code rather than by `awake:engine:render-api`
 * itself.
 */
interface PhysicsWorld {
    fun createBody(
        shape: PhysicsShape,
        position: Vec3f,
        rotation: Vec3f,
        motionType: MotionType,
    ): BodyHandle

    fun destroyBody(handle: BodyHandle)

    fun step(deltaTime: Float)

    /** Batched readback of every tracked body's current pose -- one call per frame, never one
     * call per body per frame. Physics steps happen once per frame regardless of how many
     * bodies exist, so this API mirrors that: a per-body-per-frame JNI/cinterop crossing
     * would scale with body count instead of staying constant. */
    fun syncTransforms(): List<BodyTransform>

    fun raycast(origin: Vec3f, direction: Vec3f, maxDistance: Float): RaycastHit?

    fun destroy()
}
