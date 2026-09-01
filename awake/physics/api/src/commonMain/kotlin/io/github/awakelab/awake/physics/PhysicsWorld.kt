/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics

import io.github.awakelab.awake.core.math.Quat
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
// Deliberately one wide facade rather than several narrow ones: D5 chose a coarse binding, and
// every backend implements the whole surface in a single class, so splitting it by concern would
// add interfaces without removing a line of implementation. Contact events and collision layers
// have since landed and the count has doubled, so bodies, queries and events are now three
// genuinely separate things -- but splitting them still moves method declarations between files
// without changing what any backend contains, so it stays one facade until a backend implements
// only part of it.
@Suppress("TooManyFunctions")
interface PhysicsWorld {
    /** The layers this world was built with, so a caller can name what it wants to hit. */
    val layers: CollisionLayers

    /**
     * Adds a body to the simulation.
     *
     * `layer` decides what it collides with and what queries can see it. It defaults to the
     * two-layer scheme in [CollisionLayers.Default] -- static bodies are world, everything else
     * moves -- so a caller that never mentions layers keeps the behaviour it had before they
     * existed.
     *
     * A `sensor` detects what passes through it instead of blocking it: nothing bounces off it and
     * nothing rests on it, and what overlaps it is reported through [drainContacts]. That is what a
     * pickup, a checkpoint or a damage volume is. It is fixed at creation because nothing wants a
     * trigger that is solid for part of its life.
     *
     * **Only sensors report contacts.** Jolt calls its contact listener for every touching pair in
     * the scene, on every step, from its worker threads; forwarding all of that would allocate an
     * event per contact per frame for events nothing reads. Restricting it to bodies that asked
     * keeps the cost proportional to what a game listens for.
     *
     * Two Jolt rules leak through and cannot be papered over here. **A sensor only detects active
     * bodies**, so something that has fallen asleep before entering one is never seen. And **a
     * static sensor does not detect static bodies** -- a trigger that must notice level geometry
     * has to be kinematic. A [MeshShape] or [HeightFieldShape] cannot be a sensor at all: both are
     * surfaces with no inside, so there is nothing to be inside them.
     */
    fun createBody(
        shape: PhysicsShape,
        position: Vec3f,
        rotation: Quat,
        motionType: MotionType,
        layer: CollisionLayer = defaultLayerFor(motionType),
        sensor: Boolean = false,
    ): BodyHandle

    /**
     * Removes a body from the simulation and frees it.
     *
     * The handle is dead afterwards and must not be used again. Nothing else destroys a body --
     * removing the entity that happened to hold its handle leaves it simulating, invisible and
     * unreachable -- so whoever created one owns taking it back.
     */
    fun destroyBody(handle: BodyHandle)

    /**
     * Advances the simulation by [deltaTime] seconds.
     *
     * Give it a fixed step. Physics integrates, so a variable delta makes the same scene behave
     * differently on different machines, and a large one lets fast bodies pass through thin
     * geometry. `FixedTimestepLoop` and the scene DSL's `fixedSystem(...)` exist to supply one.
     */
    fun step(deltaTime: Float)

    /**
     * Replaces this body's linear velocity outright.
     *
     * The blunt one, and usually the wrong one for anything with mass: it ignores what the body
     * was already doing and how heavy it is. Right for setting a projectile's speed or stopping a
     * body dead; [addImpulse] is what a collision or an explosion wants.
     */
    fun setLinearVelocity(handle: BodyHandle, velocity: Vec3f)

    /**
     * This body's linear velocity in world units per second.
     *
     * A character standing on a moving platform reads the platform's velocity through this and
     * adds it to its own, which is the difference between riding a lift and sliding off it.
     */
    fun getLinearVelocity(handle: BodyHandle): Vec3f

    /**
     * Replaces this body's angular velocity outright, in radians per second about each world axis.
     *
     * The rotational twin of [setLinearVelocity] and blunt in the same way: it ignores what the
     * body was already spinning at and how its mass is distributed. Enough for setting a thrown
     * object tumbling or stopping a spin dead.
     */
    fun setAngularVelocity(handle: BodyHandle, velocity: Vec3f)

    /**
     * Applies an impulse at the body's centre of mass, in newton-seconds.
     *
     * Mass-aware, unlike [setLinearVelocity]: the same impulse moves a crate further than it
     * moves a boulder, which is what makes a character shoving a body look like weight rather
     * than like a teleport.
     */
    fun addImpulse(handle: BodyHandle, impulse: Vec3f)

    /**
     * Drives a [MotionType.KINEMATIC] body toward a pose over [deltaTime], for lifts, doors and
     * anything else that pushes the world around without being pushed back.
     *
     * Not the same as writing a position: Jolt derives the velocity needed to arrive, so contacts
     * are solved with the body genuinely moving. Teleporting a kinematic body instead leaves
     * anything standing on it behind and lets fast movement pass through thin geometry.
     */
    fun moveKinematic(handle: BodyHandle, position: Vec3f, rotation: Quat, deltaTime: Float)

    /**
     * Visits each body whose pose may have changed since the last [step].
     *
     * **[position] and [rotation] are scratch, reused between calls.** Read what you need inside
     * the callback; keeping either reference hands you a value that the next body overwrites.
     * That is the point of the shape: a per-frame readback that allocates a vector and a
     * quaternion per body produces garbage at exactly the rate the game is played.
     *
     * "May have changed" rather than "has changed": a backend that can enumerate only its awake
     * bodies does, and one that cannot visits everything it tracks. Both are correct, because a
     * sleeping body simply reports the pose it already had -- so a caller must not treat being
     * visited as evidence of movement.
     *
     * The converse matters more: **a body may never be visited at all.** A static body is never
     * awake, and a settled one stops being awake, so neither is reported on a backend that filters
     * to the active set. Nothing here is the way to read one specific body's pose -- and after
     * [shiftOrigin] moves a static body, the only way to observe it is to query the world.
     *
     * One call per frame, never one per body: physics steps once regardless of body count, and
     * this mirrors that instead of scaling JNI or cinterop crossings with the population.
     */
    fun forEachBodyTransform(action: (handle: BodyHandle, position: Vec3f, rotation: Quat) -> Unit)

    /**
     * Moves every body by [offset], so the simulation follows a scene that has been rebased.
     *
     * `FloatingOriginSystem` translates the world under the observer to keep float precision
     * usable past a few kilometres. It moves `Transform`s, and physics is authoritative for
     * anything with a body -- `PhysicsSystem` writes the simulation's answer back into the
     * transform every frame -- so without this, a shifted body snaps back on the next step and a
     * scene mixing physics with a shifting origin is simply broken.
     *
     * Not a per-body setter: an origin shift moves everything at once, and the difference matters
     * to a backend that can do this in bulk. A caller repositioning ONE body wants a different
     * method, and neither this nor any other method here is that.
     */
    fun shiftOrigin(offset: Vec3f)

    /**
     * The first body along a ray, or `null`.
     *
     * Sensors are never reported, for the same reason they are not reported by [shapeCast]: a ray
     * asks what it is stopped by, and a trigger volume stops nothing.
     *
     * `onlyLayer` restricts the ray to a single layer when set: a camera that must stop at walls
     * but ignore the crates rolling around the player is the reason it exists. The same
     * one-layer-not-a-mask limit applies as in [shapeCast].
     */
    fun raycast(
        origin: Vec3f,
        direction: Vec3f,
        maxDistance: Float,
        onlyLayer: CollisionLayer? = null,
    ): RaycastHit?

    /**
     * Sweeps [shape] from [from] to [to] and returns the first thing it hits, or `null`.
     *
     * The one query a kinematic character controller needs: sweep, take the earliest hit, slide
     * the remainder along [ShapeCastHit.normal], repeat. Camera collision and spawn-validity
     * checks are the same call.
     *
     * Only convex shapes can be swept -- [HeightFieldShape] is terrain to be cast *against*, not
     * with, and passing one throws [PhysicsCapabilityException] rather than failing somewhere
     * inside a backend.
     *
     * There is deliberately no rotation parameter: every caller so far sweeps a capsule or a
     * sphere, both symmetric about the sweep, so one would be a parameter every call site passes
     * an identity to. A box sweep can add it.
     *
     * **A sensor is never reported by a cast.** It is not solid, so it is not an answer to "what
     * would block me" -- and reporting one would turn every trigger volume into an invisible wall
     * the player walks into. [overlapShape] is the query that does see them, which is the whole
     * division: casts ask what stops you, overlaps ask what you are inside.
     *
     * [ignore] skips one body outright, and exists because a character cannot sweep without it: a
     * controller that carries a body of its own at its own position hits that body first, every
     * time, and never moves. Skipping happens inside the query, so the hit reported is the nearest
     * *other* body rather than nothing -- which is the whole difference from filtering the result
     * afterwards, where the ignored body hides everything behind it.
     *
     * There is no matching parameter on [raycast]. Nothing casts a ray from inside its own body
     * today; the character sweeps. Add it when something does.
     *
     * [onlyLayer] restricts the sweep to a single layer rather than to a set of them. That is what
     * every backend can actually do: jolt-jni exposes only `SpecifiedObjectLayerFilter`, whose
     * matching filter in JoltPhysics.js takes one layer too, and jolt-jni's mask-capable
     * `DefaultObjectLayerFilter` has a package-private constructor. A caller that needs "anything
     * except my own layer" still has no way to say so -- see docs/plans/physics-open-world.md.
     */
    fun shapeCast(
        shape: PhysicsShape,
        from: Vec3f,
        to: Vec3f,
        onlyLayer: CollisionLayer? = null,
        ignore: BodyHandle? = null,
    ): ShapeCastHit?

    /**
     * Visits every body overlapping [shape] placed at [position], in no particular order.
     *
     * The query for "what is in here *right now*", which no cast can answer: a sweep reports where
     * something would first be blocked, so it finds one body and stops. Spawn validity, an
     * explosion's victims and a melee arc's targets are all this call.
     *
     * A visitor rather than a list, for the reason [forEachBodyTransform] is: an explosion that
     * allocates a list per blast allocates at the rate the game is played. Nothing is passed but
     * the handle -- a caller that wants a body's pose already has a `Transform`, and the contact
     * point of an overlap is not well defined for a shape fully inside another.
     *
     * Only convex shapes can be used to overlap with, the same restriction [shapeCast] carries and
     * for the same reason: a [MeshShape] or [HeightFieldShape] is a surface, not a volume, so
     * "inside it" has no meaning. Both throw [PhysicsCapabilityException].
     *
     * [onlyLayer] restricts the query to one layer, with the same not-a-mask limit as [shapeCast].
     */
    fun overlapShape(
        shape: PhysicsShape,
        position: Vec3f,
        onlyLayer: CollisionLayer? = null,
        onOverlap: (BodyHandle) -> Unit,
    )

    /**
     * Wakes a body or puts it to sleep.
     *
     * Jolt already sleeps a body that has settled; this is the control over *when*, and it exists
     * for scale. A world holding five thousand crates is not simulating five thousand crates unless
     * something insists, and deactivating the ones nobody is near is how a caller insists otherwise.
     *
     * **Deactivating is not disabling.** A sleeping body still collides, and anything that touches
     * it wakes it — this makes it stop *integrating*, not stop existing. Nor does it hold position
     * against gravity permanently: the first contact or explicit wake resumes the fall.
     *
     * Two consequences follow from the rest of this contract, and both bite. A sleeping body is not
     * visited by [forEachBodyTransform], so a caller that reads poses only from there sees it
     * freeze, which is correct and looks like a bug. And **a sensor only detects active bodies**,
     * so deactivating something inside a trigger volume makes the trigger forget it is there.
     *
     * Static bodies are never active and ignore this.
     */
    fun setActive(handle: BodyHandle, active: Boolean)

    /**
     * Whether the simulation is currently integrating this body.
     *
     * The observable half of [setActive], and the only honest way to check that a body settled
     * rather than merely stopped being reported. Always false for a static body.
     */
    fun isActive(handle: BodyHandle): Boolean

    /**
     * Ties two bodies together and starts enforcing it.
     *
     * Anchors are world-space and read once, here: whatever arrangement the bodies are in at this
     * moment is the one the constraint treats as its rest pose.
     *
     * **A constraint dies with either body it joins.** [destroyBody] removes the constraints that
     * reference it first, because Jolt does not: a constraint left pointing at a freed body is a
     * crash on the next step, not an error. So a handle from here is dead once either of its bodies
     * is, and holding one past that is the mistake this contract exists to name.
     */
    fun createConstraint(constraint: Constraint): ConstraintHandle

    /**
     * Removes a constraint and frees it; the bodies it joined are otherwise untouched.
     *
     * Removing one that is already gone -- because a body it joined was destroyed -- does nothing,
     * which is what lets a caller tear down in whatever order it finds convenient.
     */
    fun destroyConstraint(handle: ConstraintHandle)

    /**
     * Pushes a body up as if it were in a fluid whose surface is the horizontal plane at [surfaceY].
     *
     * **Called every step, for every body currently in the fluid** -- this is one step's worth of
     * push, not a state a body is put into. Physics has no idea where the water is; a sensor
     * covering the volume does, and `drainContacts` or `overlapShape` is how a caller finds who is
     * in it. That split is deliberate: a water *volume* is a game's concept, and a backend that
     * owned one would have to own its shape, its currents and its edges too.
     *
     * A horizontal surface at a height, rather than an arbitrary plane. Every water body in a game
     * is level; a caller that genuinely needs a tilted one wants a different method rather than a
     * normal nobody else would pass.
     *
     * Does nothing to a body that is not [MotionType.DYNAMIC] -- there is nothing to push -- and
     * nothing to one entirely above the surface, so a caller may pass a body that has just left the
     * water without checking first.
     */
    fun applyBuoyancy(
        handle: BodyHandle,
        surfaceY: Float,
        buoyancy: Buoyancy,
        deltaTime: Float,
    )

    /**
     * Makes a body sweep its own motion between steps instead of jumping.
     *
     * A step moves a body by velocity times delta and then looks for contacts *where it landed*.
     * At 400 units per second that is over six metres per frame, so a bullet crosses a wall
     * without ever having been inside it, and the wall reports nothing. Turning this on makes Jolt
     * cast the body's shape along that displacement and stop it at the first thing in the way.
     *
     * Off by default because it is not free: it is a shape cast per step per body, and it is
     * pointless for anything that never moves far in one step. Turn it on for projectiles and for
     * a character in a long fall; leave it off for crates.
     *
     * **It protects this body, not the ones it hits.** A fast body with this on will not pass
     * through a slow one; a fast body *without* it still passes through this one, however this one
     * is configured. Whichever thing moves fast is the one that needs it.
     *
     * Ignored for [MotionType.STATIC] bodies, which never move and so can never tunnel.
     */
    fun setContinuousCollision(handle: BodyHandle, enabled: Boolean)

    /**
     * Hands over the contacts that have happened since the last call, and forgets them.
     *
     * Call it once per [step], after stepping. Contacts accumulate between calls, so a caller that
     * never drains grows a buffer forever -- and one that drains before stepping reads the previous
     * frame's events.
     *
     * Events are queued during [step] from Jolt's worker threads and replayed here on the caller's
     * thread, which is what makes acting on one safe: [destroyBody] or [createBody] from inside
     * this callback is an ordinary call, not a re-entrant one into a running simulation.
     *
     * One event per touching *sub-shape* pair, not per body pair. Two convex shapes touch once, so
     * a sensor and a crate produce one [ContactPhase.BEGAN] -- but a body whose shape has several
     * parts in contact reports each of them, and a mesh or heightfield entering a sensor can report
     * many. Deduplicate by pair if that matters.
     */
    /**
     * Starts or stops reporting this body's contacts through [drainContacts].
     *
     * A sensor reports from the moment it is created and needs no call here; this is for a solid
     * body, which does not. That is the difference between the two: a sensor exists to be passed
     * through and noticed, while a crate is noticed only if somebody asked -- hit sounds and impact
     * damage being the reasons to ask.
     *
     * **Opt-in per body, deliberately.** Jolt offers every touching pair in the scene on every
     * step, so reporting them all would queue an event per pair per frame for a settled pile of
     * crates that nothing reads. What a game listens for is a handful of bodies, and this keeps the
     * cost proportional to that rather than to how many bodies are touching.
     *
     * Reporting stops when the body is destroyed, and does not transfer: Jolt reuses body ids, so
     * an id left reporting would make the next body handed that id report contacts nobody asked
     * for.
     */
    fun setContactReporting(handle: BodyHandle, enabled: Boolean)

    fun drainContacts(action: (ContactEvent) -> Unit)

    /**
     * Releases the world and everything in it.
     *
     * Native allocations outlive garbage collection on every backend, so a world that is dropped
     * without this leaks until the process ends. Handles from this world are dead afterwards.
     */
    fun destroy()
}

/**
 * Which layer a body lands in when its creator does not say.
 *
 * Static bodies are world and everything else moves, which is the split every backend already
 * hardcoded -- so a caller that never mentions layers gets exactly the behaviour it had before
 * they existed.
 */
fun defaultLayerFor(motionType: MotionType): CollisionLayer =
    if (motionType == MotionType.STATIC) CollisionLayers.World else CollisionLayers.Moving

/**
 * Every visited body's pose as a list, copied out of the scratch values.
 *
 * For tests and tools, and deliberately not for a frame loop: it allocates the list plus a vector
 * and a quaternion per body, which is what [PhysicsWorld.forEachBodyTransform] exists to avoid.
 */
fun PhysicsWorld.syncTransforms(): List<BodyTransform> = buildList {
    forEachBodyTransform { handle, position, rotation ->
        add(
            BodyTransform(
                handle = handle,
                position = Vec3f(position.x, position.y, position.z),
                rotation = Quat(rotation.x, rotation.y, rotation.z, rotation.w),
            ),
        )
    }
}
