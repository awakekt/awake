/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.InterpolatedSystem
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.physics.PhysicsBody

/**
 * Bridges [PhysicsBody]/[Transform] ECS components to a live [physicsWorld]. This is
 * constructed and `update()`-called by game/sample code, never wired into `SceneRuntime`
 * itself, since only the caller knows which concrete [PhysicsWorld] backend (jolt-jni today)
 * to construct.
 *
 * Body creation is lazy and one-shot per entity: [PhysicsBody.handle] starts `null` (it's
 * constructed before any [PhysicsWorld] exists, e.g. straight out of `SceneLoader`), so the
 * first [update] that sees a `null` handle creates the backing body from that entity's
 * paired [Transform] and stores the returned [BodyHandle] both on the component and in
 * `handleToEntity` (needed because [PhysicsWorld.syncTransforms] hands back handles, not
 * entities, and this system is the only place able to bridge that back to ECS state).
 *
 * [PhysicsWorld.step]/[PhysicsWorld.syncTransforms] are each called exactly once per
 * [update] -- never once per body -- matching [PhysicsWorld.syncTransforms]'s own "batched
 * readback" contract.
 */
class PhysicsSystem(
    private val physicsWorld: PhysicsWorld,
) : InterpolatedSystem {
    // LinkedHashMap and LinkedHashSet, not the hash forms: teardown iterates these, and
    // destruction order decides which body ids Jolt hands back out next, which feeds its
    // island ordering and so the simulation. Insertion order replays; hash order does not.
    private val handleToEntity = LinkedHashMap<BodyHandle, Entity>()

    /**
     * The motion type each live body was actually built with.
     *
     * `PhysicsBody.motionType` is what the scene *asks* for and an inspector can change it; this is
     * what the simulation is currently running. Without the second value there is nothing to
     * compare against, and an edit would leave the component saying `STATIC` while a dynamic body
     * kept falling.
     */
    private val builtMotionType = LinkedHashMap<BodyHandle, MotionType>()

    /**
     * The last two simulated poses per body, for [interpolate].
     *
     * Retained rather than read fresh, and that is not an optimisation: [PhysicsWorld.forEachBodyTransform]
     * reports only what a backend has awake, so a settled body is simply not visited. Without a
     * record here it would have no pose at all rather than the one it came to rest in.
     */
    private val poses = LinkedHashMap<BodyHandle, BodyPose>()

    override fun update(world: World, delta: Float) {
        val family = world.family<Transform, PhysicsBody>()
        family.forEach { entity, transform, physicsBody ->
            val live = physicsBody.handle
            // A rebuild rather than a mutation: the backend port has no setMotionType, and adding
            // one to four backends to serve an editor field is the larger change.
            if (live != null && builtMotionType[live] != physicsBody.motionType) {
                physicsWorld.destroyBody(live)
                handleToEntity.remove(live)
                builtMotionType.remove(live)
                poses.remove(live)
                physicsBody.handle = null
            }
            if (physicsBody.handle == null) {
                val handle = physicsWorld.createBody(
                    physicsBody.shape,
                    transform.position,
                    Quat.fromEuler(transform.rotation),
                    physicsBody.motionType,
                    physicsBody.layer,
                    physicsBody.sensor,
                )
                physicsBody.handle = handle
                handleToEntity[handle] = entity
                builtMotionType[handle] = physicsBody.motionType
            }
        }

        physicsWorld.step(delta)

        // The visitor form, not the list one: this runs every fixed step, and the list allocates
        // a vector and a quaternion per body to hand back values that are copied out immediately.
        physicsWorld.forEachBodyTransform { handle, position, rotation ->
            val entity = handleToEntity[handle]
            val transform = entity?.let { world.get<Transform>(it) }
            if (transform != null) {
                transform.position.x = position.x
                transform.position.y = position.y
                transform.position.z = position.z
                // The simulation's quaternion becomes the Euler angles `Transform` stores. This is
                // the one place in the engine that conversion happens, and it writes in place
                // because it runs per body per frame.
                rotation.toEuler(transform.rotation)
            }
            poses.getOrPut(handle) { BodyPose(position, rotation) }.record(position, rotation)
        }
    }

    /**
     * Blends each body's last two simulated poses into its [Transform].
     *
     * Called once per rendered frame rather than per step, and it overwrites what [update] wrote --
     * [update] leaves the newest pose there so a caller that never interpolates behaves exactly as
     * it did before this existed.
     */
    override fun interpolate(world: World, alpha: Float) {
        poses.forEach { (handle, pose) ->
            val entity = handleToEntity[handle] ?: return@forEach
            val transform = world.get<Transform>(entity) ?: return@forEach
            pose.blendInto(transform, alpha)
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
    private class BodyPose(position: Vec3f, rotation: Quat) {
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
}
