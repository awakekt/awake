/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.RaycastHit
import com.awakekt.awake.physics.ShapeCastHit
import com.github.stephengold.joltjni.AllHitCastRayCollector
import com.github.stephengold.joltjni.AllHitCastShapeCollector
import com.github.stephengold.joltjni.AllHitCollideShapeCollector
import com.github.stephengold.joltjni.BodyFilter
import com.github.stephengold.joltjni.BodyInterface
import com.github.stephengold.joltjni.BroadPhaseLayerFilter
import com.github.stephengold.joltjni.CastRayCollector
import com.github.stephengold.joltjni.CastShapeCollector
import com.github.stephengold.joltjni.ClosestHitCastRayCollector
import com.github.stephengold.joltjni.ClosestHitCastShapeCollector
import com.github.stephengold.joltjni.CollideShapeSettings
import com.github.stephengold.joltjni.IgnoreMultipleBodiesFilter
import com.github.stephengold.joltjni.Jolt
import com.github.stephengold.joltjni.ObjectLayerFilter
import com.github.stephengold.joltjni.RMat44
import com.github.stephengold.joltjni.RRayCast
import com.github.stephengold.joltjni.RShapeCast
import com.github.stephengold.joltjni.RVec3
import com.github.stephengold.joltjni.RayCastResult
import com.github.stephengold.joltjni.RayCastSettings
import com.github.stephengold.joltjni.ShapeCastResult
import com.github.stephengold.joltjni.ShapeCastSettings
import com.github.stephengold.joltjni.ShapeFilter
import com.github.stephengold.joltjni.SpecifiedObjectLayerFilter
import com.github.stephengold.joltjni.Vec3
import com.github.stephengold.joltjni.enumerate.EBackFaceMode
import com.github.stephengold.joltjni.readonly.ConstNarrowPhaseQuery
import com.github.stephengold.joltjni.readonly.ConstShape
import java.nio.FloatBuffer
import kotlin.math.sqrt

private const val FLOATS_PER_VEC3 = 3
private const val INITIAL_SENSOR_CAPACITY = 8
private const val MAX_FILTERED_SENSORS = 64

/**
 * Reusable narrow-phase query state and execution paths for [JoltPhysicsWorld].
 *
 * Keeps collectors, settings, filters, shape caches, and a direct [FloatBuffer] across calls so
 * steady-state `shapeCast`, `raycast`, and `overlapShape` queries avoid `JoltPhysicsObject` and
 * `java.lang.ref.Cleaner` allocations. Excludes both `ignore` and live sensors natively inside
 * Jolt via [IgnoreMultipleBodiesFilter] when possible, and releases all native scratch in [close].
 */
@Suppress("TooManyFunctions") // Fast-path and public-API fallback helpers for raycast, shapeCast, and overlapShape.
internal class JoltQueryRunner(
    private val bodyInterface: BodyInterface,
    private val narrowPhaseQuery: ConstNarrowPhaseQuery,
    private val shapeCastContacts: ShapeCastContactDecoder,
) : AutoCloseable {
    private val queryShapes = JoltQueryShapes()
    private val queryOrigin = RVec3()
    private val queryTransform = RMat44().apply { loadIdentity() }
    private val queryDirection = Vec3()
    private val unitScale = Vec3(1f, 1f, 1f)
    private val noBaseOffset = RVec3(0.0, 0.0, 0.0)
    private val queryFloatScratch: FloatBuffer = Jolt.newDirectFloatBuffer(FLOATS_PER_VEC3)

    // Initial overlaps (including stationary casts) need EPA's separating axis, not a zero axis
    // that becomes NaN when normalized. Triangle back faces block as well (#1030): a sweep let
    // through a one-sided mesh from behind leaves the shape inside it, where every later sweep
    // reports a hit at fraction 0.
    private val shapeCastSettings = ShapeCastSettings().apply {
        setReturnDeepestPoint(true)
        setBackFaceModeTriangles(EBackFaceMode.CollideWithBackFaces)
    }
    private val rayCastSettings = RayCastSettings()
    private val closestRayHit = ClosestHitCastRayCollector()
    private val allRayHits = AllHitCastRayCollector()
    private val closestCastHit = ClosestHitCastShapeCollector()
    private val allCastHits = AllHitCastShapeCollector()
    private var closestCastHitResult: ShapeCastResult? = null
    private var closestRayHitResult: RayCastResult? = null
    private val collideSettings = CollideShapeSettings()
    private val overlapHits = AllHitCollideShapeCollector()
    private val anyBroadPhaseLayer = BroadPhaseLayerFilter()
    private val anyObjectLayer = ObjectLayerFilter()
    private val anyBody = BodyFilter()
    private val sensorsFilter = IgnoreMultipleBodiesFilter()
    private var sensorsFilterVersion = -1
    private val ignoreBodyFilter = IgnoreMultipleBodiesFilter().apply { reserve(1) }
    private var ignoreFilterBodyId = Jolt.cInvalidBodyId
    private var ignoreFilterSensorsVersion = -1
    private val anyShape = ShapeFilter()
    private val layerFilters = arrayOfNulls<SpecifiedObjectLayerFilter>(CollisionLayers.MAX_LAYERS)

    // Live sensor IDs stored unboxed so queries can push them into a native IgnoreMultipleBodiesFilter
    // without allocating iterators or hitting sensors on the closest-hit collector path.
    private var sensorBodyIds = IntArray(INITIAL_SENSOR_CAPACITY)
    private var sensorCount = 0
    private var sensorsVersion = 0

    // overlapHits is read while overlapShape's callback runs, and that callback may query again.
    private var overlapping = false

    fun addSensor(id: Int) {
        if (sensorCount == sensorBodyIds.size) {
            sensorBodyIds = sensorBodyIds.copyOf(sensorBodyIds.size * 2)
        }
        sensorBodyIds[sensorCount++] = id
        sensorsVersion++
    }

    fun removeSensor(id: Int) {
        var found = false
        var index = 0
        while (index < sensorCount && !found) {
            if (sensorBodyIds[index] == id) {
                sensorCount--
                sensorBodyIds[index] = sensorBodyIds[sensorCount]
                sensorsVersion++
                found = true
            }
            index++
        }
    }

    fun raycast(
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
        val bodyFilter = bodyFilterForCast(ignore)
        val sensorsExcluded = sensorCount <= MAX_FILTERED_SENSORS
        val ops = JoltNativeQueryOps.bound
        return if (ops != null) {
            raycastNative(ops, origin, directionX, directionY, directionZ, maxDistance, onlyLayer, bodyFilter, sensorsExcluded, ignore)
        } else {
            raycastFallback(origin, directionX, directionY, directionZ, maxDistance, onlyLayer, bodyFilter, sensorsExcluded, ignore)
        }
    }

    @Suppress("LongParameterList") // Carries unboxed ray components and query filters without allocating a parameter object.
    private fun raycastNative(
        ops: JoltNativeQueryOps.Bound,
        origin: Vec3f,
        directionX: Float,
        directionY: Float,
        directionZ: Float,
        maxDistance: Float,
        onlyLayer: CollisionLayer?,
        bodyFilter: BodyFilter,
        sensorsExcluded: Boolean,
        ignore: BodyHandle?,
    ): RaycastHit? {
        val rayVa = ops.createRRayCast(
            origin.x.toDouble(),
            origin.y.toDouble(),
            origin.z.toDouble(),
            directionX * maxDistance,
            directionY * maxDistance,
            directionZ * maxDistance,
        )
        return try {
            val queryVa = narrowPhaseQuery.targetVa()
            val settingsVa = rayCastSettings.targetVa()
            val bplFilterVa = anyBroadPhaseLayer.va()
            val olFilterVa = objectLayerFilter(onlyLayer).va()
            val bodyFilterVa = bodyFilter.va()
            val shapeFilterVa = anyShape.va()

            closestRayHit.reset()
            ops.castRay(queryVa, rayVa, settingsVa, closestRayHit.va(), bplFilterVa, olFilterVa, bodyFilterVa, shapeFilterVa)

            var hitVa = if (closestRayHit.hadHit()) ops.closestCastRayHit(closestRayHit.va()) else 0L
            if (hitVa != 0L && !sensorsExcluded && !isReportable(ops.rayBodyId(hitVa), ignore)) {
                hitVa = nearestReportableRayHitVa(
                    ops, queryVa, rayVa, settingsVa, bplFilterVa, olFilterVa, bodyFilterVa, shapeFilterVa, ignore,
                )
            }
            if (hitVa == 0L) {
                null
            } else {
                val distance = maxDistance * ops.rayFraction(hitVa)
                RaycastHit(
                    BodyHandle(ops.rayBodyId(hitVa).toLong()),
                    Vec3f(origin.x + directionX * distance, origin.y + directionY * distance, origin.z + directionZ * distance),
                    distance,
                )
            }
        } finally {
            if (rayVa != 0L) ops.freeRRayCast(rayVa)
        }
    }

    @Suppress("LongParameterList") // Mirrors raycastNative without allocating a wrapper object.
    private fun nearestReportableRayHitVa(
        ops: JoltNativeQueryOps.Bound,
        queryVa: Long,
        rayVa: Long,
        settingsVa: Long,
        bplFilterVa: Long,
        olFilterVa: Long,
        bodyFilterVa: Long,
        shapeFilterVa: Long,
        ignore: BodyHandle?,
    ): Long {
        allRayHits.reset()
        val collectorVa = allRayHits.va()
        ops.castRay(queryVa, rayVa, settingsVa, collectorVa, bplFilterVa, olFilterVa, bodyFilterVa, shapeFilterVa)
        var nearestVa = 0L
        var nearestFraction = Float.POSITIVE_INFINITY
        for (index in 0 until allRayHits.countHits()) {
            val candidateVa = ops.allCastRayHit(collectorVa, index)
            if (!isReportable(ops.rayBodyId(candidateVa), ignore)) continue
            val fraction = ops.rayFraction(candidateVa)
            if (nearestVa == 0L || fraction < nearestFraction) {
                nearestVa = candidateVa
                nearestFraction = fraction
            }
        }
        return nearestVa
    }

    @Suppress("LongParameterList") // Public-API fallback mirroring raycastNative.
    private fun raycastFallback(
        origin: Vec3f,
        directionX: Float,
        directionY: Float,
        directionZ: Float,
        maxDistance: Float,
        onlyLayer: CollisionLayer?,
        bodyFilter: BodyFilter,
        sensorsExcluded: Boolean,
        ignore: BodyHandle?,
    ): RaycastHit? {
        queryOrigin.set(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble())
        queryDirection.set(directionX * maxDistance, directionY * maxDistance, directionZ * maxDistance)
        return RRayCast(queryOrigin, queryDirection).use { ray ->
            closestRayHit.reset()
            castRayInto(ray, onlyLayer, bodyFilter, closestRayHit)
            val solid = when {
                !closestRayHit.hadHit() -> null
                else -> {
                    val closest = closestRayHitResult ?: closestRayHit.hit.also { closestRayHitResult = it }
                    if (!sensorsExcluded && !isReportable(closest.bodyId, ignore)) {
                        nearestReportableRayHit(ray, onlyLayer, bodyFilter, ignore)
                    } else {
                        closest
                    }
                }
            }
            if (solid == null) {
                null
            } else {
                val distance = maxDistance * solid.fraction
                RaycastHit(
                    BodyHandle(solid.bodyId.toLong()),
                    Vec3f(origin.x + directionX * distance, origin.y + directionY * distance, origin.z + directionZ * distance),
                    distance,
                )
            }
        }
    }

    private fun nearestReportableRayHit(
        ray: RRayCast,
        onlyLayer: CollisionLayer?,
        bodyFilter: BodyFilter,
        ignore: BodyHandle?,
    ): RayCastResult? {
        allRayHits.reset()
        castRayInto(ray, onlyLayer, bodyFilter, allRayHits)
        var nearest: RayCastResult? = null
        for (index in 0 until allRayHits.countHits()) {
            val candidate = allRayHits.get(index)
            if (!isReportable(candidate.bodyId, ignore)) continue
            if (nearest == null || candidate.fraction < nearest.fraction) nearest = candidate
        }
        return nearest
    }

    private fun castRayInto(
        ray: RRayCast,
        onlyLayer: CollisionLayer?,
        bodyFilter: BodyFilter,
        collector: CastRayCollector,
    ) {
        narrowPhaseQuery.castRay(
            ray,
            rayCastSettings,
            collector,
            anyBroadPhaseLayer,
            objectLayerFilter(onlyLayer),
            bodyFilter,
            anyShape,
        )
    }

    fun shapeCast(
        shape: PhysicsShape,
        from: Vec3f,
        to: Vec3f,
        onlyLayer: CollisionLayer?,
        ignore: BodyHandle?,
    ): ShapeCastHit? {
        val joltShape = queryShapes.of(shape)
        queryOrigin.set(from.x.toDouble(), from.y.toDouble(), from.z.toDouble())
        queryTransform.setTranslation(queryOrigin)
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val bodyFilter = bodyFilterForCast(ignore)
        val sensorsExcluded = sensorCount <= MAX_FILTERED_SENSORS
        val ops = JoltNativeQueryOps.bound
        return if (ops != null) {
            shapeCastNative(ops, joltShape, dx, dy, dz, onlyLayer, bodyFilter, sensorsExcluded, ignore, from, to)
        } else {
            shapeCastFallback(joltShape, dx, dy, dz, onlyLayer, bodyFilter, sensorsExcluded, ignore, from, to)
        }
    }

    @Suppress("LongParameterList") // Carries unboxed sweep components and query filters without allocating a parameter object.
    private fun shapeCastNative(
        ops: JoltNativeQueryOps.Bound,
        joltShape: ConstShape,
        dx: Float,
        dy: Float,
        dz: Float,
        onlyLayer: CollisionLayer?,
        bodyFilter: BodyFilter,
        sensorsExcluded: Boolean,
        ignore: BodyHandle?,
        from: Vec3f,
        to: Vec3f,
    ): ShapeCastHit? {
        val castVa = ops.createRShapeCastNoBounds(
            joltShape.targetVa(),
            1f,
            1f,
            1f,
            queryTransform.targetVa(),
            dx,
            dy,
            dz,
        )
        return try {
            val queryVa = narrowPhaseQuery.targetVa()
            val settingsVa = shapeCastSettings.targetVa()
            val bplFilterVa = anyBroadPhaseLayer.va()
            val olFilterVa = objectLayerFilter(onlyLayer).va()
            val bodyFilterVa = bodyFilter.va()
            val shapeFilterVa = anyShape.va()

            closestCastHit.reset()
            ops.castShape(
                queryVa, castVa, settingsVa, 0.0, 0.0, 0.0,
                closestCastHit.va(), bplFilterVa, olFilterVa, bodyFilterVa, shapeFilterVa,
            )

            var hitVa = if (closestCastHit.hadHit()) ops.closestCastShapeHit(closestCastHit.va()) else 0L
            if (hitVa != 0L && !sensorsExcluded && !isReportable(ops.collideShapeBodyId2(hitVa), ignore)) {
                hitVa = nearestShapeCastHitVaExcluding(
                    ops, queryVa, castVa, settingsVa, bplFilterVa, olFilterVa, bodyFilterVa, shapeFilterVa, ignore,
                )
            }
            if (hitVa == 0L) null else decodeShapeCastHitVa(ops, hitVa, from, to)
        } finally {
            if (castVa != 0L) ops.freeRShapeCast(castVa)
        }
    }

    private fun decodeShapeCastHitVa(
        ops: JoltNativeQueryOps.Bound,
        hitVa: Long,
        from: Vec3f,
        to: Vec3f,
    ): ShapeCastHit {
        val bodyId = ops.collideShapeBodyId2(hitVa)
        ops.collideShapeContactPointOn2(hitVa, queryFloatScratch)
        val contactX = queryFloatScratch.get(0)
        val contactY = queryFloatScratch.get(1)
        val contactZ = queryFloatScratch.get(2)
        // Jolt reports the separating axis pointing INTO the hit surface and unnormalized;
        // ShapeCastContactDecoder negates and normalizes it into an outward contact normal.
        ops.collideShapePenetrationAxis(hitVa, queryFloatScratch)
        return shapeCastContacts.decode(
            handle = BodyHandle(bodyId.toLong()),
            point = Vec3f(contactX, contactY, contactZ),
            axisX = queryFloatScratch.get(0),
            axisY = queryFloatScratch.get(1),
            axisZ = queryFloatScratch.get(2),
            fraction = ops.shapeCastFraction(hitVa),
            from = from,
            to = to,
        )
    }

    @Suppress("LongParameterList") // Mirrors shapeCastNative without allocating a wrapper object.
    private fun nearestShapeCastHitVaExcluding(
        ops: JoltNativeQueryOps.Bound,
        queryVa: Long,
        castVa: Long,
        settingsVa: Long,
        bplFilterVa: Long,
        olFilterVa: Long,
        bodyFilterVa: Long,
        shapeFilterVa: Long,
        ignore: BodyHandle?,
    ): Long {
        allCastHits.reset()
        val collectorVa = allCastHits.va()
        ops.castShape(
            queryVa, castVa, settingsVa, 0.0, 0.0, 0.0,
            collectorVa, bplFilterVa, olFilterVa, bodyFilterVa, shapeFilterVa,
        )
        var nearestVa = 0L
        var nearestFraction = Float.POSITIVE_INFINITY
        for (index in 0 until allCastHits.countHits()) {
            val candidateVa = ops.allCastShapeHit(collectorVa, index)
            if (!isReportable(ops.collideShapeBodyId2(candidateVa), ignore)) continue
            val fraction = ops.shapeCastFraction(candidateVa)
            if (nearestVa == 0L || fraction < nearestFraction) {
                nearestVa = candidateVa
                nearestFraction = fraction
            }
        }
        return nearestVa
    }

    @Suppress("LongParameterList") // Public-API fallback mirroring shapeCastNative.
    private fun shapeCastFallback(
        joltShape: ConstShape,
        dx: Float,
        dy: Float,
        dz: Float,
        onlyLayer: CollisionLayer?,
        bodyFilter: BodyFilter,
        sensorsExcluded: Boolean,
        ignore: BodyHandle?,
        from: Vec3f,
        to: Vec3f,
    ): ShapeCastHit? {
        queryDirection.set(dx, dy, dz)
        return RShapeCast(joltShape, unitScale, queryTransform, queryDirection).use { cast ->
            closestCastHit.reset()
            castShapeInto(cast, onlyLayer, bodyFilter, closestCastHit)
            val hit = when {
                !closestCastHit.hadHit() -> null
                else -> {
                    val closest = closestCastHitResult ?: closestCastHit.hit.also { closestCastHitResult = it }
                    if (!sensorsExcluded && !isReportable(closest.bodyId2, ignore)) {
                        nearestShapeCastHitExcluding(cast, onlyLayer, bodyFilter, ignore)
                    } else {
                        closest
                    }
                }
            }
            if (hit == null) {
                null
            } else {
                val contact = hit.contactPointOn2
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
    }

    private fun nearestShapeCastHitExcluding(
        cast: RShapeCast,
        onlyLayer: CollisionLayer?,
        bodyFilter: BodyFilter,
        ignore: BodyHandle?,
    ): ShapeCastResult? {
        allCastHits.reset()
        castShapeInto(cast, onlyLayer, bodyFilter, allCastHits)
        var nearest: ShapeCastResult? = null
        for (index in 0 until allCastHits.countHits()) {
            val candidate = allCastHits.get(index)
            if (!isReportable(candidate.bodyId2, ignore)) continue
            if (nearest == null || candidate.fraction < nearest.fraction) nearest = candidate
        }
        return nearest
    }

    private fun castShapeInto(
        cast: RShapeCast,
        onlyLayer: CollisionLayer?,
        bodyFilter: BodyFilter,
        collector: CastShapeCollector,
    ) {
        narrowPhaseQuery.castShape(
            cast,
            shapeCastSettings,
            noBaseOffset,
            collector,
            anyBroadPhaseLayer,
            objectLayerFilter(onlyLayer),
            bodyFilter,
            anyShape,
        )
    }

    fun overlapShape(
        shape: PhysicsShape,
        position: Vec3f,
        onlyLayer: CollisionLayer?,
        onOverlap: (BodyHandle) -> Unit,
    ) {
        // The callback may overlap again; the collector it is reading from then has to be another.
        val reuse = !overlapping
        val collector = if (reuse) overlapHits.also { it.reset() } else AllHitCollideShapeCollector()
        try {
            queryOrigin.set(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
            queryTransform.setTranslation(queryOrigin)
            narrowPhaseQuery.collideShape(
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
            if (reuse) overlapping = true
            val hitCount = collector.countHits()
            val ops = JoltNativeQueryOps.bound
            if (ops != null) {
                val collectorVa = collector.va()
                for (index in 0 until hitCount) {
                    val bodyId = ops.collideShapeBodyId2(ops.allCollideShapeHit(collectorVa, index))
                    onOverlap(BodyHandle(bodyId.toLong()))
                }
            } else {
                for (index in 0 until hitCount) {
                    onOverlap(BodyHandle(collector.get(index).bodyId2.toLong()))
                }
            }
        } finally {
            if (reuse) overlapping = false else collector.close()
        }
    }

    private fun isReportable(bodyId: Int, ignore: BodyHandle?): Boolean =
        bodyId != ignore?.id?.toInt() && !bodyInterface.isSensor(bodyId)

    /**
     * Native `BodyFilter` for [shapeCast] and [raycast], excluding [ignore] and up to
     * [MAX_FILTERED_SENSORS] live sensors inside Jolt's narrow phase so the closest-hit collector
     * finds the nearest solid body on the first pass.
     */
    private fun bodyFilterForCast(ignore: BodyHandle?): BodyFilter = when {
        ignore == null && (sensorCount == 0 || sensorCount > MAX_FILTERED_SENSORS) -> anyBody
        ignore == null -> {
            if (sensorsFilterVersion != sensorsVersion) {
                sensorsFilter.clear()
                sensorsFilter.reserve(sensorCount)
                for (index in 0 until sensorCount) {
                    sensorsFilter.ignoreBody(sensorBodyIds[index])
                }
                sensorsFilterVersion = sensorsVersion
            }
            sensorsFilter
        }
        else -> {
            val ignoreId = ignore.id.toInt()
            if (ignoreFilterBodyId != ignoreId || ignoreFilterSensorsVersion != sensorsVersion) {
                ignoreBodyFilter.clear()
                val includeSensors = sensorCount in 1..MAX_FILTERED_SENSORS
                ignoreBodyFilter.reserve(if (includeSensors) sensorCount + 1 else 1)
                if (includeSensors) {
                    for (index in 0 until sensorCount) {
                        ignoreBodyFilter.ignoreBody(sensorBodyIds[index])
                    }
                }
                ignoreBodyFilter.ignoreBody(ignoreId)
                ignoreFilterBodyId = ignoreId
                ignoreFilterSensorsVersion = sensorsVersion
            }
            ignoreBodyFilter
        }
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

    override fun close() {
        sensorCount = 0
        sensorsVersion++
        closestCastHitResult = null
        closestRayHitResult = null
        queryShapes.clear()
        listOf(
            queryTransform, shapeCastSettings, closestCastHit, allCastHits, collideSettings, overlapHits,
            rayCastSettings, closestRayHit, allRayHits,
            anyBroadPhaseLayer, anyObjectLayer, anyBody, sensorsFilter, ignoreBodyFilter, anyShape,
        ).forEach { it.close() }
        layerFilters.forEach { it?.close() }
    }
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
