/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.Buoyancy
import com.awakekt.awake.physics.ContactPhase
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.showcase.terrain.TerrainExampleAsset

/** Half a unit each way, matching the `cube` mesh the boxes are drawn with -- a collider that
 * disagrees with its mesh makes physics look broken when it is working perfectly. */
private const val BOX_HALF_EXTENT = 0.5f

/**
 * The volume a box has to reach to be collected, and the one collider here that deliberately does
 * *not* match its mesh.
 *
 * The `goal-zone` node is drawn as a narrow post, and the trigger is the space around it. A post
 * rather than a pad because the terrain is a dome: anything flat and wide enough to push a box onto
 * would have one edge buried in the hill and the other floating above it, while a narrow one is
 * planted at whatever height the ground happens to be. The volume is what a player aims at; the
 * post is only how they see where.
 */
private val GOAL_ZONE_HALF_EXTENTS = Vec3f(1.25f, 1f, 1.25f)

/**
 * The pool's collision volume, matching the `water` node's own extent.
 *
 * Unlike the goal post this one *does* match its mesh: the volume a body is inside is exactly the
 * water you can see, and a mismatch would make things float above the surface or sink through it.
 */
private val WATER_HALF_EXTENTS = Vec3f(2f, 1f, 2f)

/**
 * Where the surface is, in world space.
 *
 * `applyBuoyancy` takes a horizontal plane rather than deriving one from the volume, so this has to
 * agree with the node's own top face -- centre plus half height. A surface below the real one sinks
 * everything; above it, things bob out of the water.
 */
private const val WATER_SURFACE_Y = 0.6f + 1f

/**
 * The showcase's physics world, and the only one.
 *
 * A mutable holder rather than a constructor argument because of an ordering problem with no
 * tidier answer: scene systems are built when the scene initializes, the world can only be built
 * in `onReady`, and on wasmJs building it suspends on an Emscripten bootstrap. So the system is
 * registered first and finds its world later.
 */
internal object ShowcasePhysics {
    var world: PhysicsWorld? = null

    /**
     * Runs [PhysicsSystem] once [world] exists, and does nothing before that.
     *
     * Registered with `fixedSystem`, never `frameSystem`: that is the whole difference between a
     * simulation that behaves the same at 30 and 144 fps and one that does not. `FixedTimestepLoop`
     * already guarantees the constant delta -- the only way to get this wrong is to register it in
     * the wrong phase.
     */
    fun system(): InterpolatedSystem = object : InterpolatedSystem {
        private var delegate: PhysicsSystem? = null

        override fun update(world: World, delta: Float) {
            val physicsWorld = ShowcasePhysics.world ?: return
            val system = delegate ?: PhysicsSystem(physicsWorld).also { delegate = it }
            system.update(world, delta)
        }

        /**
         * Forwarded, and the reason this wrapper declares [InterpolatedSystem] at all.
         *
         * `SceneSchedule` decides who gets interpolated by testing the registered system, which is
         * this wrapper -- not the [PhysicsSystem] behind it. Declaring only `System` here compiles,
         * runs, and silently draws every body at whatever the last fixed step wrote.
         */
        override fun interpolate(world: World, alpha: Float) {
            delegate?.interpolate(world, alpha)
        }
    }
}

/**
 * Drops four boxes onto the heightfield.
 *
 * The terrain's collision shape and its mesh come from the same samples in
 * [TerrainExampleAsset], so what the boxes land on is what you can see. The boxes themselves are
 * authored in the scene document -- transform and mesh are things a document can express;
 * [PhysicsBody] is not, which is the same "author a named node, attach the rest on activation"
 * split [InstancedCubesExampleDriver] uses.
 */
internal object TerrainPhysicsExampleDriver {

    /** The trigger's entity; its body handle only exists once [PhysicsSystem] has built it. */
    private var goalZone: Entity? = null

    /** The pool's entity, and the bodies currently inside it. */
    private var water: Entity? = null

    /**
     * What is in the water right now.
     *
     * Tracked from contact events rather than tested per frame: `applyBuoyancy` needs to run every
     * step for every body in the fluid, and asking the world "who is in this box" every step would
     * be an overlap query per frame to answer something the sensor already reported once.
     */
    private val submerged = mutableSetOf<BodyHandle>()

    /** How many boxes have been pushed into the zone, for anything that wants to say so. */
    var collected = 0
        private set

    /**
     * Whether the player has stood in the goal zone.
     *
     * The verb the trigger was built for and could not do until the character carried a body: a
     * controller moves by sweeping shapes, so physics had nothing to report contacts about.
     */
    var reachedByPlayer = false
        private set

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        if (ShowcasePhysics.world == null) return
        val world = runtime.world

        instance.roots.find { it.name == "goal-zone" }?.let { attachGoalZone(world, it.entity) }
        instance.roots.find { it.name == "water" }?.let { pool ->
            water = pool.entity
            submerged.clear()
            world.add(
                pool.entity,
                PhysicsBody(
                    shape = BoxShape(WATER_HALF_EXTENTS),
                    motionType = MotionType.STATIC,
                    sensor = true,
                ),
            )
        }
        instance.roots.find { it.name == "floating-box" }?.let { box ->
            world.add(
                box.entity,
                PhysicsBody(
                    BoxShape(Vec3f(BOX_HALF_EXTENT, BOX_HALF_EXTENT, BOX_HALF_EXTENT)),
                    MotionType.DYNAMIC,
                ),
            )
        }

        instance.roots.find { it.name == "heightfield-terrain" }?.let { terrain ->
            world.add(
                terrain.entity,
                PhysicsBody(TerrainExampleAsset.collisionShape, MotionType.STATIC),
            )
        }
        instance.roots.filter { it.name?.startsWith("falling-box-") == true }.forEach { box ->
            world.add(
                box.entity,
                PhysicsBody(
                    BoxShape(Vec3f(BOX_HALF_EXTENT, BOX_HALF_EXTENT, BOX_HALF_EXTENT)),
                    MotionType.DYNAMIC,
                ),
            )
        }
    }

    /**
     * Destroys the Jolt bodies before the scene that owned them closes.
     *
     * The entities go with the scene, but the bodies do not: they live in a `PhysicsWorld` that
     * outlives every showcase switch. Without this, switching away and back leaves the previous
     * run's boxes simulating invisibly, and the terrain collider accumulates a copy per visit.
     */
    fun detach(runtime: SceneAppLifecycleRuntime) {
        goalZone = null
        water = null
        submerged.clear()
        val physicsWorld = ShowcasePhysics.world ?: return
        runtime.world.queryEach<PhysicsBody> { _, body ->
            body.handle?.let(physicsWorld::destroyBody)
            body.handle = null
        }
    }

    /**
     * Makes an authored node the trigger volume boxes are pushed into.
     *
     * Static, because the pad does not move; a sensor, because it has to notice a box rather than
     * stop one. Separate from [attach] so a test can build the pair without a loaded scene.
     */
    internal fun attachGoalZone(world: World, zone: Entity) {
        goalZone = zone
        // The count belongs to the zone, not to the app: a revisit to this showcase gets a fresh
        // set of boxes, so it has to get a fresh score with them.
        collected = 0
        reachedByPlayer = false
        world.add(
            zone,
            PhysicsBody(
                shape = BoxShape(GOAL_ZONE_HALF_EXTENTS),
                motionType = MotionType.STATIC,
                sensor = true,
            ),
        )
    }

    /**
     * Collects the boxes pushed into the goal zone.
     *
     * The one thing in the showcase that reacts to physics having happened rather than to physics
     * having moved something. A box is not collected by being near the zone -- nothing here
     * measures a distance -- but by Jolt reporting that it entered, which is the difference
     * between a trigger and a poll.
     *
     * Registered on the fixed step *after* physics, because [PhysicsWorld.drainContacts] hands
     * over what the last [PhysicsWorld.step] produced. It is also the world's only drain: two
     * callers would each see the events the other did not.
     */
    fun goalZoneSystem(): System = object : System {
        override fun update(world: World, delta: Float) {
            val physicsWorld = ShowcasePhysics.world ?: return
            val zone = goalZone?.let { world.get<PhysicsBody>(it) }?.handle
            val pool = water?.let { world.get<PhysicsBody>(it) }?.handle

            // One drain for both triggers, because there can only be one: drainContacts hands over
            // the events since the last call and forgets them, so a second caller would see only
            // what the first left behind -- which is nothing.
            physicsWorld.drainContacts { event ->
                val other = when {
                    event.a == zone || event.a == pool -> event.b
                    event.b == zone || event.b == pool -> event.a
                    else -> return@drainContacts
                }
                val trigger = if (event.a == other) event.b else event.a
                when {
                    trigger == zone && event.phase == ContactPhase.BEGAN ->
                        collect(world, physicsWorld, other)

                    trigger == pool && event.phase == ContactPhase.BEGAN -> submerged += other
                    trigger == pool -> submerged -= other
                }
            }

            // Every step, for everything in the fluid: buoyancy is one step's worth of push rather
            // than a state a body is put into. A body that has left the water is dropped from the
            // set by its own ENDED event above, so this only ever pushes what is actually in it.
            submerged.forEach { body ->
                physicsWorld.applyBuoyancy(body, WATER_SURFACE_Y, Buoyancy.Water, delta)
            }
        }
    }

    /** How many bodies the pool is currently holding up, for tests and diagnostics. */
    val floatingCount: Int get() = submerged.size

    /**
     * Whether a body is currently in the water.
     *
     * Asked rather than tested: the pool already knows its occupants from contact events, so a
     * caller wanting to know about one of them costs a set lookup instead of an overlap query per
     * frame.
     */
    fun isSubmerged(body: BodyHandle?): Boolean = body != null && body in submerged

    /**
     * Takes a collected box out of the world, body first.
     *
     * Order matters and is the same rule `PhysicsCellStreamer` follows: destroying the entity first
     * drops the only reference to the handle, leaving a body that is invisible, still solid, and
     * unreachable forever.
     *
     * An unknown handle is ignored rather than treated as an error -- a drain can carry an event
     * for a body destroyed since, including one left over from a previous visit to this showcase.
     */
    private fun collect(world: World, physicsWorld: PhysicsWorld, body: BodyHandle) {
        // The player reaching the zone is the goal, not something to remove from the world. Its
        // body is the character controller's, so it is not one of the entities below.
        if (body == CharacterExampleDriver.characterBody) {
            reachedByPlayer = true
            return
        }
        var target: Entity? = null
        world.queryEach(PhysicsBody::class) { entity, physicsBody ->
            if (physicsBody.handle == body && !physicsBody.sensor) target = entity
        }
        val entity = target ?: return

        world.get<PhysicsBody>(entity)?.handle = null
        physicsWorld.destroyBody(body)
        world.destroy(entity)
        collected++
    }
}
