/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.defaultLayerFor

/**
 * Declares that an entity should have a live physics body -- same "declared in scene JSON,
 * backed by a live GPU/native resource" shape as [MeshRenderer] (a plain, non-[Poolable]
 * data class carrying just enough to describe the resource before it exists), except the
 * live resource here is a jolt-jni body rather than a GPU handle.
 *
 * [handle] starts `null` and stays that way until [com.awakekt.awake.scene.physics.PhysicsSystem]
 * actually creates the backing body -- ECS components (including ones loaded straight from
 * `scene.json` via `SceneLoader`) are constructed long before any `PhysicsWorld` exists, so
 * this can't be a constructor-required, non-null field the way [shape]/[motionType] are.
 */
data class PhysicsBody(
    /**
     * Not a `var`. Changing a shape means choosing a variant *and* its parameters, and there is no
     * editor field for that yet -- a settable one would be a control that silently does nothing,
     * which is the trap `Camera.isPrimary` already set.
     */
    val shape: PhysicsShape,
    /**
     * Settable, because [com.awakekt.awake.scene.physics.PhysicsSystem] rebuilds the
     * backing body when this changes. The rebuild destroys and recreates, so the body's velocity
     * does not survive it -- switching a falling crate to `STATIC` and back drops it from rest.
     */
    var motionType: MotionType,
    /**
     * Which collision layer the body belongs to.
     *
     * Defaults from [motionType] -- static bodies are world, everything else moves -- which is
     * what every body got before layers existed. Not a `var`: unlike a motion type, a layer is not
     * something the simulation can be told to change, and `PhysicsWorld` has no setter for it, so
     * an editable field here would rebuild the body to serve an edit nobody has asked for yet.
     */
    val layer: CollisionLayer = defaultLayerFor(motionType),
    /**
     * Whether this body detects what passes through it instead of blocking it.
     *
     * A trigger volume -- a pickup, a checkpoint, a damage zone -- and the only kind of body whose
     * contacts `PhysicsWorld.drainContacts` reports. Not a `var` for the same reason as [layer]:
     * `PhysicsWorld` fixes it at creation, so an editable field here would rebuild the body to
     * serve an edit nobody has asked for.
     */
    val sensor: Boolean = false,
    var handle: BodyHandle? = null,
)
