/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.host.MemoryPressure
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.ComponentStore
import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family2
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BodyTransformVisitor
import com.awakekt.awake.physics.ContactEvent
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.abs

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
    private val poseResolver = PhysicsWorldPoseResolver()
    private val scratchWorldPosition = Vec3f()
    private val scratchWorldRotation = Quat()

    // Resolved once per world generation, so per-step body sync, readback and interpolation touch
    // neither KClass nor ComponentRegistry.
    private var cachedWorld: World? = null
    private var cachedGeneration = -1
    private var transformTypeId = ComponentTypeId(0)
    private var transformStore: ComponentStore<Transform>? = null
    private var bodyFamily: Family2<Transform, PhysicsBody>? = null
    private var steppingTransformStore: ComponentStore<Transform>? = null

    // Steady-state memoization for body creation / motionType rebuilds: when the family membership
    // and in-place motionType/handle counters have not moved, every entity in the family already has
    // its live handle, avoiding an O(N) scan over tens of thousands of static bodies every fixed step.
    private var syncedWorld: World? = null
    private var syncedGeneration = -1
    private var syncedFamilyChanges = -1
    private var syncedFamilySize = 0
    private var syncedMotionEdits = -1
    private var syncedHandleEdits = -1
    private var detachedEntities = LongArray(INITIAL_DETACHED_CAPACITY)
    private var detachedCount = 0

    // Diagnostic counters for incremental-sync verification in tests.
    internal var lastBodySyncScannedCount = 0
        private set

    // Built once, so a step allocates no visitor; a BodyTransformVisitor, so no handle is boxed per body (#1428).
    private val readBack = BodyTransformVisitor { handleId, position, rotation ->
        val record = bodies.forHandle(BodyHandle(handleId))
        if (record != null) {
            val transform = steppingTransformStore?.get(record.entity)
            val kinematic = record.kinematic
            if (kinematic != null) {
                kinematic.lastPosition.set(position)
                kinematic.lastRotation.set(rotation)
                // A parented kinematic collider's Transform is in local space under the parent that
                // drives it; writing world coordinates back into it would double-translate it.
                if (transform?.parent != null) return@BodyTransformVisitor
            }
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
            if (pose == null) {
                record.pose = BodyPose(position, rotation)
                bodies.markPosed(record)
            } else {
                pose.record(position, rotation)
            }
        }
    }

    private val stepContacts = ArrayList<PhysicsContact>()
    private var drainingWorld: World? = null
    private val memoryPressure = MemoryPressure.watch()

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
        body.assignHandleSilently(null)
        if (detachedCount == detachedEntities.size) {
            detachedEntities = detachedEntities.copyOf(detachedEntities.size * 2)
        }
        detachedEntities[detachedCount++] = entity.packed
    }

    private fun liveEntity(handle: BodyHandle): Entity? =
        bodies.forHandle(handle)?.entity?.takeIf { drainingWorld?.isAlive(it) == true }

    private fun ensureWorldCache(world: World): Family2<Transform, PhysicsBody> {
        val cached = bodyFamily
        if (cachedWorld === world && cachedGeneration == world.generation && cached != null) {
            if (transformStore == null) transformStore = world.componentStore(transformTypeId)
            return cached
        }
        transformTypeId = world.typeId(TRANSFORM_CLASS)
        transformStore = world.componentStore(transformTypeId)
        val family = world.family<Transform, PhysicsBody>()
        cachedWorld = world
        cachedGeneration = world.generation
        bodyFamily = family
        return family
    }

    override fun update(world: World, delta: Float) {
        if (memoryPressure.check()) physicsWorld.trimMemory()
        val family = ensureWorldCache(world)
        val store = transformStore
        lastBodySyncScannedCount = 0
        syncPendingBodies(world, family, store)

        if (bodies.kinematicCount > 0 && delta > 0f) {
            syncKinematicBodies(store, delta)
        }

        physicsWorld.step(delta)

        // The visitor form, not the list one: this runs every fixed step, and the list allocates
        // a vector and a quaternion per body to hand back values that are copied out immediately.
        steppingTransformStore = store
        physicsWorld.visitBodyTransforms(readBack)
        steppingTransformStore = null

        stepContacts.clear()
        drainingWorld = world
        physicsWorld.drainContacts(collectContact)
        drainingWorld = null
    }

    private fun syncPendingBodies(
        world: World,
        family: Family2<Transform, PhysicsBody>,
        store: ComponentStore<Transform>?,
    ) {
        val worldChanged = syncedWorld !== world || syncedGeneration != world.generation
        val editsChanged = syncedMotionEdits != physicsBodyMotionTypeEdits || syncedHandleEdits != physicsBodyHandleEdits
        val familyChanges = family.changes
        if (worldChanged || editsChanged || syncedFamilyChanges != familyChanges) {
            syncFamilyBodies(family, store, worldChanged || editsChanged, familyChanges)
            syncedWorld = world
            syncedGeneration = world.generation
            syncedFamilyChanges = family.changes
            syncedFamilySize = family.size
            syncedMotionEdits = physicsBodyMotionTypeEdits
            syncedHandleEdits = physicsBodyHandleEdits
        }
        if (detachedCount > 0) {
            if (!worldChanged) syncDetachedBodies(world, store)
            detachedCount = 0
        }
    }

    private fun syncFamilyBodies(
        family: Family2<Transform, PhysicsBody>,
        store: ComponentStore<Transform>?,
        forceFullScan: Boolean,
        familyChanges: Int,
    ) {
        val deltaChanges = familyChanges - syncedFamilyChanges
        val deltaSize = family.size - syncedFamilySize
        val pureRemoval = !forceFullScan && deltaSize < 0 && deltaChanges == -deltaSize
        val pureAppend = !forceFullScan && deltaSize > 0 && deltaChanges == deltaSize
        when {
            pureRemoval -> Unit
            pureAppend -> {
                for (index in syncedFamilySize until family.size) {
                    lastBodySyncScannedCount++
                    syncEntityBody(store, family.entityAt(index), family.componentA(index), family.componentB(index))
                }
            }
            else -> {
                family.forEach { entity, transform, physicsBody ->
                    lastBodySyncScannedCount++
                    syncEntityBody(store, entity, transform, physicsBody)
                }
            }
        }
    }

    private fun syncDetachedBodies(world: World, store: ComponentStore<Transform>?) {
        for (index in 0 until detachedCount) {
            val entity = Entity(detachedEntities[index])
            if (world.isAlive(entity)) {
                val transform = store?.get(entity)
                val physicsBody = world.get<PhysicsBody>(entity)
                if (transform != null && physicsBody != null) {
                    lastBodySyncScannedCount++
                    syncEntityBody(store, entity, transform, physicsBody)
                }
            }
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
        if (bodies.posedCount == 0) return
        ensureWorldCache(world)
        val store = transformStore ?: return
        bodies.forEachPosed { record ->
            val pose = record.pose ?: return@forEachPosed
            val transform = store.get(record.entity) ?: return@forEachPosed
            pose.blendInto(transform, alpha)
        }
    }

    /**
     * Drives kinematic bodies whose ECS world pose changed since the last step (e.g. moved by
     * `KeyframeAnimationSystem` or a parent transform) via [PhysicsWorld.moveKinematic], so Jolt
     * derives true velocity and carries standing characters/dynamic bodies cleanly. Callers that
     * drive [PhysicsWorld.moveKinematic] directly without editing `Transform` first see no
     * mismatch and are left untouched.
     */
    private fun syncKinematicBodies(store: ComponentStore<Transform>?, delta: Float) {
        if (store == null) return
        bodies.forEachKinematic { record ->
            val kinematic = record.kinematic ?: return@forEachKinematic
            val transform = store.get(record.entity) ?: return@forEachKinematic
            poseResolver.resolve(store, transform, scratchWorldPosition, scratchWorldRotation)
            val moved = !samePose(
                scratchWorldPosition,
                scratchWorldRotation,
                kinematic.lastPosition,
                kinematic.lastRotation,
            )
            if (moved || kinematic.wasMoving) {
                physicsWorld.moveKinematic(record.handle, scratchWorldPosition, scratchWorldRotation, delta)
                kinematic.lastPosition.set(scratchWorldPosition)
                kinematic.lastRotation.set(scratchWorldRotation)
                kinematic.wasMoving = moved
            }
        }
    }

    private fun syncEntityBody(
        store: ComponentStore<Transform>?,
        entity: Entity,
        transform: Transform,
        physicsBody: PhysicsBody,
    ) {
        val live = physicsBody.handle
        // A rebuild rather than a mutation: the backend port has no setMotionType, and adding
        // one to four backends to serve an editor field is the larger change.
        if (live != null && bodies.built(entity, live)?.motionType != physicsBody.motionType) {
            physicsWorld.destroyBody(live)
            bodies.remove(live)
            physicsBody.assignHandleSilently(null)
        }
        if (physicsBody.handle == null) {
            poseResolver.resolve(store, transform, scratchWorldPosition, scratchWorldRotation)
            val handle = physicsWorld.createBody(
                physicsBody.shape,
                scratchWorldPosition,
                scratchWorldRotation,
                physicsBody.motionType,
                physicsBody.layer,
                physicsBody.sensor,
                physicsBody.degreesOfFreedom,
            )
            physicsBody.assignHandleSilently(handle)
            val record = bodies.add(entity, handle, physicsBody.motionType)
            if (physicsBody.motionType == MotionType.KINEMATIC) {
                record.kinematic = KinematicSyncState(scratchWorldPosition, scratchWorldRotation)
            }
        }
    }
}

private fun samePose(posA: Vec3f, rotA: Quat, posB: Vec3f, rotB: Quat): Boolean {
    val dx = posA.x - posB.x
    val dy = posA.y - posB.y
    val dz = posA.z - posB.z
    if (dx * dx + dy * dy + dz * dz > POSITION_EPSILON_SQ) return false
    val dot = abs(rotA.x * rotB.x + rotA.y * rotB.y + rotA.z * rotB.z + rotA.w * rotB.w)
    return dot >= 1f - ROTATION_DOT_EPSILON
}

private val TRANSFORM_CLASS = Transform::class
private const val INITIAL_DETACHED_CAPACITY = 16
private const val POSITION_EPSILON_SQ = 1e-10f
private const val ROTATION_DOT_EPSILON = 1e-5f
