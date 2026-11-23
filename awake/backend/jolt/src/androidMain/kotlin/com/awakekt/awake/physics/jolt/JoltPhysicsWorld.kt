/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BallSocketConstraint
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BodyTransformVisitor
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.Buoyancy
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.Constraint
import com.awakekt.awake.physics.ConstraintHandle
import com.awakekt.awake.physics.ContactEvent
import com.awakekt.awake.physics.ConvexHullShape
import com.awakekt.awake.physics.DegreesOfFreedom
import com.awakekt.awake.physics.DistanceConstraint
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.HingeConstraint
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsCapabilityException
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.RaycastHit
import com.awakekt.awake.physics.ScaledShape
import com.awakekt.awake.physics.ShapeCastHit
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.physics.requireCanBeSensor
import com.github.stephengold.joltjni.AllHitCastRayCollector
import com.github.stephengold.joltjni.AllHitCastShapeCollector
import com.github.stephengold.joltjni.AllHitCollideShapeCollector
import com.github.stephengold.joltjni.BodyCreationSettings
import com.github.stephengold.joltjni.BodyFilter
import com.github.stephengold.joltjni.BodyIdVector
import com.github.stephengold.joltjni.BodyInterface
import com.github.stephengold.joltjni.BodyLockWrite
import com.github.stephengold.joltjni.BroadPhaseLayerFilter
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable
import com.github.stephengold.joltjni.CastRayCollector
import com.github.stephengold.joltjni.CastShapeCollector
import com.github.stephengold.joltjni.ClosestHitCastRayCollector
import com.github.stephengold.joltjni.ClosestHitCastShapeCollector
import com.github.stephengold.joltjni.CollideShapeSettings
import com.github.stephengold.joltjni.ConvexHullShapeSettings
import com.github.stephengold.joltjni.DistanceConstraintSettings
import com.github.stephengold.joltjni.HeightFieldShapeSettings
import com.github.stephengold.joltjni.HingeConstraintSettings
import com.github.stephengold.joltjni.JobSystem
import com.github.stephengold.joltjni.JobSystemThreadPool
import com.github.stephengold.joltjni.Jolt
import com.github.stephengold.joltjni.MeshShapeSettings
import com.github.stephengold.joltjni.ObjectLayerFilter
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable
import com.github.stephengold.joltjni.PhysicsSystem
import com.github.stephengold.joltjni.RMat44
import com.github.stephengold.joltjni.RRayCast
import com.github.stephengold.joltjni.RShapeCast
import com.github.stephengold.joltjni.RVec3
import com.github.stephengold.joltjni.RayCastResult
import com.github.stephengold.joltjni.RayCastSettings
import com.github.stephengold.joltjni.ScaledShapeSettings
import com.github.stephengold.joltjni.ShapeCastResult
import com.github.stephengold.joltjni.ShapeCastSettings
import com.github.stephengold.joltjni.ShapeFilter
import com.github.stephengold.joltjni.ShapeRefC
import com.github.stephengold.joltjni.ShapeResult
import com.github.stephengold.joltjni.SixDofConstraintSettings
import com.github.stephengold.joltjni.SpecifiedObjectLayerFilter
import com.github.stephengold.joltjni.TempAllocator
import com.github.stephengold.joltjni.TempAllocatorMalloc
import com.github.stephengold.joltjni.TwoBodyConstraint
import com.github.stephengold.joltjni.Vec3
import com.github.stephengold.joltjni.enumerate.EActivation
import com.github.stephengold.joltjni.enumerate.EAllowedDofs
import com.github.stephengold.joltjni.enumerate.EAxis
import com.github.stephengold.joltjni.enumerate.EBackFaceMode
import com.github.stephengold.joltjni.enumerate.EBodyType
import com.github.stephengold.joltjni.enumerate.EConstraintSpace
import com.github.stephengold.joltjni.enumerate.EMotionQuality
import com.github.stephengold.joltjni.enumerate.EMotionType
import com.github.stephengold.joltjni.readonly.ConstShape
import kotlin.math.sqrt

private fun shapeResultGet(result: ShapeResult, what: String): ShapeRefC {
    if (result.hasError()) {
        throw PhysicsCapabilityException("$what failed: ${result.error}")
    }
    return result.get()
}

private fun scaledShapeOf(inner: ConstShape, scale: Vec3f): ShapeRefC =
    ScaledShapeSettings(inner, scale.x, scale.y, scale.z).use { settings ->
        settings.create().use { result -> shapeResultGet(result, "ScaledShapeSettings") }
    }

/**
 * The jolt-jni shape for a convex [PhysicsShape].
 *
 * Shared by body creation and [PhysicsWorld.shapeCast], which is the point: a character swept
 * through the world with a different shape from the one it collides with is a bug that looks
 * like bad tuning.
 */
private fun convexShapeOf(shape: PhysicsShape): ConstShape = when (shape) {
    is BoxShape -> com.github.stephengold.joltjni.BoxShape(
        shape.halfExtents.x,
        shape.halfExtents.y,
        shape.halfExtents.z,
    )

    is SphereShape -> com.github.stephengold.joltjni.SphereShape(shape.radius)
    is CapsuleShape -> com.github.stephengold.joltjni.CapsuleShape(shape.halfHeight, shape.radius)
    is ConvexHullShape -> {
        val points = ArrayList<Vec3>(shape.pointCount)
        for (index in 0 until shape.pointCount) {
            val base = index * 3
            points += Vec3(shape.points[base], shape.points[base + 1], shape.points[base + 2])
        }
        ConvexHullShapeSettings(points).use { settings ->
            // The shape lives as long as this reference does: body creation copies a reference of
            // its own, and JoltQueryShapes keeps the ones queries reuse.
            settings.create().use { result -> shapeResultGet(result, "ConvexHullShapeSettings") }
        }
    }

    is ScaledShape -> {
        val inner = convexShapeOf(shape.shape)
        try {
            scaledShapeOf(inner, shape.scale)
        } finally {
            (inner as? AutoCloseable)?.close()
        }
    }

    is MeshShape ->
        throw PhysicsCapabilityException(
            "A MeshShape is a surface with no inside, so it cannot be swept or simulated -- " +
                "use a ConvexHullShape",
        )
    is HeightFieldShape ->
        throw PhysicsCapabilityException("A HeightFieldShape is terrain to cast against, not a shape to cast with")
}

/**
 * The jolt-jni shape for a triangle mesh.
 *
 * Packs triangle corners into a single direct [java.nio.FloatBuffer] so JNI is crossed once per
 * unique mesh (zero per-vertex `Float3` or per-triangle `IndexedTriangle` allocations) and Jolt's
 * native `MeshShapeSettings(TriangleList)` constructor runs `Indexify()` + `Sanitize()` to weld
 * vertices and drop quantized-space degenerate triangles.
 */
private fun meshShapeOf(shape: MeshShape): ShapeRefC {
    val buffer = Jolt.newDirectFloatBuffer(shape.triangleCount * FLOATS_PER_TRIANGLE)
    val vertices = shape.vertices
    val indices = shape.indices
    for (index in 0 until shape.triangleCount) {
        val base = index * INDICES_PER_TRIANGLE
        for (corner in 0 until INDICES_PER_TRIANGLE) {
            val v = indices[base + corner] * VALUES_PER_VERTEX
            buffer.put(vertices[v])
            buffer.put(vertices[v + 1])
            buffer.put(vertices[v + 2])
        }
    }
    buffer.rewind()
    return MeshShapeSettings(buffer).use { settings ->
        settings.create().use { result -> shapeResultGet(result, "MeshShapeSettings") }
    }
}

/** The jolt-jni shape for a heightfield, offset so the field is centred if the engine's is. */
private fun heightFieldShapeOf(shape: HeightFieldShape): ShapeRefC {
    val offset = com.github.stephengold.joltjni.Vec3(
        heightFieldOffset(shape.origin, shape.sampleCount, shape.scale.x),
        0f,
        heightFieldOffset(shape.origin, shape.sampleCount, shape.scale.z),
    )
    return HeightFieldShapeSettings(
        shape.heights,
        offset,
        com.github.stephengold.joltjni.Vec3(shape.scale.x, shape.scale.y, shape.scale.z),
        shape.sampleCount,
    ).use { settings ->
        settings.create().use { result -> shapeResultGet(result, "HeightFieldShapeSettings") }
    }
}

private const val VALUES_PER_VERTEX = 3
private const val INDICES_PER_TRIANGLE = 3
private const val FLOATS_PER_TRIANGLE = VALUES_PER_VERTEX * INDICES_PER_TRIANGLE

// Jolt Physics integration slice 1 (see docs/reference/decision-log.md): only 2 object
// layers, matching jolt-jni's own HelloJoltJni tutorial -- fine-grained per-game layer
// authoring (multiple moving layers with custom collision rules) is out of scope for this
// slice, the same "coarse first, refine later" scoping this slice's API itself follows.
private const val NUM_BROADPHASE_LAYERS = 2
private const val BROADPHASE_LAYER_STATIC = 0
private const val BROADPHASE_LAYER_MOVING = 1
private const val MAX_BODIES = 65_536
private const val MAX_BODY_PAIRS = 65_536
private const val MAX_CONTACTS = 20_480

/**
 * jolt-jni-backed [PhysicsWorld] -- Android only (see `desktopMain`'s near-identical
 * duplicate, and this module's `build.gradle.kts` comment for why it isn't a shared
 * intermediate source set). Duplicated verbatim rather than factored into a shared
 * `commonMain` class because jolt-jni's actual JVM classes ARE the desktop/Android
 * implementation -- there's no additional platform-neutral layer to extract without
 * reinventing jolt-jni's own API.
 *
 * Use a world from one thread at a time, as the loop that steps it does. Its queries share one set
 * of scratch objects, so two queries running at once on different threads would overwrite each
 * other's. A query an [overlapShape] callback makes is safe: that is handled.
 */
@Suppress("TooManyFunctions") // One per PhysicsWorld member; the count is the interface's.
class JoltPhysicsWorld(
    gravity: Vec3f = Vec3f(0f, -9.81f, 0f),
    override val layers: CollisionLayers = CollisionLayers.Default,
) : PhysicsWorld {
    private val shapeCastContacts = ShapeCastContactDecoder()

    // Companion object init always runs before any instance member -- the only ordering
    // guarantee strong enough to load the native lib before tempAllocator's native constructor
    // call (confirmed via UnsatisfiedLinkError when this lived in an instance init instead).
    private companion object {
        init {
            JoltNative.ensureLoaded()
        }
    }

    private val physicsSystem: PhysicsSystem

    /** Kept for query filters: Jolt needs it again to answer "what would layer X hit". */
    private val objectLayerPairFilter: ObjectLayerPairFilterTable
    private val bodyInterface: BodyInterface
    private val tempAllocator: TempAllocator = TempAllocatorMalloc()
    private val jobSystem: JobSystem

    // Held in a field, not just handed to Jolt: the native side keeps a raw pointer to this Java
    // object, so letting it be collected would leave Jolt calling into freed memory.
    private val contacts = JoltContactQueue()

    // Every body this PhysicsWorld created, so syncTransforms() knows what to read back and
    // destroy() knows what to tear down -- jolt-jni's PhysicsSystem itself doesn't expose an
    // "iterate my bodies" call cheap enough to lean on instead (getBodies() copies into a
    // BodyIdVector every call).
    private val trackedBodyIds = mutableListOf<Int>()

    /**
     * Live constraints by handle, and which bodies each joins.
     *
     * Kept because Jolt does not: a constraint left referencing a destroyed body is a crash on the
     * next step rather than an error, so [destroyBody] has to be able to find them.
     */
    // LinkedHashMap and LinkedHashSet, not the hash forms: teardown iterates these, and
    // destruction order decides which body ids Jolt hands back out next, which feeds its
    // island ordering and so the simulation. Insertion order replays; hash order does not.
    private val constraints = LinkedHashMap<Long, TwoBodyConstraint>()
    private val constraintBodies = LinkedHashMap<Long, Pair<Int, Int>>()
    private var nextConstraintId = 1L

    // Readback scratch. forEachBodyTransform runs once per frame forever, and each of these would
    // otherwise be garbage produced at exactly the rate the game is played.
    private val activeBodyIds = BodyIdVector()
    private val scratchPosition = RVec3()
    private val scratchRotation = com.github.stephengold.joltjni.Quat()
    private val scratchOutPosition = Vec3f()
    private val scratchOutRotation = Quat()

    // Query scratch (#570). A character controller sweeps about twice per fixed step, and every
    // jolt-jni object registers with a Cleaner, so building these per query made garbage that grew
    // with how many characters a scene moves. Only RShapeCast and RRayCast, which have no setters, are
    // still built per query. The explicit filters also stop jolt-jni building defaults inside every
    // call. Rays reuse the same set.
    private val queryShapes = JoltQueryShapes()

    // Built base MeshShape BVHs reused across unit-scale and ScaledShape placements in this world.
    // Closed in destroy() so native memory is released deterministically with the world.
    private val meshShapes = HashMap<MeshShape, ShapeRefC>()
    private val queryOrigin = RVec3()
    private val queryTransform = RMat44().apply { loadIdentity() }
    private val queryDirection = com.github.stephengold.joltjni.Vec3()
    private val unitScale = com.github.stephengold.joltjni.Vec3(1f, 1f, 1f)
    private val noBaseOffset = RVec3(0.0, 0.0, 0.0)

    // Initial overlaps need a separating axis even when the cast has no travel. Triangle back
    // faces block as well (#1030): a sweep let through a one-sided mesh from behind leaves the
    // shape inside it, where every later sweep reports a hit at fraction 0.
    private val shapeCastSettings = ShapeCastSettings().apply {
        setReturnDeepestPoint(true)
        setBackFaceModeTriangles(EBackFaceMode.CollideWithBackFaces)
    }
    private val rayCastSettings = RayCastSettings()
    private val closestRayHit = ClosestHitCastRayCollector()
    private val allRayHits = AllHitCastRayCollector()
    private val closestCastHit = ClosestHitCastShapeCollector()
    private val allCastHits = AllHitCastShapeCollector()
    private val collideSettings = CollideShapeSettings()
    private val overlapHits = AllHitCollideShapeCollector()
    private val anyBroadPhaseLayer = BroadPhaseLayerFilter()
    private val anyObjectLayer = ObjectLayerFilter()
    private val anyBody = BodyFilter()
    private val anyShape = ShapeFilter()
    private val layerFilters = arrayOfNulls<SpecifiedObjectLayerFilter>(CollisionLayers.MAX_LAYERS)

    // overlapHits is read while overlapShape's callback runs, and that callback may query again.
    private var overlapping = false

    init {
        objectLayerPairFilter = ObjectLayerPairFilterTable(layers.count).apply {
            // Built from the world's own matrix rather than hardcoded. Jolt wants each pair stated
            // once; the matrix is symmetric by construction, so the upper triangle covers it.
            for (a in 0 until layers.count) {
                for (b in a until layers.count) {
                    if (layers.collides(CollisionLayer(a), CollisionLayer(b))) {
                        enableCollision(a, b)
                    } else {
                        disableCollision(a, b)
                    }
                }
            }
        }
        val broadPhaseLayerInterface =
            BroadPhaseLayerInterfaceTable(layers.count, NUM_BROADPHASE_LAYERS).apply {
                // Two trees: one for things that never move, one for things that do. Jolt rebuilds
                // the moving tree as bodies move, and terrain sitting in it would be rebuilt with
                // them for nothing.
                for (index in 0 until layers.count) {
                    val layer = CollisionLayer(index)
                    mapObjectToBroadPhaseLayer(
                        index,
                        if (layers.isMoving(layer)) BROADPHASE_LAYER_MOVING else BROADPHASE_LAYER_STATIC,
                    )
                }
            }
        val objectVsBroadPhaseLayerFilter = ObjectVsBroadPhaseLayerFilterTable(
            broadPhaseLayerInterface,
            NUM_BROADPHASE_LAYERS,
            objectLayerPairFilter,
            layers.count,
        )

        physicsSystem = PhysicsSystem().apply {
            init(
                MAX_BODIES,
                0,
                MAX_BODY_PAIRS,
                MAX_CONTACTS,
                broadPhaseLayerInterface,
                objectVsBroadPhaseLayerFilter,
                // Qualified: PhysicsSystem has a getObjectLayerPairFilter() of its own, and inside
                // `apply` the receiver wins -- it returns null until this very call succeeds.
                this@JoltPhysicsWorld.objectLayerPairFilter,
            )
            setGravity(gravity.x, gravity.y, gravity.z)
            setContactListener(contacts)
        }
        bodyInterface = physicsSystem.bodyInterface

        val numWorkerThreads = Runtime.getRuntime().availableProcessors()
        jobSystem =
            JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numWorkerThreads)
    }

    override fun createBody(
        shape: PhysicsShape,
        position: Vec3f,
        rotation: Quat,
        motionType: MotionType,
        layer: CollisionLayer,
        sensor: Boolean,
    ): BodyHandle = createBody(shape, position, rotation, motionType, layer, sensor, DegreesOfFreedom.ALL)

    override fun createBody(
        shape: PhysicsShape,
        position: Vec3f,
        rotation: Quat,
        motionType: MotionType,
        layer: CollisionLayer,
        sensor: Boolean,
        degreesOfFreedom: DegreesOfFreedom,
    ): BodyHandle {
        validateBodyRequest(shape, motionType, layer, sensor)
        val resolved = resolveBodyShape(shape)
        val joltMotionType = toJoltMotionType(motionType)
        try {
            BodyCreationSettings().use { bcs ->
                bcs.setShape(resolved.shape)
                bcs.setPosition(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
                bcs.setRotation(rotation.x, rotation.y, rotation.z, rotation.w)
                bcs.setMotionType(joltMotionType)
                bcs.setObjectLayer(layer.index)
                bcs.setIsSensor(sensor)
                bcs.setAllowedDofs(toJoltAllowedDofs(degreesOfFreedom))
                val body = bodyInterface.createBody(bcs)
                val activation =
                    if (motionType == MotionType.STATIC) EActivation.DontActivate else EActivation.Activate
                bodyInterface.addBody(body, activation)

                trackedBodyIds.add(body.id)
                if (sensor) contacts.setReporting(body.id, true)
                return BodyHandle(body.id.toLong())
            }
        } finally {
            // Cached base MeshShapes stay alive until destroy(); temporary handles are released once
            // the body holds its own Jolt reference.
            resolved.owned?.close()
        }
    }

    private fun validateBodyRequest(
        shape: PhysicsShape,
        motionType: MotionType,
        layer: CollisionLayer,
        sensor: Boolean,
    ) {
        (shape as? HeightFieldShape)?.requireSupportedMotionType(motionType)
        (shape as? MeshShape)?.requireSupportedMotionType(motionType)
        (shape as? ScaledShape)?.requireSupportedMotionType(motionType)
        if (sensor) shape.requireCanBeSensor()
        require(layer.index < layers.count) {
            "layer ${layer.index} is outside this world's ${layers.count} layers"
        }
    }

    private fun resolveBodyShape(shape: PhysicsShape): ResolvedShape = when (shape) {
        is BoxShape, is SphereShape, is CapsuleShape, is ConvexHullShape -> {
            val built = convexShapeOf(shape)
            ResolvedShape(built, built as? AutoCloseable)
        }
        is MeshShape -> ResolvedShape(cachedMeshShape(shape), owned = null)
        is ScaledShape -> when (val inner = shape.shape) {
            is MeshShape -> scaledShapeOf(cachedMeshShape(inner), shape.scale).let { ResolvedShape(it, it) }
            else -> convexShapeOf(shape).let { ResolvedShape(it, it as? AutoCloseable) }
        }
        is HeightFieldShape -> heightFieldShapeOf(shape).let { ResolvedShape(it, it) }
    }

    private fun cachedMeshShape(shape: MeshShape): ShapeRefC = meshShapes.getOrPut(shape) { meshShapeOf(shape) }

    override fun destroyBody(handle: BodyHandle) {
        val id = handle.id.toInt()
        // Constraints first, and this is not tidiness: Jolt does not detach them, so a constraint
        // still referencing a freed body crashes on the next step.
        removeConstraintsFor(id)
        bodyInterface.removeBody(id)
        bodyInterface.destroyBody(id)
        trackedBodyIds.remove(id)
        // Jolt hands the id back out to the next body created, which would inherit this one's
        // contact reporting.
        contacts.setReporting(id, false)
    }

    override fun step(deltaTime: Float) {
        val collisionSteps = 1
        physicsSystem.update(deltaTime, collisionSteps, tempAllocator, jobSystem)
    }

    override fun setLinearVelocity(handle: BodyHandle, velocity: Vec3f) {
        bodyInterface.setLinearVelocity(handle.id.toInt(), velocity.x, velocity.y, velocity.z)
    }

    override fun setAngularVelocity(handle: BodyHandle, velocity: Vec3f) {
        // Vec3 rather than three floats: jolt-jni's angular setter has no loose-component
        // overload the way its linear one does.
        bodyInterface.setAngularVelocity(
            handle.id.toInt(),
            com.github.stephengold.joltjni.Vec3(velocity.x, velocity.y, velocity.z),
        )
    }

    override fun getLinearVelocity(handle: BodyHandle): Vec3f {
        val velocity = bodyInterface.getLinearVelocity(handle.id.toInt())
        return Vec3f(velocity.x, velocity.y, velocity.z)
    }

    override fun addImpulse(handle: BodyHandle, impulse: Vec3f) {
        bodyInterface.addImpulse(
            handle.id.toInt(),
            com.github.stephengold.joltjni.Vec3(impulse.x, impulse.y, impulse.z),
        )
    }

    override fun moveKinematic(
        handle: BodyHandle,
        position: Vec3f,
        rotation: Quat,
        deltaTime: Float,
    ) {
        bodyInterface.moveKinematic(
            handle.id.toInt(),
            RVec3(position.x.toDouble(), position.y.toDouble(), position.z.toDouble()),
            com.github.stephengold.joltjni.Quat(rotation.x, rotation.y, rotation.z, rotation.w),
            deltaTime,
        )
    }

    override fun forEachBodyTransform(
        action: (handle: BodyHandle, position: Vec3f, rotation: Quat) -> Unit,
    ) = visitBodyTransforms { id, position, rotation -> action(BodyHandle(id), position, rotation) }

    override fun visitBodyTransforms(visitor: BodyTransformVisitor) {
        // Only the bodies Jolt considers awake. A sleeping crate has not moved, so reading it back
        // is a JNI crossing that returns the pose the caller already has -- and a scene spends far
        // more of its life settled than in motion.
        physicsSystem.getActiveBodies(EBodyType.RigidBody, activeBodyIds)
        for (index in 0 until activeBodyIds.size()) {
            val id = activeBodyIds[index]
            bodyInterface.getPositionAndRotation(id, scratchPosition, scratchRotation)
            scratchOutPosition.set(
                scratchPosition.x().toFloat(),
                scratchPosition.y().toFloat(),
                scratchPosition.z().toFloat(),
            )
            scratchOutRotation.x = scratchRotation.x
            scratchOutRotation.y = scratchRotation.y
            scratchOutRotation.z = scratchRotation.z
            scratchOutRotation.w = scratchRotation.w
            visitor.visit(id.toLong(), scratchOutPosition, scratchOutRotation)
        }
    }

    override fun shiftOrigin(offset: Vec3f) {
        // Read-then-write per body: jolt-jni exposes no bulk translate, and Jolt's own
        // PhysicsSystem::SaveState/RestoreState round trip would cost far more than a position
        // write per body for the handful of bodies a shift moves.
        //
        // Activation matters. A body asleep on the floor keeps its old position until something
        // wakes it, so shifting the world under a sleeping body without activating it leaves it
        // behind -- a static-looking prop at the OLD origin while everything else moved.
        val position = RVec3()
        val rotation = com.github.stephengold.joltjni.Quat()
        trackedBodyIds.forEach { id ->
            bodyInterface.getPositionAndRotation(id, position, rotation)
            bodyInterface.setPositionAndRotation(
                id,
                RVec3(
                    position.x() + offset.x.toDouble(),
                    position.y() + offset.y.toDouble(),
                    position.z() + offset.z.toDouble(),
                ),
                rotation,
                EActivation.Activate,
            )
        }
    }

    override fun raycast(
        origin: Vec3f,
        direction: Vec3f,
        maxDistance: Float,
        onlyLayer: CollisionLayer?,
    ): RaycastHit? = raycast(origin, direction, maxDistance, onlyLayer, ignore = null)

    override fun raycast(
        origin: Vec3f,
        direction: Vec3f,
        maxDistance: Float,
        onlyLayer: CollisionLayer?,
        ignore: BodyHandle?,
    ): RaycastHit? {
        // Normalized here rather than through Vec3f.normalized(), so a ray allocates only its answer.
        // A zero direction stays zero, as normalized() leaves it.
        val length = sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z)
        val inverseLength = if (length != 0f) 1f / length else 1f
        val directionX = direction.x * inverseLength
        val directionY = direction.y * inverseLength
        val directionZ = direction.z * inverseLength
        queryOrigin.set(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble())
        queryDirection.set(directionX * maxDistance, directionY * maxDistance, directionZ * maxDistance)
        // The collector form of castRay, not the one that fills a RayCastResult: Jolt reads that
        // result's fraction as the distance to beat, and jolt-jni gives no way to reset it for reuse.
        // The default RayCastSettings answer as that form does: convex shapes are solid, back faces
        // ignored.
        return RRayCast(queryOrigin, queryDirection).use { ray ->
            closestRayHit.reset()
            castRayInto(ray, onlyLayer, closestRayHit)
            // Same rule as shapeCast: a sensor is not solid, so it is never the answer to what a ray
            // is stopped by, and the ignored body is skipped. Only when the closest hit is one of
            // those does the whole set have to be collected: jolt-jni's BodyFilter can't be
            // overridden to skip them inside the query (see shapeCast).
            val closest = if (closestRayHit.hadHit()) closestRayHit.hit else null
            val solid = when {
                closest == null -> null
                !isReportable(closest.bodyId, ignore) -> nearestReportableRayHit(ray, onlyLayer, ignore)
                else -> closest
            } ?: return@use null

            val distance = maxDistance * solid.fraction
            RaycastHit(
                BodyHandle(solid.bodyId.toLong()),
                Vec3f(origin.x + directionX * distance, origin.y + directionY * distance, origin.z + directionZ * distance),
                distance,
            )
        }
    }

    /** The nearest ray hit that is neither a sensor nor [ignore]; see [raycast] for when this is reached. */
    private fun nearestReportableRayHit(ray: RRayCast, onlyLayer: CollisionLayer?, ignore: BodyHandle?): RayCastResult? {
        allRayHits.reset()
        castRayInto(ray, onlyLayer, allRayHits)
        var nearest: RayCastResult? = null
        for (index in 0 until allRayHits.countHits()) {
            val candidate = allRayHits.get(index)
            if (!isReportable(candidate.bodyId, ignore)) continue
            if (nearest == null || candidate.fraction < nearest.fraction) nearest = candidate
        }
        return nearest
    }

    private fun castRayInto(ray: RRayCast, onlyLayer: CollisionLayer?, collector: CastRayCollector) {
        physicsSystem.narrowPhaseQuery.castRay(
            ray,
            rayCastSettings,
            collector,
            anyBroadPhaseLayer,
            objectLayerFilter(onlyLayer),
            anyBody,
            anyShape,
        )
    }

    override fun shapeCast(
        shape: PhysicsShape,
        from: Vec3f,
        to: Vec3f,
        onlyLayer: CollisionLayer?,
        ignore: BodyHandle?,
    ): ShapeCastHit? {
        val joltShape = queryShapes.of(shape)
        queryOrigin.set(from.x.toDouble(), from.y.toDouble(), from.z.toDouble())
        queryTransform.setTranslation(queryOrigin)
        queryDirection.set(to.x - from.x, to.y - from.y, to.z - from.z)
        // Jolt's own BodyFilter cannot carry the exclusion here. jolt-jni exposes the base class
        // with no callback subclass, and an override is simply never called -- measured, not
        // assumed: a filter rejecting everything left the ray hitting the body anyway. So an
        // ignored body means collecting every hit and taking the nearest other one, which is
        // correct but slower, and is why the plain path below still uses the closest-hit
        // collector.
        // The closest hit is the answer unless it is one this query must not report, in which case
        // the whole set has to be collected -- the closest-hit collector told Jolt to stop looking
        // the moment it found this one, so there is nothing behind it to fall back to.
        return RShapeCast(joltShape, unitScale, queryTransform, queryDirection).use { cast ->
            val closest = closestShapeCastHit(cast, onlyLayer)
            val hit = when {
                closest == null -> null
                isReportable(closest.bodyId2, ignore) -> closest
                else -> nearestShapeCastHitExcluding(cast, onlyLayer, ignore)
            } ?: return@use null

            val contact = hit.contactPointOn2
            // Jolt reports the axis it would have to push along to separate the shapes, pointing
            // INTO the hit surface and unnormalized. A contact normal points back out, which is what
            // a caller slides along -- negating and normalizing here rather than making every call
            // site rediscover that.
            val axis = hit.penetrationAxis
            shapeCastContacts.decode(
                handle = BodyHandle(hit.bodyId2.toLong()),
                point = Vec3f(contact.x, contact.y, contact.z),
                axisX = axis.x,
                axisY = axis.y,
                axisZ = axis.z,
                fraction = hit.fraction,
                from = from,
                to = to,
            )
        }
    }

    private fun closestShapeCastHit(cast: RShapeCast, onlyLayer: CollisionLayer?): ShapeCastResult? {
        closestCastHit.reset()
        castShapeInto(cast, onlyLayer, closestCastHit)
        return if (closestCastHit.hadHit()) closestCastHit.hit else null
    }

    /**
     * Whether a hit on this body is one the caller asked about.
     *
     * Two bodies are never an answer to "what would block me": the one the caller asked to ignore,
     * and any sensor -- a sensor is not solid, so reporting one as an obstruction turns every
     * trigger volume into an invisible wall.
     */
    private fun isReportable(bodyId: Int, ignore: BodyHandle?): Boolean =
        bodyId != ignore?.id?.toInt() && !bodyInterface.isSensor(bodyId)

    /**
     * The nearest reportable hit, found by collecting all of them.
     *
     * Only reached when the closest hit was not reportable. jolt-jni's `BodyFilter` would have done
     * this inside the query, but it cannot be overridden -- see [shapeCast]. A character controller
     * reaches it on most sweeps, because the closest hit is the body it asked to ignore.
     */
    private fun nearestShapeCastHitExcluding(
        cast: RShapeCast,
        onlyLayer: CollisionLayer?,
        ignore: BodyHandle?,
    ): ShapeCastResult? {
        allCastHits.reset()
        castShapeInto(cast, onlyLayer, allCastHits)
        var nearest: ShapeCastResult? = null
        for (index in 0 until allCastHits.countHits()) {
            val candidate = allCastHits.get(index)
            if (!isReportable(candidate.bodyId2, ignore)) continue
            if (nearest == null || candidate.fraction < nearest.fraction) nearest = candidate
        }
        return nearest
    }

    private fun castShapeInto(cast: RShapeCast, onlyLayer: CollisionLayer?, collector: CastShapeCollector) {
        physicsSystem.narrowPhaseQuery.castShape(
            cast,
            shapeCastSettings,
            noBaseOffset,
            collector,
            anyBroadPhaseLayer,
            objectLayerFilter(onlyLayer),
            anyBody,
            anyShape,
        )
    }

    // SpecifiedObjectLayerFilter is the only public layer filter jolt-jni offers, and it matches one
    // layer rather than a mask -- see PhysicsWorld.shapeCast's own note. It has no setter, so each
    // layer keeps its own.
    private fun objectLayerFilter(onlyLayer: CollisionLayer?): ObjectLayerFilter = when {
        onlyLayer == null -> anyObjectLayer
        onlyLayer.index >= layerFilters.size -> SpecifiedObjectLayerFilter(onlyLayer.index)
        else -> layerFilters[onlyLayer.index]
            ?: SpecifiedObjectLayerFilter(onlyLayer.index).also { layerFilters[onlyLayer.index] = it }
    }

    override fun overlapShape(
        shape: PhysicsShape,
        position: Vec3f,
        onlyLayer: CollisionLayer?,
        onOverlap: (BodyHandle) -> Unit,
    ) {
        // The callback may overlap again; the collector it is reading from then has to be another.
        val reuse = !overlapping
        val collector = if (reuse) overlapHits.also { it.reset() } else AllHitCollideShapeCollector()
        queryOrigin.set(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
        queryTransform.setTranslation(queryOrigin)
        physicsSystem.narrowPhaseQuery.collideShape(
            queryShapes.of(shape),
            unitScale,
            queryTransform,
            collideSettings,
            noBaseOffset,
            collector,
            anyBroadPhaseLayer,
            objectLayerFilter(onlyLayer),
            anyBody,
            anyShape,
        )
        overlapping = true
        try {
            for (index in 0 until collector.countHits()) {
                onOverlap(BodyHandle(collector.get(index).bodyId2.toLong()))
            }
        } finally {
            if (reuse) overlapping = false
        }
    }

    override fun setContinuousCollision(handle: BodyHandle, enabled: Boolean) {
        // Jolt's own name for it: a discrete step integrates then looks, a linear cast sweeps.
        bodyInterface.setMotionQuality(
            handle.id.toInt(),
            if (enabled) EMotionQuality.LinearCast else EMotionQuality.Discrete,
        )
    }

    override fun setActive(handle: BodyHandle, active: Boolean) {
        val id = handle.id.toInt()
        if (active) bodyInterface.activateBody(id) else bodyInterface.deactivateBody(id)
    }

    override fun isActive(handle: BodyHandle): Boolean = bodyInterface.isActive(handle.id.toInt())

    override fun createConstraint(constraint: Constraint): ConstraintHandle {
        val bodyA = lockedBody(constraint.bodyA) ?: error("body ${constraint.bodyA.id} is not in this world")
        val bodyB = lockedBody(constraint.bodyB) ?: error("body ${constraint.bodyB.id} is not in this world")
        val created = when (constraint) {
            is HingeConstraint -> hingeSettings(constraint).use { it.create(bodyA, bodyB) }
            is DistanceConstraint -> distanceSettings(constraint).use { it.create(bodyA, bodyB) }
            is BallSocketConstraint -> ballSocketSettings(constraint).use { it.create(bodyA, bodyB) }
        }
        physicsSystem.addConstraint(created)
        val id = nextConstraintId++
        constraints[id] = created
        constraintBodies[id] = constraint.bodyA.id.toInt() to constraint.bodyB.id.toInt()
        return ConstraintHandle(id)
    }

    override fun destroyConstraint(handle: ConstraintHandle) {
        val constraint = constraints.remove(handle.id) ?: return
        constraintBodies.remove(handle.id)
        physicsSystem.removeConstraint(constraint)
    }

    /**
     * The Body behind a handle, or null when it is not in this world.
     *
     * Jolt's constraint settings want Body references rather than ids, and the lock is also the
     * only honest way to answer whether a handle is still live.
     */
    private fun lockedBody(handle: BodyHandle): com.github.stephengold.joltjni.Body? =
        BodyLockWrite(physicsSystem.bodyLockInterface, handle.id.toInt()).use { locked ->
            if (locked.succeeded()) locked.body else null
        }

    private fun ballSocketSettings(constraint: BallSocketConstraint) = SixDofConstraintSettings().apply {
        setSpace(EConstraintSpace.WorldSpace)
        val point = RVec3(
            constraint.point.x.toDouble(),
            constraint.point.y.toDouble(),
            constraint.point.z.toDouble(),
        )
        setPosition1(point)
        setPosition2(point)
        val twist = constraint.twistAxis.normalized()
        val reference = constraint.swingReferenceAxis()
        setAxisX1(com.github.stephengold.joltjni.Vec3(twist.x, twist.y, twist.z))
        setAxisX2(com.github.stephengold.joltjni.Vec3(twist.x, twist.y, twist.z))
        setAxisY1(com.github.stephengold.joltjni.Vec3(reference.x, reference.y, reference.z))
        setAxisY2(com.github.stephengold.joltjni.Vec3(reference.x, reference.y, reference.z))
        // A ball joint pivots and does not slide, so all three translations are pinned. This is the
        // whole reason six-DOF is used here rather than a distance constraint: pinning translation
        // outright is what a socket does, and leaves the rotations free to be limited below.
        makeFixedAxis(EAxis.TranslationX)
        makeFixedAxis(EAxis.TranslationY)
        makeFixedAxis(EAxis.TranslationZ)
        // Rotation about X is the twist, Y and Z together are the swing -- Jolt's own convention,
        // which is why AxisX above is the twist axis.
        val twistLimit = constraint.twistLimit
        if (twistLimit == null) {
            makeFreeAxis(EAxis.RotationX)
        } else {
            setLimitedAxis(EAxis.RotationX, twistLimit.start, twistLimit.endInclusive)
        }
        if (constraint.swingLimit >= BallSocketConstraint.FREE_SWING) {
            makeFreeAxis(EAxis.RotationY)
            makeFreeAxis(EAxis.RotationZ)
        } else {
            // Symmetric, because the cone swing type reads the maximum and assumes the minimum is
            // its negation. Setting them asymmetrically here would silently be ignored.
            setLimitedAxis(EAxis.RotationY, -constraint.swingLimit, constraint.swingLimit)
            setLimitedAxis(EAxis.RotationZ, -constraint.swingLimit, constraint.swingLimit)
        }
    }

    private fun hingeSettings(constraint: HingeConstraint) = HingeConstraintSettings().apply {
        // World space, so Jolt converts to each body's local frame using the poses they hold right
        // now -- which is what makes the current arrangement the constraint's rest pose.
        setSpace(EConstraintSpace.WorldSpace)
        setPoint1(RVec3(constraint.point.x.toDouble(), constraint.point.y.toDouble(), constraint.point.z.toDouble()))
        setPoint2(RVec3(constraint.point.x.toDouble(), constraint.point.y.toDouble(), constraint.point.z.toDouble()))
        val axis = constraint.axis.normalized()
        setHingeAxis1(com.github.stephengold.joltjni.Vec3(axis.x, axis.y, axis.z))
        setHingeAxis2(com.github.stephengold.joltjni.Vec3(axis.x, axis.y, axis.z))
        constraint.limits?.let {
            setLimitsMin(it.start)
            setLimitsMax(it.endInclusive)
        }
    }

    private fun distanceSettings(constraint: DistanceConstraint) = DistanceConstraintSettings().apply {
        setSpace(EConstraintSpace.WorldSpace)
        setPoint1(
            RVec3(
                constraint.pointA.x.toDouble(),
                constraint.pointA.y.toDouble(),
                constraint.pointA.z.toDouble(),
            ),
        )
        setPoint2(
            RVec3(
                constraint.pointB.x.toDouble(),
                constraint.pointB.y.toDouble(),
                constraint.pointB.z.toDouble(),
            ),
        )
        setMinDistance(constraint.minDistance)
        setMaxDistance(constraint.maxDistance)
    }

    /** Removes every constraint touching this body; see [destroyBody] for why that is mandatory. */
    private fun removeConstraintsFor(bodyId: Int) {
        constraintBodies.entries
            .filter { (_, bodies) -> bodies.first == bodyId || bodies.second == bodyId }
            .map { it.key }
            .forEach { destroyConstraint(ConstraintHandle(it)) }
    }

    override fun applyBuoyancy(
        handle: BodyHandle,
        surfaceY: Float,
        buoyancy: Buoyancy,
        deltaTime: Float,
    ) {
        // jolt-jni puts this on Body rather than BodyInterface, so it needs the lock. The lock also
        // answers the "is it still there" question for free: a handle destroyed since the caller
        // collected it simply fails to lock rather than throwing.
        BodyLockWrite(physicsSystem.bodyLockInterface, handle.id.toInt()).use { locked ->
            // Dynamic only, and this is a hard requirement rather than a tidy-up: Jolt's own
            // ApplyBuoyancyImpulse reaches for motion properties a static body does not have, and
            // aborts the process rather than returning. A water volume contains whatever happens
            // to be floating in it, including the level, so the check belongs here.
            // Asked of the locked body, never of the body interface: that would take a second lock
            // on a body this already holds one for, and a recursive lock is an assert in a checked
            // build. Sleeping bodies are skipped because buoyancy adds velocity and a sleeping body
            // has no motion state to receive it -- Android's debug artifact asserts on that, which
            // is how this was found while desktop's release build ran on quietly.
            if (!locked.succeeded()) return
            val body = locked.body
            if (body.motionType != EMotionType.Dynamic || !body.isActive) return
            val gravity = physicsSystem.gravity
            body.applyBuoyancyImpulse(
                RVec3(0.0, surfaceY.toDouble(), 0.0),
                com.github.stephengold.joltjni.Vec3(0f, 1f, 0f),
                buoyancy.strength,
                buoyancy.linearDrag,
                buoyancy.angularDrag,
                com.github.stephengold.joltjni.Vec3(
                    buoyancy.fluidVelocity.x,
                    buoyancy.fluidVelocity.y,
                    buoyancy.fluidVelocity.z,
                ),
                gravity,
                deltaTime,
            )
        }
    }

    override fun setContactReporting(handle: BodyHandle, enabled: Boolean) {
        contacts.setReporting(handle.id.toInt(), enabled)
    }

    override fun drainContacts(action: (ContactEvent) -> Unit) = contacts.drain(action)

    override fun destroy() {
        constraints.keys.toList().forEach { destroyConstraint(ConstraintHandle(it)) }
        trackedBodyIds.forEach { id ->
            bodyInterface.removeBody(id)
            bodyInterface.destroyBody(id)
            contacts.setReporting(id, false)
        }
        trackedBodyIds.clear()
        meshShapes.values.forEach { it.close() }
        meshShapes.clear()
        queryShapes.clear()
        listOf(
            queryTransform, shapeCastSettings, closestCastHit, allCastHits, collideSettings, overlapHits,
            rayCastSettings, closestRayHit, allRayHits,
            anyBroadPhaseLayer, anyObjectLayer, anyBody, anyShape,
        ).forEach { it.close() }
        layerFilters.forEach { it?.close() }
        jobSystem.close()
        tempAllocator.close()
        physicsSystem.close()
    }
}

private class ResolvedShape(val shape: ConstShape, val owned: AutoCloseable?)

private fun toJoltMotionType(motionType: MotionType): EMotionType = when (motionType) {
    MotionType.STATIC -> EMotionType.Static
    MotionType.KINEMATIC -> EMotionType.Kinematic
    MotionType.DYNAMIC -> EMotionType.Dynamic
}

private fun toJoltAllowedDofs(degreesOfFreedom: DegreesOfFreedom): Int = when (degreesOfFreedom) {
    DegreesOfFreedom.ALL -> EAllowedDofs.All
    DegreesOfFreedom.PLANE_2D -> EAllowedDofs.Plane2D
}

/**
 * Jolt lays a heightfield out from its offset corner, so a centred field needs half of itself
 * subtracted; a corner-anchored one needs nothing. The engine's own heightmaps declare which they
 * are, and the collider has to agree with the mesh built from the same samples.
 */
private fun heightFieldOffset(origin: GridOrigin, sampleCount: Int, scale: Float): Float =
    when (origin) {
        GridOrigin.Centered -> -(sampleCount - 1) * scale * 0.5f
        GridOrigin.Corner -> 0f
    }

/**
 * The jolt-jni shapes queries sweep and overlap with, kept between calls (#570).
 *
 * A character controller passes the same capsule on every sweep, and building its Jolt shape each
 * time was a large part of a sweep's garbage. Shapes are values, so equal ones share an entry: a
 * crowd of identical capsules costs one Jolt shape. The cache is bounded, so a caller that passes a
 * new shape every time pays what it paid before, and an evicted shape is freed with its reference.
 */
private class JoltQueryShapes {
    private val shapes = object : LinkedHashMap<PhysicsShape, ConstShape>(CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PhysicsShape, ConstShape>): Boolean {
            if (size <= CAPACITY) return false
            (eldest.value as? AutoCloseable)?.close()
            return true
        }
    }

    fun of(shape: PhysicsShape): ConstShape = shapes.getOrPut(shape) { convexShapeOf(shape) }

    fun clear() {
        shapes.values.forEach { (it as? AutoCloseable)?.close() }
        shapes.clear()
    }

    private companion object {
        const val CAPACITY = 64
        const val LOAD_FACTOR = 0.75f
    }
}
