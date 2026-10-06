/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.Buoyancy
import com.awakekt.awake.physics.ContactPhase
import com.awakekt.awake.physics.ConvexHullShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.showcase.terrain.TerrainExampleAsset
import kotlin.random.Random

private const val BOX_HALF_EXTENT = 0.5f

/**
 * A wedge shape defined by 6 vertices (a triangular prism) to demonstrate dynamic convex hull physics.
 */
internal val WEDGE_HULL_POINTS = floatArrayOf(
    -0.5f, -0.5f, -0.5f,
    0.5f, -0.5f, -0.5f,
    -0.5f, -0.5f, 0.5f,
    0.5f, -0.5f, 0.5f,
    -0.5f, 0.5f, -0.5f,
    0.5f, 0.5f, -0.5f,
)

/**
 * The volume a box has to reach to be collected, and the one collider here that deliberately does
 * *not* match its mesh.
 */
private val GOAL_ZONE_HALF_EXTENTS = Vec3f(1.25f, 1f, 1.25f)

/**
 * The pool's collision volume, matching the `water` node's own extent.
 */
private val WATER_HALF_EXTENTS = Vec3f(2f, 1f, 2f)

/**
 * Where the surface is, in world space.
 */
private const val WATER_SURFACE_Y = 0.6f + 1f

/**
 * Shapes available for dynamic prop spawning in the heightfield terrain showcase.
 */
enum class PropShapeKind {
    Box,
    Sphere,
    Wedge,
}

internal class PendingImpulse(
    val linear: Vec3f,
    val angular: Vec3f? = null,
)

private data class BaselineProp(
    val entity: Entity,
    val initialPos: Vec3f,
    val shape: PhysicsShape,
    val mesh: String,
)

/**
 * The showcase's physics world, and the only one.
 */
internal object ShowcasePhysics {
    var world: PhysicsWorld? = null

    /** The system running [world], once built; triggers read its published contacts. */
    var physicsSystem: PhysicsSystem? = null

    fun system(): InterpolatedSystem = object : InterpolatedSystem {
        private var delegate: PhysicsSystem? = null

        override fun update(world: World, delta: Float) {
            val physicsWorld = ShowcasePhysics.world ?: return
            val system = delegate ?: PhysicsSystem(physicsWorld).also {
                delegate = it
                physicsSystem = it
            }
            system.update(world, delta)
        }

        override fun interpolate(world: World, alpha: Float) {
            delegate?.interpolate(world, alpha)
        }
    }
}

/**
 * Drops boxes and props onto the heightfield with interactive spawning and camera controls.
 */
internal object TerrainPhysicsExampleDriver {

    private var goalZone: Entity? = null
    private var water: Entity? = null
    private val submerged = mutableSetOf<BodyHandle>()

    private val baselineProps = mutableListOf<BaselineProp>()
    private val spawnedEntities = mutableListOf<Entity>()
    private val pendingImpulses = mutableListOf<Pair<Entity, PendingImpulse>>()
    private val random = Random(42)

    var activeRuntime: SceneAppLifecycleRuntime? = null
    var activeWorld: World? = null

    /** How many boxes have been pushed into the zone. */
    var collected = 0
        private set

    /** Whether the player has stood in the goal zone. */
    var reachedByPlayer = false
        private set

    /** Whether spawned props receive random linear and angular impulses upon creation. */
    var applyImpulsesOnSpawn = false

    /** Active camera rig mode for the showcase viewport (Orbit vs Free-Fly). */
    var cameraMode: CameraMode = CameraMode.ThirdPerson

    /** Total number of dynamically spawned props currently live. */
    val spawnedCount: Int get() = spawnedEntities.size

    val wedgeGeometry: MeshGeometry by lazy { buildWedgeGeometry() }

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        activeRuntime = runtime
        cameraMode = CameraMode.ThirdPerson
        val world = runtime.world
        activeWorld = world
        if (ShowcasePhysics.world == null) return

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

        instance.roots.find { it.name == "heightfield-terrain" }?.let { terrain ->
            world.add(
                terrain.entity,
                PhysicsBody(TerrainExampleAsset.collisionShape, MotionType.STATIC),
            )
        }

        baselineProps.clear()

        instance.roots.filter { it.name?.startsWith("falling-box-") == true }.forEach { box ->
            val transform = world.get<Transform>(box.entity)
            val shape = BoxShape(Vec3f(BOX_HALF_EXTENT, BOX_HALF_EXTENT, BOX_HALF_EXTENT))
            if (transform != null) {
                baselineProps += BaselineProp(box.entity, Vec3f(transform.position.x, transform.position.y, transform.position.z), shape, "cube")
            }
            world.add(box.entity, PhysicsBody(shape, MotionType.DYNAMIC))
        }

        instance.roots.find { it.name == "falling-wedge" }?.let { wedge ->
            val transform = world.get<Transform>(wedge.entity)
            val shape = ConvexHullShape(WEDGE_HULL_POINTS)
            if (transform != null) {
                baselineProps += BaselineProp(wedge.entity, Vec3f(transform.position.x, transform.position.y, transform.position.z), shape, "wedge")
            }
            world.add(wedge.entity, PhysicsBody(shape, MotionType.DYNAMIC))
        }

        instance.roots.find { it.name == "floating-box" }?.let { box ->
            val transform = world.get<Transform>(box.entity)
            val shape = BoxShape(Vec3f(BOX_HALF_EXTENT, BOX_HALF_EXTENT, BOX_HALF_EXTENT))
            if (transform != null) {
                baselineProps += BaselineProp(box.entity, Vec3f(transform.position.x, transform.position.y, transform.position.z), shape, "cube")
            }
            world.add(box.entity, PhysicsBody(shape, MotionType.DYNAMIC))
        }
    }

    fun spawnProp(
        world: World,
        shapeKind: PropShapeKind,
        position: Vec3f? = null,
        applyImpulse: Boolean = applyImpulsesOnSpawn,
    ): Entity {
        val entity = world.create()
        val spawnPos = position ?: Vec3f(
            x = (random.nextFloat() * 2f - 1f),
            y = 7.5f + (random.nextFloat() * 2f),
            z = (random.nextFloat() * 2f - 1f),
        )
        val transform = Transform().apply {
            this.position.set(spawnPos.x, spawnPos.y, spawnPos.z)
            this.rotation.set(
                random.nextFloat() * 3.14f,
                random.nextFloat() * 3.14f,
                random.nextFloat() * 3.14f,
            )
        }
        world.add(entity, transform)

        val (shape, meshName) = when (shapeKind) {
            PropShapeKind.Box -> BoxShape(Vec3f(BOX_HALF_EXTENT, BOX_HALF_EXTENT, BOX_HALF_EXTENT)) to "cube"
            PropShapeKind.Sphere -> SphereShape(0.5f) to "sphere"
            PropShapeKind.Wedge -> ConvexHullShape(WEDGE_HULL_POINTS) to "wedge"
        }

        world.add(entity, PhysicsBody(shape = shape, motionType = MotionType.DYNAMIC))

        activeRuntime?.let { runtime ->
            val mesh = runtime.requireMesh(meshName)
            val material = runtime.requireMaterial("lit-shadow")
            world.add(entity, MeshRenderer(mesh = mesh, material = material, cullMode = CullMode.Back))
        }

        if (applyImpulse) {
            val linear = Vec3f(
                random.nextFloat() * 8f - 4f,
                random.nextFloat() * 3f + 1f,
                random.nextFloat() * 8f - 4f,
            )
            val angular = Vec3f(
                random.nextFloat() * 6f - 3f,
                random.nextFloat() * 6f - 3f,
                random.nextFloat() * 6f - 3f,
            )
            pendingImpulses += Pair(entity, PendingImpulse(linear, angular))
        }

        spawnedEntities += entity
        return entity
    }

    fun resetProps(world: World) {
        val physicsSystem = ShowcasePhysics.physicsSystem

        spawnedEntities.forEach { entity ->
            if (world.isAlive(entity)) {
                physicsSystem?.destroyBody(world, entity)
                world.destroy(entity)
            }
        }
        spawnedEntities.clear()
        pendingImpulses.clear()

        baselineProps.forEach { prop ->
            if (world.isAlive(prop.entity)) {
                physicsSystem?.destroyBody(world, prop.entity)
                val transform = world.get<Transform>(prop.entity)
                transform?.position?.set(prop.initialPos.x, prop.initialPos.y, prop.initialPos.z)
                transform?.rotation?.set(0f, 0f, 0f)
                world.get<PhysicsBody>(prop.entity)?.handle = null
            }
        }

        submerged.clear()
        collected = 0
        reachedByPlayer = false
    }

    fun setCameraMode(world: World, mode: CameraMode) {
        cameraMode = mode
        world.queryEach(CameraRig::class, ActiveCamera::class) { _, rig, _ ->
            rig.mode = mode
        }
    }

    fun detach(world: World) {
        goalZone = null
        water = null
        submerged.clear()
        pendingImpulses.clear()
        cameraMode = CameraMode.ThirdPerson

        val physicsWorld = ShowcasePhysics.world
        val physicsSystem = ShowcasePhysics.physicsSystem

        spawnedEntities.forEach { entity ->
            if (world.isAlive(entity)) {
                physicsSystem?.destroyBody(world, entity)
                world.destroy(entity)
            }
        }
        spawnedEntities.clear()
        baselineProps.clear()

        if (physicsWorld != null) {
            world.queryEach<PhysicsBody> { _, body ->
                body.handle?.let(physicsWorld::destroyBody)
                body.handle = null
            }
        }

        activeRuntime = null
        activeWorld = null
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

    fun applyPendingImpulses(world: World, physicsWorld: PhysicsWorld) {
        if (pendingImpulses.isEmpty()) return
        val iterator = pendingImpulses.iterator()
        while (iterator.hasNext()) {
            val (entity, impulse) = iterator.next()
            val body = world.get<PhysicsBody>(entity)
            val handle = body?.handle
            if (handle != null) {
                physicsWorld.addImpulse(handle, impulse.linear)
                impulse.angular?.let { physicsWorld.setAngularVelocity(handle, it) }
                iterator.remove()
            }
        }
    }

    /**
     * Collects the boxes pushed into the goal zone.
     *
     * The one thing in the showcase that reacts to physics having happened rather than to physics
     * having moved something. A box is not collected by being near the zone -- nothing here
     * measures a distance -- but by Jolt reporting that it entered, which is the difference
     * between a trigger and a poll.
     *
     * Registered on the fixed step *after* physics, because it reads the contacts
     * [PhysicsSystem] published for the step that just ran. Any other trigger can read the same
     * list.
     */
    fun goalZoneSystem(): System = object : System {
        override fun update(world: World, delta: Float) {
            val physicsWorld = ShowcasePhysics.world ?: return
            val zone = goalZone?.let { world.get<PhysicsBody>(it) }?.handle
            val pool = water?.let { world.get<PhysicsBody>(it) }?.handle

            applyPendingImpulses(world, physicsWorld)

            // PhysicsSystem drained this step's contacts; read them rather than draining again,
            // which would hand this system nothing.
            val contacts = ShowcasePhysics.physicsSystem?.contacts ?: return
            for (i in contacts.indices) {
                val event = contacts[i]
                val other = when {
                    event.a == zone || event.a == pool -> event.b
                    event.b == zone || event.b == pool -> event.a
                    else -> continue
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
        val entity = ShowcasePhysics.physicsSystem?.entityFor(body)
            ?.takeIf { world.isAlive(it) && world.get<PhysicsBody>(it)?.sensor == false }
            ?: return

        world.get<PhysicsBody>(entity)?.handle = null
        physicsWorld.destroyBody(body)
        world.destroy(entity)
        collected++
    }
}

private fun buildWedgeGeometry(): MeshGeometry {
    val p0 = Vec3f(-0.5f, -0.5f, -0.5f)
    val p1 = Vec3f(0.5f, -0.5f, -0.5f)
    val p2 = Vec3f(-0.5f, -0.5f, 0.5f)
    val p3 = Vec3f(0.5f, -0.5f, 0.5f)
    val p4 = Vec3f(-0.5f, 0.5f, -0.5f)
    val p5 = Vec3f(0.5f, 0.5f, -0.5f)

    val invSqrt2 = 0.70710678f
    val nBottom = Vec3f(0f, -1f, 0f)
    val nBack = Vec3f(0f, 0f, -1f)
    val nSlant = Vec3f(0f, invSqrt2, invSqrt2)
    val nLeft = Vec3f(-1f, 0f, 0f)
    val nRight = Vec3f(1f, 0f, 0f)

    val cBottom = Vec3f(0.85f, 0.55f, 0.2f)
    val cBack = Vec3f(0.75f, 0.45f, 0.15f)
    val cSlant = Vec3f(0.95f, 0.65f, 0.25f)
    val cLeft = Vec3f(0.8f, 0.5f, 0.18f)
    val cRight = Vec3f(0.8f, 0.5f, 0.18f)

    val vertices = FloatArray(18 * 9)
    fun writeVertex(offset: Int, pos: Vec3f, normal: Vec3f, color: Vec3f) {
        val i = offset * 9
        vertices[i + 0] = pos.x
        vertices[i + 1] = pos.y
        vertices[i + 2] = pos.z
        vertices[i + 3] = normal.x
        vertices[i + 4] = normal.y
        vertices[i + 5] = normal.z
        vertices[i + 6] = color.x
        vertices[i + 7] = color.y
        vertices[i + 8] = color.z
    }

    writeVertex(0, p0, nBottom, cBottom)
    writeVertex(1, p1, nBottom, cBottom)
    writeVertex(2, p3, nBottom, cBottom)
    writeVertex(3, p2, nBottom, cBottom)

    writeVertex(4, p1, nBack, cBack)
    writeVertex(5, p0, nBack, cBack)
    writeVertex(6, p4, nBack, cBack)
    writeVertex(7, p5, nBack, cBack)

    writeVertex(8, p2, nSlant, cSlant)
    writeVertex(9, p3, nSlant, cSlant)
    writeVertex(10, p5, nSlant, cSlant)
    writeVertex(11, p4, nSlant, cSlant)

    writeVertex(12, p0, nLeft, cLeft)
    writeVertex(13, p2, nLeft, cLeft)
    writeVertex(14, p4, nLeft, cLeft)

    writeVertex(15, p1, nRight, cRight)
    writeVertex(16, p5, nRight, cRight)
    writeVertex(17, p3, nRight, cRight)

    val indices = intArrayOf(
        0, 1, 2, 0, 2, 3,
        4, 5, 6, 4, 6, 7,
        8, 9, 10, 8, 10, 11,
        12, 13, 14,
        15, 16, 17,
    )

    return MeshGeometry(vertices, indices, VertexFormat.PositionNormalColor)
}
