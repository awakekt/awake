/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.ContactEvent
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody

/**
 * Bridges [PhysicsBody]/[Transform] ECS components to a live [physicsWorld]. This is
 * constructed and `update()`-called by game/sample code, never wired into `SceneRuntime`
 * itself, since only the caller knows which concrete [PhysicsWorld] backend (jolt-jni today)
 * to construct.
 *
 * Body creation is lazy and one-shot per entity: [PhysicsBody.handle] starts `null` (it's
 * constructed before any [PhysicsWorld] exists, e.g. straight out of `SceneLoader`), so the
 * first [update] that sees a `null` handle creates the backing body from that entity's
 * paired [Transform] and stores the returned [BodyHandle] both on the component and in its own
 * body records (needed because [PhysicsWorld.syncTransforms] hands back handles, not
 * entities, and this system is the only place able to bridge that back to ECS state).
 *
 * [PhysicsWorld.step]/[PhysicsWorld.syncTransforms] are each called exactly once per
 * [update] -- never once per body -- matching [PhysicsWorld.syncTransforms]'s own "batched
 * readback" contract.
 *
 * It is also the world's one [PhysicsWorld.drainContacts] caller. A drain hands its events to one
 * caller and forgets them, so this system drains after every step and republishes the step's events
 * as [contacts], which any number of systems registered after it can read. Draining here
 * unconditionally also stops the buffer growing in a game that never reads contacts. Code that
 * called [PhysicsWorld.drainContacts] itself now receives nothing and should read [contacts].
 */
class PhysicsSystem(
    private val physicsWorld: PhysicsWorld,
) : InterpolatedSystem {
    /**
     * Every body this system built, with the motion type it was actually built with and its last two
     * simulated poses, for [interpolate].
     *
     * The motion type is kept because `PhysicsBody.motionType` is what the scene *asks* for and an
     * inspector can change it; without the built value there is nothing to compare against, and an
     * edit would leave the component saying `STATIC` while a dynamic body kept falling. Poses are
     * retained rather than read fresh because [PhysicsWorld.forEachBodyTransform] reports only what a
     * backend has awake, so a settled body would otherwise have no pose at all.
     *
     * Found per step by entity slot and by handle id, never by hashing a boxed handle (#1428).
     */
    private val bodies = PhysicsBodyRecords()

    // Resolved once per world generation, so the per-body readback does not find `Transform` by class.
    private var transformWorld: World? = null
    private var transformGeneration = -1
    private var transformTypeId = ComponentTypeId(0)
    private var steppingWorld: World? = null

    // Built once, so a step allocates no visitor.
    private val readBack: (BodyHandle, Vec3f, Quat) -> Unit = { handle, position, rotation ->
        val record = bodies.forHandle(handle)
        if (record != null) {
            val transform = steppingWorld?.get<Transform>(record.entity, transformTypeId)
            if (transform != null) {
                transform.position.x = position.x
                transform.position.y = position.y
                transform.position.z = position.z
                // The simulation's quaternion becomes the Euler angles `Transform` stores. This is
                // the one place in the engine that conversion happens, and it writes in place
                // because it runs per body per frame.
                rotation.toEuler(transform.rotation)
            }
            val pose = record.pose
            if (pose == null) record.pose = BodyPose(position, rotation) else pose.record(position, rotation)
        }
    }

    private val stepContacts = ArrayList<PhysicsContact>()
    private var drainingWorld: World? = null

    // Built once, so a step with no contacts allocates nothing.
    private val collectContact: (ContactEvent) -> Unit = { event ->
        stepContacts += PhysicsContact(
            a = event.a,
            b = event.b,
            entityA = liveEntity(event.a),
            entityB = liveEntity(event.b),
            phase = event.phase,
        )
    }

    /**
     * Every contact the last [update]'s step reported, in the order the backend reported them.
     *
     * Replaced on each [update], so a system registered after this one in the fixed phase reads
     * the step that just ran. Reading does not consume: every reader sees the same list. Events are
     * per touching sub-shape pair, as [PhysicsWorld.drainContacts] describes; deduplicate by pair if
     * that matters.
     */
    val contacts: List<PhysicsContact> get() = stepContacts

    /**
     * The entity this system built [handle] for, or `null` for a body it did not build.
     *
     * May name an entity destroyed since; check `World.isAlive` before acting on it. [contacts]
     * already resolves to live entities only.
     */
    fun entityFor(handle: BodyHandle): Entity? = bodies.forHandle(handle)?.entity

    /**
     * Destroys [entity]'s body, if this system built one, and forgets it.
     *
     * Call before `World.destroy`: destroying the entity first drops the only reference to the
     * handle and leaves a body that is invisible, still solid and unreachable.
     */
    fun destroyBody(world: World, entity: Entity) {
        val body = world.get<PhysicsBody>(entity) ?: return
        val handle = body.handle ?: return
        physicsWorld.destroyBody(handle)
        bodies.remove(handle)
        body.handle = null
    }

    private fun liveEntity(handle: BodyHandle): Entity? =
        bodies.forHandle(handle)?.entity?.takeIf { drainingWorld?.isAlive(it) == true }

    private fun resolveTransformType(world: World) {
        if (transformWorld === world && transformGeneration == world.generation) return
        transformTypeId = world.typeId(Transform::class)
        transformWorld = world
        transformGeneration = world.generation
    }

    override fun update(world: World, delta: Float) {
        resolveTransformType(world)
        val family = world.family<Transform, PhysicsBody>()
        family.forEach { entity, transform, physicsBody ->
            val live = physicsBody.handle
            // A rebuild rather than a mutation: the backend port has no setMotionType, and adding
            // one to four backends to serve an editor field is the larger change.
            if (live != null && bodies.built(entity, live)?.motionType != physicsBody.motionType) {
                physicsWorld.destroyBody(live)
                bodies.remove(live)
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
                    physicsBody.degreesOfFreedom,
                )
                physicsBody.handle = handle
                bodies.add(entity, handle, physicsBody.motionType)
            }
        }

        physicsWorld.step(delta)

        // The visitor form, not the list one: this runs every fixed step, and the list allocates
        // a vector and a quaternion per body to hand back values that are copied out immediately.
        steppingWorld = world
        physicsWorld.forEachBodyTransform(readBack)
        steppingWorld = null

        stepContacts.clear()
        drainingWorld = world
        physicsWorld.drainContacts(collectContact)
        drainingWorld = null
    }

    /**
     * Blends each body's last two simulated poses into its [Transform].
     *
     * Called once per rendered frame rather than per step, and it overwrites what [update] wrote --
     * [update] leaves the newest pose there so a caller that never interpolates behaves exactly as
     * it did before this existed.
     */
    override fun interpolate(world: World, alpha: Float) {
        resolveTransformType(world)
        bodies.forEach { record ->
            val pose = record.pose ?: return@forEach
            val transform = world.get<Transform>(record.entity, transformTypeId) ?: return@forEach
            pose.blendInto(transform, alpha)
        }
    }
}
