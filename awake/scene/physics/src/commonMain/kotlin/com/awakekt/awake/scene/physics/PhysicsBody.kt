/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.DegreesOfFreedom
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.defaultLayerFor

/**
 * Incremented whenever any [PhysicsBody.motionType] changes in place, so [PhysicsSystem] can skip
 * scanning settled static bodies on every fixed step without missing an inspector edit.
 */
internal var physicsBodyMotionTypeEdits: Int = 0
    private set

/**
 * Incremented whenever any [PhysicsBody.handle] changes in place, so [PhysicsSystem] detects
 * externally cleared or reassigned handles without scanning settled bodies every fixed step.
 */
internal var physicsBodyHandleEdits: Int = 0
    private set

/**
 * Declares that an entity should have a live physics body -- same "declared in scene JSON,
 * backed by a live GPU/native resource" shape as [MeshRenderer] (a plain, non-[Poolable]
 * component carrying just enough to describe the resource before it exists), except the
 * live resource here is a jolt-jni body rather than a GPU handle.
 *
 * [handle] starts `null` and stays that way until [com.awakekt.awake.scene.physics.PhysicsSystem]
 * actually creates the backing body -- ECS components (including ones loaded straight from
 * `scene.json` via `SceneLoader`) are constructed long before any `PhysicsWorld` exists, so
 * this can't be a constructor-required, non-null field the way [shape]/[motionType] are.
 */
class PhysicsBody(
    /**
     * Not a `var`. Changing a shape means choosing a variant *and* its parameters, and there is no
     * editor field for that yet -- a settable one would be a control that silently does nothing,
     * which is the trap `Camera.isPrimary` already set.
     */
    val shape: PhysicsShape,
    motionType: MotionType,
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
     * serve an edit nobody has asked for yet.
     */
    val sensor: Boolean = false,
    handle: BodyHandle? = null,
    /** Allowed motion in world axes, fixed at creation like [layer] and [sensor]. */
    val degreesOfFreedom: DegreesOfFreedom = DegreesOfFreedom.ALL,
) {
    /**
     * Settable, because [com.awakekt.awake.scene.physics.PhysicsSystem] rebuilds the
     * backing body when this changes. The rebuild destroys and recreates, so the body's velocity
     * does not survive it -- switching a falling crate to `STATIC` and back drops it from rest.
     */
    var motionType: MotionType = motionType
        set(value) {
            if (field != value) {
                field = value
                physicsBodyMotionTypeEdits++
            }
        }

    private var handleBacking: BodyHandle? = handle

    var handle: BodyHandle?
        get() = handleBacking
        set(value) {
            if (handleBacking != value) {
                handleBacking = value
                physicsBodyHandleEdits++
            }
        }

    /** Updates [handle] from [PhysicsSystem] without tripping external handle-edit rescan tracking. */
    internal fun assignHandleSilently(value: BodyHandle?) {
        handleBacking = value
    }

    /** Destructures [shape]. */
    operator fun component1(): PhysicsShape = shape

    /** Destructures [motionType]. */
    operator fun component2(): MotionType = motionType

    /** Destructures [layer]. */
    operator fun component3(): CollisionLayer = layer

    /** Destructures [sensor]. */
    operator fun component4(): Boolean = sensor

    /** Destructures [handle]. */
    operator fun component5(): BodyHandle? = handle

    /** Destructures [degreesOfFreedom]. */
    operator fun component6(): DegreesOfFreedom = degreesOfFreedom

    /** Returns a copy of this body with any supplied properties overridden. */
    fun copy(
        shape: PhysicsShape = this.shape,
        motionType: MotionType = this.motionType,
        layer: CollisionLayer = this.layer,
        sensor: Boolean = this.sensor,
        handle: BodyHandle? = this.handle,
        degreesOfFreedom: DegreesOfFreedom = this.degreesOfFreedom,
    ): PhysicsBody = PhysicsBody(
        shape = shape,
        motionType = motionType,
        layer = layer,
        sensor = sensor,
        handle = handle,
        degreesOfFreedom = degreesOfFreedom,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhysicsBody) return false
        return shape == other.shape &&
            motionType == other.motionType &&
            layer == other.layer &&
            sensor == other.sensor &&
            handle == other.handle &&
            degreesOfFreedom == other.degreesOfFreedom
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + motionType.hashCode()
        result = 31 * result + layer.hashCode()
        result = 31 * result + sensor.hashCode()
        result = 31 * result + (handle?.hashCode() ?: 0)
        result = 31 * result + degreesOfFreedom.hashCode()
        return result
    }

    override fun toString(): String =
        "PhysicsBody(shape=$shape, motionType=$motionType, layer=$layer, " +
            "sensor=$sensor, handle=$handle, degreesOfFreedom=$degreesOfFreedom)"
}
