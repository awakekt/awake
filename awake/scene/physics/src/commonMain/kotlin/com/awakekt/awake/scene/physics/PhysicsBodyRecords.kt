/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import androidx.collection.MutableLongObjectMap
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.scene.core.transform.Transform

/**
 * What [PhysicsSystem] knows about one body it built: its entity, the motion type the body runs
 * with (`PhysicsBody.motionType` is only what the scene asks for), and its last two poses.
 */
internal class PhysicsBodyRecord(val entity: Entity, val handle: BodyHandle, val motionType: MotionType) {
    /** The last two simulated poses, once the backend has reported the body. */
    var pose: BodyPose? = null

    /** Where this record sits in [PhysicsBodyRecords]' dense list. */
    var listIndex: Int = -1
}

/**
 * [PhysicsSystem]'s bodies, found without hashing on the per-step paths (#1428).
 *
 * The scan over `Transform`+`PhysicsBody` finds a body's record by its entity's slot, an array
 * read. The backend's pose readback reports handles, so a body is also found by its handle's id,
 * in a primitive map that neither boxes the handle nor calls `equals`. [forEach] walks a dense list.
 *
 * A record outlives its entity when the entity is destroyed before [PhysicsSystem.destroyBody],
 * just as the body does. It keeps its handle so contacts and [PhysicsSystem.entityFor] still
 * resolve, and it gives up the slot to a new entity there.
 */
internal class PhysicsBodyRecords {
    private var bySlot = arrayOfNulls<PhysicsBodyRecord>(INITIAL_CAPACITY)
    private val byHandle = MutableLongObjectMap<PhysicsBodyRecord>()
    private val records = ArrayList<PhysicsBodyRecord>()

    /** The record of the body this system built for [entity] as [handle], or `null` when it built none. */
    fun built(entity: Entity, handle: BodyHandle): PhysicsBodyRecord? {
        val slot = entity.id
        val inSlot = if (slot < bySlot.size) bySlot[slot] else null
        if (inSlot != null && inSlot.handle == handle) return inSlot
        // A handle this system built for some other slot, or one it never built. Rare: the
        // component moved between entities, or a handle was set from outside.
        return byHandle[handle.id]
    }

    fun forHandle(handle: BodyHandle): PhysicsBodyRecord? = byHandle[handle.id]

    fun add(entity: Entity, handle: BodyHandle, motionType: MotionType): PhysicsBodyRecord {
        val record = PhysicsBodyRecord(entity, handle, motionType)
        val slot = entity.id
        if (slot >= bySlot.size) bySlot = bySlot.copyOf(maxOf(slot + 1, bySlot.size * 2))
        bySlot[slot] = record
        byHandle[handle.id] = record
        record.listIndex = records.size
        records += record
        return record
    }

    fun remove(handle: BodyHandle) {
        val record = byHandle.remove(handle.id) ?: return
        val slot = record.entity.id
        if (slot < bySlot.size && bySlot[slot] === record) bySlot[slot] = null
        val last = records.removeAt(records.lastIndex)
        if (last !== record) {
            records[record.listIndex] = last
            last.listIndex = record.listIndex
        }
        record.listIndex = -1
    }

    /** Visits every record, without allocating. */
    inline fun forEach(action: (PhysicsBodyRecord) -> Unit) {
        for (index in 0 until recordCount) action(recordAt(index))
    }

    val recordCount: Int get() = records.size

    fun recordAt(index: Int): PhysicsBodyRecord = records[index]

    private companion object {
        const val INITIAL_CAPACITY = 64
    }
}

/**
 * One body's previous and current simulated pose.
 *
 * Rotation is kept as a quaternion even though `Transform` stores Euler angles, because the
 * blend has to happen before the conversion: Euler angles cannot be interpolated. Halfway
 * between 350 and 10 degrees is 180 -- a body spinning past the wrap point would snap right
 * round rather than continue, once per revolution.
 */
internal class BodyPose(position: Vec3f, rotation: Quat) {
    private val previousPosition = Vec3f(position.x, position.y, position.z)
    private val previousRotation = Quat(rotation.x, rotation.y, rotation.z, rotation.w)
    private val currentPosition = Vec3f(position.x, position.y, position.z)
    private val currentRotation = Quat(rotation.x, rotation.y, rotation.z, rotation.w)

    /**
     * Every value is copied, never held.
     *
     * The pose handed to this is scratch that the next body overwrites, so assigning the
     * reference would leave every body sharing whichever one was visited last -- and rolling
     * previous from current would make the two halves the same object, so there would be
     * nothing left to interpolate between.
     */
    fun record(position: Vec3f, rotation: Quat) {
        previousPosition.set(currentPosition)
        previousRotation.set(currentRotation)
        currentPosition.set(position)
        currentRotation.set(rotation)
    }

    fun blendInto(transform: Transform, alpha: Float) {
        transform.position.x = previousPosition.x + (currentPosition.x - previousPosition.x) * alpha
        transform.position.y = previousPosition.y + (currentPosition.y - previousPosition.y) * alpha
        transform.position.z = previousPosition.z + (currentPosition.z - previousPosition.z) * alpha
        Quat.nlerp(previousRotation, currentRotation, alpha).toEuler(transform.rotation)
    }
}
