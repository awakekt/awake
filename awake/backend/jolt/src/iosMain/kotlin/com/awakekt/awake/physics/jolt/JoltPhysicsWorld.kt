/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalForeignApi::class)

@file:Suppress("TooManyFunctions") // JoltC's callback tables need one top-level staticCFunction per slot.

package com.awakekt.awake.physics.jolt

import cnames.structs.JPC_BodyInterface
import cnames.structs.JPC_JobSystem
import cnames.structs.JPC_PhysicsSystem
import cnames.structs.JPC_Shape
import cnames.structs.JPC_String
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BallSocketConstraint
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.Buoyancy
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.Constraint
import com.awakekt.awake.physics.ConstraintHandle
import com.awakekt.awake.physics.ContactEvent
import com.awakekt.awake.physics.ConvexHullShape
import com.awakekt.awake.physics.DistanceConstraint
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.HingeConstraint
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsCapabilityException
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.RaycastHit
import com.awakekt.awake.physics.ShapeCastHit
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.physics.requireCanBeSensor
import com.awakekt.awake.physics.toMeshShape
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.joltc.JPC_ACTIVATION_ACTIVATE
import platform.joltc.JPC_ACTIVATION_DONT_ACTIVATE
import platform.joltc.JPC_Activation
import platform.joltc.JPC_BodyCreationSettings
import platform.joltc.JPC_BodyCreationSettings_default
import platform.joltc.JPC_BodyFilterFns
import platform.joltc.JPC_BodyFilter_delete
import platform.joltc.JPC_BodyFilter_new
import platform.joltc.JPC_BodyID
import platform.joltc.JPC_BodyInterface_ActivateBody
import platform.joltc.JPC_BodyInterface_AddBody
import platform.joltc.JPC_BodyInterface_AddImpulse
import platform.joltc.JPC_BodyInterface_CreateBody
import platform.joltc.JPC_BodyInterface_DeactivateBody
import platform.joltc.JPC_BodyInterface_DestroyBody
import platform.joltc.JPC_BodyInterface_GetLinearVelocity
import platform.joltc.JPC_BodyInterface_GetPositionAndRotation
import platform.joltc.JPC_BodyInterface_IsActive
import platform.joltc.JPC_BodyInterface_MoveKinematic
import platform.joltc.JPC_BodyInterface_RemoveBody
import platform.joltc.JPC_BodyInterface_SetAngularVelocity
import platform.joltc.JPC_BodyInterface_SetLinearVelocity
import platform.joltc.JPC_BodyInterface_SetMotionQuality
import platform.joltc.JPC_BodyInterface_SetPositionAndRotation
import platform.joltc.JPC_BodyLockWrite_GetBody
import platform.joltc.JPC_BodyLockWrite_Succeeded
import platform.joltc.JPC_BodyLockWrite_delete
import platform.joltc.JPC_BodyLockWrite_new
import platform.joltc.JPC_Body_ApplyBuoyancyImpulse
import platform.joltc.JPC_Body_GetID
import platform.joltc.JPC_Body_GetMotionType
import platform.joltc.JPC_Body_IsSensor
import platform.joltc.JPC_BoxShapeSettings
import platform.joltc.JPC_BoxShapeSettings_Create
import platform.joltc.JPC_BoxShapeSettings_default
import platform.joltc.JPC_BroadPhaseLayer
import platform.joltc.JPC_BroadPhaseLayerInterfaceFns
import platform.joltc.JPC_BroadPhaseLayerInterface_delete
import platform.joltc.JPC_BroadPhaseLayerInterface_new
import platform.joltc.JPC_CapsuleShapeSettings
import platform.joltc.JPC_CapsuleShapeSettings_Create
import platform.joltc.JPC_CapsuleShapeSettings_default
import platform.joltc.JPC_CastShapeCollectorFns
import platform.joltc.JPC_CastShapeCollector_UpdateEarlyOutFraction
import platform.joltc.JPC_CastShapeCollector_delete
import platform.joltc.JPC_CastShapeCollector_new
import platform.joltc.JPC_CollideShapeCollectorFns
import platform.joltc.JPC_CollideShapeCollector_delete
import platform.joltc.JPC_CollideShapeCollector_new
import platform.joltc.JPC_CollideShapeResult
import platform.joltc.JPC_CollideShapeSettings_default
import platform.joltc.JPC_ConstraintSpace
import platform.joltc.JPC_ContactListenerFns
import platform.joltc.JPC_ContactListener_delete
import platform.joltc.JPC_ContactListener_new
import platform.joltc.JPC_ConvexHullShapeSettings
import platform.joltc.JPC_ConvexHullShapeSettings_Create
import platform.joltc.JPC_ConvexHullShapeSettings_default
import platform.joltc.JPC_DistanceConstraintSettings
import platform.joltc.JPC_DistanceConstraintSettings_Create
import platform.joltc.JPC_DistanceConstraintSettings_default
import platform.joltc.JPC_FactoryInit
import platform.joltc.JPC_Float3
import platform.joltc.JPC_HingeConstraintSettings
import platform.joltc.JPC_HingeConstraintSettings_Create
import platform.joltc.JPC_HingeConstraintSettings_default
import platform.joltc.JPC_IndexedTriangle
import platform.joltc.JPC_JobSystemThreadPool_delete
import platform.joltc.JPC_JobSystemThreadPool_new2
import platform.joltc.JPC_MAX_PHYSICS_BARRIERS
import platform.joltc.JPC_MAX_PHYSICS_JOBS
import platform.joltc.JPC_MeshShapeSettings
import platform.joltc.JPC_MeshShapeSettings_Create
import platform.joltc.JPC_MeshShapeSettings_default
import platform.joltc.JPC_MotionQuality
import platform.joltc.JPC_MotionType
import platform.joltc.JPC_NarrowPhaseQuery_CastRay
import platform.joltc.JPC_NarrowPhaseQuery_CastRayArgs
import platform.joltc.JPC_NarrowPhaseQuery_CastShape
import platform.joltc.JPC_NarrowPhaseQuery_CastShapeArgs
import platform.joltc.JPC_NarrowPhaseQuery_CollideShape
import platform.joltc.JPC_NarrowPhaseQuery_CollideShapeArgs
import platform.joltc.JPC_ObjectLayer
import platform.joltc.JPC_ObjectLayerFilterFns
import platform.joltc.JPC_ObjectLayerFilter_delete
import platform.joltc.JPC_ObjectLayerFilter_new
import platform.joltc.JPC_ObjectLayerPairFilterFns
import platform.joltc.JPC_ObjectLayerPairFilter_delete
import platform.joltc.JPC_ObjectLayerPairFilter_new
import platform.joltc.JPC_ObjectVsBroadPhaseLayerFilterFns
import platform.joltc.JPC_ObjectVsBroadPhaseLayerFilter_delete
import platform.joltc.JPC_ObjectVsBroadPhaseLayerFilter_new
import platform.joltc.JPC_PhysicsSystem_AddConstraint
import platform.joltc.JPC_PhysicsSystem_GetBodyInterface
import platform.joltc.JPC_PhysicsSystem_GetBodyLockInterface
import platform.joltc.JPC_PhysicsSystem_GetGravity
import platform.joltc.JPC_PhysicsSystem_GetNarrowPhaseQuery
import platform.joltc.JPC_PhysicsSystem_Init
import platform.joltc.JPC_PhysicsSystem_RemoveConstraint
import platform.joltc.JPC_PhysicsSystem_SetContactListener
import platform.joltc.JPC_PhysicsSystem_SetGravity
import platform.joltc.JPC_PhysicsSystem_Update
import platform.joltc.JPC_PhysicsSystem_delete
import platform.joltc.JPC_PhysicsSystem_new
import platform.joltc.JPC_Quat
import platform.joltc.JPC_RVec3
import platform.joltc.JPC_RegisterDefaultAllocator
import platform.joltc.JPC_RegisterTypes
import platform.joltc.JPC_ShapeCastResult
import platform.joltc.JPC_ShapeCastSettings_default
import platform.joltc.JPC_SixDOFConstraintSettings
import platform.joltc.JPC_SixDOFConstraintSettings_Create
import platform.joltc.JPC_SixDOFConstraintSettings_default
import platform.joltc.JPC_SphereShapeSettings
import platform.joltc.JPC_SphereShapeSettings_Create
import platform.joltc.JPC_SphereShapeSettings_default
import platform.joltc.JPC_String_c_str
import platform.joltc.JPC_String_delete
import platform.joltc.JPC_TempAllocatorImpl_delete
import platform.joltc.JPC_TempAllocatorImpl_new
import platform.joltc.JPC_Vec3
import kotlin.math.sqrt

// Jolt Physics integration slice 2 (see docs/reference/decision-log.md): only 2 object
// layers, matching JoltC's own HelloWorld example (mirrors the desktop/Android jolt-jni
// backend's own scoping, see that class's own doc comment) -- fine-grained per-game layer
// authoring is out of scope for this slice.
private const val NUM_BROADPHASE_LAYERS: UInt = 2u
private const val BROADPHASE_LAYER_STATIC: JPC_BroadPhaseLayer = 0u
private const val BROADPHASE_LAYER_MOVING: JPC_BroadPhaseLayer = 1u
private const val MAX_BODIES = 5_000u
private const val NUM_BODY_MUTEXES = 0u
private const val MAX_BODY_PAIRS = 65_536u
private const val MAX_CONTACTS = 20_480u

/**
 * Scratch memory for one `Update`, and the one number on this backend that aborts the process if
 * it is wrong.
 *
 * `TempAllocatorImpl` is a linear allocator over a fixed block: overrunning it does not fall back
 * to malloc, it calls `abort()`. And Jolt sizes its per-step arrays from the *configured maxima*
 * rather than from the bodies actually present, so a world with one box in it needs as much of this
 * as a full one -- [MAX_CONTACTS] contact constraints dominate, at a few hundred bytes each.
 *
 * The 10 MB this used to be was under that, so **every step aborted**, from the first one, in a
 * scene with a single body. It went unnoticed because nothing ran this backend: it compiled, and
 * compiling proves nothing about a temp allocator. The desktop and Android backends never had the
 * problem because jolt-jni's `TempAllocatorMalloc` has no fixed size to run out of.
 */
private const val TEMP_ALLOCATOR_BYTES = 33_554_432u // 32 MiB

// JoltC's callback tables need plain top-level `staticCFunction`s, which cannot capture. The
// world's layers reach them through the `self` pointer each `_new` takes -- a StableRef, the same
// route the shape-cast collector uses -- so the tables below are the world's own matrix rather
// than a hardcoded scheme.
@OptIn(ExperimentalForeignApi::class)
private fun layersFrom(self: COpaquePointer?): CollisionLayers? =
    self?.asStableRef<CollisionLayers>()?.get()

@OptIn(ExperimentalForeignApi::class)
private fun getNumBroadPhaseLayers(self: COpaquePointer?): UInt = NUM_BROADPHASE_LAYERS

@OptIn(ExperimentalForeignApi::class)
private fun getBroadPhaseLayer(
    self: COpaquePointer?,
    layer: JPC_ObjectLayer,
): JPC_BroadPhaseLayer {
    val layers = layersFrom(self) ?: return BROADPHASE_LAYER_STATIC
    return if (layers.isMoving(CollisionLayer(layer.toInt()))) {
        BROADPHASE_LAYER_MOVING
    } else {
        BROADPHASE_LAYER_STATIC
    }
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("FunctionOnlyReturningConstant") // C callback slot -- Jolt's vtable needs a function.
private fun objectVsBroadPhaseShouldCollide(
    self: COpaquePointer?,
    objectLayer: JPC_ObjectLayer,
    broadPhaseLayer: JPC_BroadPhaseLayer,
): Boolean = true

@OptIn(ExperimentalForeignApi::class)
private fun objectLayerPairShouldCollide(
    self: COpaquePointer?,
    layer1: JPC_ObjectLayer,
    layer2: JPC_ObjectLayer,
): Boolean {
    val layers = layersFrom(self) ?: return true
    return layers.collides(CollisionLayer(layer1.toInt()), CollisionLayer(layer2.toInt()))
}

/** Backs [PhysicsWorld.shapeCast]'s `onlyLayer`: everything outside the one layer is skipped. */
@OptIn(ExperimentalForeignApi::class)
private fun singleLayerShouldCollide(self: COpaquePointer?, layer: JPC_ObjectLayer): Boolean {
    val only = self?.asStableRef<CollisionLayer>()?.get() ?: return true
    return only.index == layer.toInt()
}

// Function-parameter Vec3/Quat cinterop as by-value CValue ([vec3Value]/[quatValue]), but
// nested struct fields (e.g. JPC_BodyCreationSettings.Position) expose only a read-only view
// with no CValue setter, so those need field-by-field writes ([JPC_Vec3.write]/[JPC_Quat.write]).
@OptIn(ExperimentalForeignApi::class)
private fun vec3Value(v: Vec3f): CValue<JPC_Vec3> = cValue {
    x = v.x
    y = v.y
    z = v.z
}

@OptIn(ExperimentalForeignApi::class)
private fun quatValue(w: Float, x: Float, y: Float, z: Float): CValue<JPC_Quat> = cValue {
    this.w = w
    this.x = x
    this.y = y
    this.z = z
}

@OptIn(ExperimentalForeignApi::class)
private fun JPC_Vec3.write(v: Vec3f) {
    x = v.x
    y = v.y
    z = v.z
}

@OptIn(ExperimentalForeignApi::class)
private fun JPC_Quat.write(w: Float, x: Float, y: Float, z: Float) {
    this.w = w
    this.x = x
    this.y = y
    this.z = z
}

/** [JPC_String] is JoltC's owned error-message type (see JoltC/Functions.h's `JPC_String_c_str`/
 * `JPC_String_delete`) -- callers of a `*_Create` function that fails must free the returned
 * error string themselves once they're done reading it. */
@OptIn(ExperimentalForeignApi::class)
private fun joltErrorMessage(error: CPointer<JPC_String>?): String {
    if (error == null) return "(no error message)"
    val message = JPC_String_c_str(error)?.toKString() ?: "(null error message)"
    JPC_String_delete(error)
    return message
}

/**
 * This target's JoltC shape for a convex [PhysicsShape].
 *
 * Shared by body creation and [PhysicsWorld.shapeCast]: a character swept with a different shape
 * from the one it collides with is a bug that reads as bad tuning.
 */
@OptIn(ExperimentalForeignApi::class)
private fun MemScope.convexShape(shape: PhysicsShape): CPointer<JPC_Shape> = when (shape) {
    is BoxShape -> boxShape(shape)
    is SphereShape -> sphereShape(shape)
    is CapsuleShape -> capsuleShape(shape)
    is ConvexHullShape -> hullShape(shape)
    is MeshShape -> meshShape(shape)
    // JoltC wraps no heightfield at all, so this target collides with the same surface expressed
    // as triangles. A heightfield is a compressed grid of them, so what is lost is the compression
    // and the grid-aware queries, not the collision -- see toMeshShape for what that costs.
    is HeightFieldShape -> meshShape(shape.toMeshShape())
}

/** Every `*_Create` here follows the same shape: fill settings, create, or report why not. */
@OptIn(ExperimentalForeignApi::class)
private fun MemScope.createdShape(
    what: String,
    create: (CPointer<CPointerVar<JPC_Shape>>, CPointer<CPointerVar<JPC_String>>) -> Boolean,
): CPointer<JPC_Shape> {
    val outShape = alloc<CPointerVar<JPC_Shape>>()
    val outError = alloc<CPointerVar<JPC_String>>()
    check(create(outShape.ptr, outError.ptr)) {
        "${what}_Create failed: ${joltErrorMessage(outError.value)}"
    }
    return outShape.value ?: error("${what}_Create returned null shape")
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.boxShape(shape: BoxShape): CPointer<JPC_Shape> {
    val settings = alloc<JPC_BoxShapeSettings>()
    JPC_BoxShapeSettings_default(settings.ptr)
    settings.HalfExtent.write(shape.halfExtents)
    return createdShape("JPC_BoxShapeSettings") { out, err ->
        JPC_BoxShapeSettings_Create(settings.ptr, out, err)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.sphereShape(shape: SphereShape): CPointer<JPC_Shape> {
    val settings = alloc<JPC_SphereShapeSettings>()
    JPC_SphereShapeSettings_default(settings.ptr)
    settings.Radius = shape.radius
    return createdShape("JPC_SphereShapeSettings") { out, err ->
        JPC_SphereShapeSettings_Create(settings.ptr, out, err)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.capsuleShape(shape: CapsuleShape): CPointer<JPC_Shape> {
    val settings = alloc<JPC_CapsuleShapeSettings>()
    JPC_CapsuleShapeSettings_default(settings.ptr)
    settings.Radius = shape.radius
    settings.HalfHeightOfCylinder = shape.halfHeight
    return createdShape("JPC_CapsuleShapeSettings") { out, err ->
        JPC_CapsuleShapeSettings_Create(settings.ptr, out, err)
    }
}

/** JoltC takes a pointer to a contiguous array, so the points are laid out in scope-owned memory. */
@OptIn(ExperimentalForeignApi::class)
private fun MemScope.hullShape(shape: ConvexHullShape): CPointer<JPC_Shape> {
    val settings = alloc<JPC_ConvexHullShapeSettings>()
    JPC_ConvexHullShapeSettings_default(settings.ptr)
    val points = allocArray<JPC_Vec3>(shape.pointCount)
    for (index in 0 until shape.pointCount) {
        val base = index * VALUES_PER_POINT
        points[index].x = shape.points[base]
        points[index].y = shape.points[base + 1]
        points[index].z = shape.points[base + 2]
    }
    settings.Points = points
    settings.PointsLen = shape.pointCount.convert()
    return createdShape("JPC_ConvexHullShapeSettings") { out, err ->
        JPC_ConvexHullShapeSettings_Create(settings.ptr, out, err)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.meshShape(shape: MeshShape): CPointer<JPC_Shape> {
    val settings = alloc<JPC_MeshShapeSettings>()
    JPC_MeshShapeSettings_default(settings.ptr)
    val vertexCount = shape.vertices.size / VALUES_PER_POINT
    val vertices = allocArray<JPC_Float3>(vertexCount)
    for (index in 0 until vertexCount) {
        val base = index * VALUES_PER_POINT
        vertices[index].x = shape.vertices[base]
        vertices[index].y = shape.vertices[base + 1]
        vertices[index].z = shape.vertices[base + 2]
    }
    val triangles = allocArray<JPC_IndexedTriangle>(shape.triangleCount)
    for (index in 0 until shape.triangleCount) {
        val base = index * VALUES_PER_POINT
        triangles[index].idx[0] = shape.indices[base].convert()
        triangles[index].idx[1] = shape.indices[base + 1].convert()
        triangles[index].idx[2] = shape.indices[base + 2].convert()
    }
    settings.TriangleVertices = vertices
    settings.TriangleVerticesLen = vertexCount.convert()
    settings.IndexedTriangles = triangles
    settings.IndexedTrianglesLen = shape.triangleCount.convert()
    return createdShape("JPC_MeshShapeSettings") { out, err ->
        JPC_MeshShapeSettings_Create(settings.ptr, out, err)
    }
}

/** x, y, z -- the stride of every point, vertex and triangle index array above. */
private const val VALUES_PER_POINT = 3

/**
 * Where a shape cast's best hit is kept while JoltC walks the candidates.
 *
 * JoltC ships no closest-hit collector -- jolt-jni has `ClosestHitCastShapeCollector` and
 * JoltPhysics.js has `CastShapeClosestHitCollisionCollector`, and this binding has neither, only
 * the raw `JPC_CastShapeCollector_new(self, fns)` hook. So the collector is built here: a
 * `staticCFunction` cannot capture, and the `self` pointer is how state reaches it -- a
 * [kotlinx.cinterop.StableRef] to one of these, freed by the caller that made it.
 */
/** Collects every overlapping body id; see [PhysicsWorld.overlapShape] for why it is all of them. */
private class OverlapHits {
    val bodyIds = mutableListOf<JPC_BodyID>()
}

@OptIn(ExperimentalForeignApi::class)
private fun overlapReset(self: COpaquePointer?) {
    self?.asStableRef<OverlapHits>()?.get()?.bodyIds?.clear()
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("UnusedParameter") // Signature fixed by the C callback slot; an overlap never early-outs.
private fun overlapAddHit(
    self: COpaquePointer?,
    base: CPointer<cnames.structs.JPC_CollideShapeCollector>?,
    result: CPointer<JPC_CollideShapeResult>?,
) {
    val hits = self?.asStableRef<OverlapHits>()?.get() ?: return
    val hit = result?.pointed ?: return
    // No early-out call, unlike the cast collector: stopping at the nearest is exactly what an
    // overlap must not do.
    hits.bodyIds += hit.BodyID2
}

/**
 * The optional layer and body filters a query runs with, and the JoltC handles behind them.
 *
 * JoltC has no `SpecifiedObjectLayerFilter` or ignore-one-body filter, so both are built from the
 * same callback-plus-`StableRef` shape the collectors use -- and both have to be freed afterwards,
 * which is the only reason this is a type rather than two locals.
 */
@OptIn(ExperimentalForeignApi::class)
private class QueryFilters(
    val layerRef: StableRef<CollisionLayer>?,
    val layerFilter: CPointer<cnames.structs.JPC_ObjectLayerFilter>?,
    val bodyRef: StableRef<IgnoredBody>?,
    val bodyFilter: CPointer<cnames.structs.JPC_BodyFilter>?,
) {
    fun dispose() {
        layerRef?.dispose()
        layerFilter?.let(::JPC_ObjectLayerFilter_delete)
        bodyRef?.dispose()
        bodyFilter?.let(::JPC_BodyFilter_delete)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.queryFilters(onlyLayer: CollisionLayer?, ignore: BodyHandle?): QueryFilters {
    val layerRef = onlyLayer?.let { StableRef.create(it) }
    val layerFilter = layerRef?.let { ref ->
        val fns = alloc<JPC_ObjectLayerFilterFns>().apply {
            ShouldCollide = staticCFunction(::singleLayerShouldCollide)
        }
        JPC_ObjectLayerFilter_new(ref.asCPointer(), fns.readValue())
    }
    // Always built, even with nothing to ignore: it is also what keeps sensors out of the result.
    val bodyRef = ignore?.let { StableRef.create(IgnoredBody(it.id.toUInt())) }
    val fns = alloc<JPC_BodyFilterFns>().apply {
        ShouldCollide = if (bodyRef == null) {
            staticCFunction(::anyBodyShouldCollide)
        } else {
            staticCFunction(::ignoredBodyShouldCollide)
        }
        ShouldCollideLocked = staticCFunction(::solidOnlyShouldCollideLocked)
    }
    val bodyFilter = JPC_BodyFilter_new(bodyRef?.asCPointer(), fns.readValue())
    return QueryFilters(layerRef, layerFilter, bodyRef, bodyFilter)
}

/** The body a sweep must pretend is not there; see [PhysicsWorld.shapeCast]'s `ignore`. */
private class IgnoredBody(val id: JPC_BodyID)

@OptIn(ExperimentalForeignApi::class)
private fun ignoredBodyShouldCollide(self: COpaquePointer?, bodyId: JPC_BodyID): Boolean =
    self?.asStableRef<IgnoredBody>()?.get()?.id != bodyId

@OptIn(ExperimentalForeignApi::class)
@Suppress("UnusedParameter", "FunctionOnlyReturningConstant") // C slot; the locked form decides.
private fun anyBodyShouldCollide(self: COpaquePointer?, bodyId: JPC_BodyID): Boolean = true

/**
 * Rejects sensors, whatever the query.
 *
 * The two halves of a body filter split cleanly here: the id form knows which body the caller asked
 * to ignore, and this one has the body itself, which is what it takes to ask whether it is solid.
 * A sensor is not, and reporting one as an obstruction turns every trigger volume into an invisible
 * wall.
 */
@OptIn(ExperimentalForeignApi::class)
@Suppress("UnusedParameter") // Signature fixed by the C callback slot.
private fun solidOnlyShouldCollideLocked(
    self: COpaquePointer?,
    body: CPointer<cnames.structs.JPC_Body>?,
): Boolean = body?.let { !JPC_Body_IsSensor(it) } ?: true

private class ClosestShapeCastHit {
    var hasHit = false
    var fraction = 0f
    var bodyId: JPC_BodyID = 0u
    var pointX = 0f
    var pointY = 0f
    var pointZ = 0f
    var normalX = 0f
    var normalY = 0f
    var normalZ = 0f
}

@OptIn(ExperimentalForeignApi::class)
private fun shapeCastReset(self: COpaquePointer?) {
    self?.asStableRef<ClosestShapeCastHit>()?.get()?.hasHit = false
}

@OptIn(ExperimentalForeignApi::class)
private fun shapeCastAddHit(
    self: COpaquePointer?,
    base: CPointer<cnames.structs.JPC_CastShapeCollector>?,
    result: CPointer<JPC_ShapeCastResult>?,
) {
    val best = self?.asStableRef<ClosestShapeCastHit>()?.get() ?: return
    val hit = result?.pointed ?: return
    if (!best.hasHit || hit.Fraction < best.fraction) {
        record(best, hit)
        // Tells Jolt to stop considering anything farther away, which is what makes this a
        // closest-hit collector rather than a list that happens to be filtered afterwards.
        JPC_CastShapeCollector_UpdateEarlyOutFraction(base, hit.Fraction)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun record(best: ClosestShapeCastHit, hit: JPC_ShapeCastResult) {
    best.hasHit = true
    best.fraction = hit.Fraction
    best.bodyId = hit.BodyID2
    best.pointX = hit.ContactPointOn2.x
    best.pointY = hit.ContactPointOn2.y
    best.pointZ = hit.ContactPointOn2.z
    // PenetrationAxis points into the surface and is not unit length; a contact normal points
    // back out of it. Same conversion the other three backends do, by hand here.
    val axisX = hit.PenetrationAxis.x
    val axisY = hit.PenetrationAxis.y
    val axisZ = hit.PenetrationAxis.z
    val length = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
    if (length > 0f) {
        best.normalX = -axisX / length
        best.normalY = -axisY / length
        best.normalZ = -axisZ / length
    }
}

/**
 * JoltC-backed (cinterop, `SecondHalfGames/JoltC` -- see this module's `build.gradle.kts` for
 * how the vendored `ios-native/JoltC` submodule is built into a static lib and linked)
 * [PhysicsWorld] -- iOS only. Mirrors the desktop/Android `jolt-jni` backend's own semantics
 * (gravity default, body-creation/tracked-body-list/syncTransforms/raycast/destroy shape) as
 * closely as JoltC's C API allows; see that class's doc comment for the parts of this design
 * this class intentionally duplicates rather than shares (no common intermediate layer exists
 * since jolt-jni and JoltC share zero code).
 */
@OptIn(ExperimentalForeignApi::class)
class JoltPhysicsWorld(
    gravity: Vec3f = Vec3f(0f, -9.81f, 0f),
    override val layers: CollisionLayers = CollisionLayers.Default,
) : PhysicsWorld {
    /**
     * The world's layers, reachable from callbacks that cannot capture.
     *
     * Held for the world's lifetime and disposed in [destroy]: JoltC keeps the raw pointer inside
     * its filter tables, so freeing this earlier would leave Jolt reading freed memory on its next
     * broadphase pass.
     */
    private val layersRef = StableRef.create(layers)

    // Readback scratch, reused across every visited body -- see PhysicsWorld's own note.
    private val scratchOutPosition = Vec3f()
    private val scratchOutRotation = Quat()
    private companion object {
        // Matches HelloWorld's own initialization order: allocator, factory, types, exactly
        // once per process. A `private companion object { init { } }` runs before any
        // instance member here, same ordering guarantee the desktop backend's own
        // `JoltNative.ensureLoaded()` companion-object trick relies on.
        init {
            JPC_RegisterDefaultAllocator()
            JPC_FactoryInit()
            JPC_RegisterTypes()
        }
    }

    private val tempAllocator = JPC_TempAllocatorImpl_new(TEMP_ALLOCATOR_BYTES)
        ?: error("JPC_TempAllocatorImpl_new failed")
    private val jobSystem = JPC_JobSystemThreadPool_new2(
        JPC_MAX_PHYSICS_JOBS.toUInt(),
        JPC_MAX_PHYSICS_BARRIERS.toUInt(),
    )
        ?: error("JPC_JobSystemThreadPool_new2 failed")

    private val broadPhaseLayerInterfaceFns = cValue<JPC_BroadPhaseLayerInterfaceFns> {
        GetNumBroadPhaseLayers = staticCFunction(::getNumBroadPhaseLayers)
        GetBroadPhaseLayer = staticCFunction(::getBroadPhaseLayer)
    }
    private val broadPhaseLayerInterface =
        JPC_BroadPhaseLayerInterface_new(layersRef.asCPointer(), broadPhaseLayerInterfaceFns)
            ?: error("JPC_BroadPhaseLayerInterface_new failed")

    private val objectVsBroadPhaseLayerFilterFns = cValue<JPC_ObjectVsBroadPhaseLayerFilterFns> {
        ShouldCollide = staticCFunction(::objectVsBroadPhaseShouldCollide)
    }
    private val objectVsBroadPhaseLayerFilter =
        JPC_ObjectVsBroadPhaseLayerFilter_new(null, objectVsBroadPhaseLayerFilterFns)
            ?: error("JPC_ObjectVsBroadPhaseLayerFilter_new failed")

    private val objectLayerPairFilterFns = cValue<JPC_ObjectLayerPairFilterFns> {
        ShouldCollide = staticCFunction(::objectLayerPairShouldCollide)
    }
    private val objectLayerPairFilter =
        JPC_ObjectLayerPairFilter_new(layersRef.asCPointer(), objectLayerPairFilterFns)
            ?: error("JPC_ObjectLayerPairFilter_new failed")

    private val physicsSystem: CPointer<JPC_PhysicsSystem> =
        JPC_PhysicsSystem_new() ?: error("JPC_PhysicsSystem_new failed")

    private val bodyInterface: CPointer<JPC_BodyInterface>

    // Every body this PhysicsWorld created, so syncTransforms() knows what to read back and
    // destroy() knows what to tear down -- same reasoning as the desktop backend's own
    // `trackedBodyIds` (JoltC exposes no cheap "iterate my bodies" call either).
    private val trackedBodyIds = mutableListOf<JPC_BodyID>()

    /** Live constraints and the bodies each joins; see [destroyBody] for why the pairing is kept. */
    // LinkedHashMap and LinkedHashSet, not the hash forms: teardown iterates these, and
    // destruction order decides which body ids Jolt hands back out next, which feeds its
    // island ordering and so the simulation. Insertion order replays; hash order does not.
    private val constraints = LinkedHashMap<Long, CPointer<cnames.structs.JPC_Constraint>>()
    private val constraintBodies = LinkedHashMap<Long, Pair<JPC_BodyID, JPC_BodyID>>()
    private var nextConstraintId = 1L

    private val contacts = JoltContactQueue()

    /** Reaches the callback table's non-capturing `staticCFunction`s through its `self` pointer. */
    private val contactsRef = StableRef.create(contacts)

    private val contactListenerFns = cValue<JPC_ContactListenerFns> {
        OnContactValidate = staticCFunction(::contactValidate)
        OnContactAdded = staticCFunction(::contactAdded)
        OnContactPersisted = staticCFunction(::contactPersisted)
        OnContactRemoved = staticCFunction(::contactRemoved)
    }
    private val contactListener =
        JPC_ContactListener_new(contactsRef.asCPointer(), contactListenerFns)
            ?: error("JPC_ContactListener_new failed")

    init {
        JPC_PhysicsSystem_Init(
            physicsSystem,
            MAX_BODIES,
            NUM_BODY_MUTEXES,
            MAX_BODY_PAIRS,
            MAX_CONTACTS,
            broadPhaseLayerInterface,
            objectVsBroadPhaseLayerFilter,
            objectLayerPairFilter,
        )
        JPC_PhysicsSystem_SetGravity(physicsSystem, vec3Value(gravity))
        JPC_PhysicsSystem_SetContactListener(physicsSystem, contactListener)
        bodyInterface = JPC_PhysicsSystem_GetBodyInterface(physicsSystem)
            ?: error("JPC_PhysicsSystem_GetBodyInterface failed")
    }

    override fun createBody(
        shape: PhysicsShape,
        position: Vec3f,
        rotation: Quat,
        motionType: MotionType,
        layer: CollisionLayer,
        sensor: Boolean,
    ): BodyHandle = memScoped {
        // A triangle mesh is a surface with no inside, so Jolt cannot simulate one. Checked here
        // rather than in the shape builder, which has no motion type to judge against.
        (shape as? MeshShape)?.requireSupportedMotionType(motionType)
        // Enforced here even though this target builds the field as a mesh: a heightfield is
        // static on every backend, and a target that quietly accepted a dynamic one would let a
        // scene load on iOS and fail everywhere else.
        (shape as? HeightFieldShape)?.requireSupportedMotionType(motionType)
        if (sensor) shape.requireCanBeSensor()
        val joltShape = convexShape(shape)

        val joltMotionType: JPC_MotionType = when (motionType) {
            MotionType.STATIC -> JPC_MotionType.JPC_MOTION_TYPE_STATIC
            MotionType.KINEMATIC -> JPC_MotionType.JPC_MOTION_TYPE_KINEMATIC
            MotionType.DYNAMIC -> JPC_MotionType.JPC_MOTION_TYPE_DYNAMIC
        }
        require(layer.index < layers.count) {
            "layer ${layer.index} is outside this world's ${layers.count} layers"
        }
        val objectLayer: JPC_ObjectLayer = layer.index.toUShort()

        val bodyCreationSettings = alloc<JPC_BodyCreationSettings>()
        JPC_BodyCreationSettings_default(bodyCreationSettings.ptr)
        bodyCreationSettings.apply {
            Position.write(position)
            Rotation.write(rotation.w, rotation.x, rotation.y, rotation.z)
            MotionType = joltMotionType
            ObjectLayer = objectLayer
            IsSensor = sensor
            Shape = joltShape
        }

        val body = JPC_BodyInterface_CreateBody(bodyInterface, bodyCreationSettings.ptr)
            ?: error("JPC_BodyInterface_CreateBody failed")
        val bodyId = JPC_Body_GetID(body)
        val activation: JPC_Activation = if (motionType == MotionType.STATIC) {
            JPC_ACTIVATION_DONT_ACTIVATE
        } else {
            JPC_ACTIVATION_ACTIVATE
        }
        JPC_BodyInterface_AddBody(bodyInterface, bodyId, activation)

        trackedBodyIds.add(bodyId)
        if (sensor) contacts.setReporting(bodyId, true)
        BodyHandle(bodyId.toLong())
    }

    override fun destroyBody(handle: BodyHandle) {
        val id: JPC_BodyID = handle.id.toUInt()
        // Constraints first: Jolt does not detach them, so one still referencing a freed body
        // crashes on the next step.
        removeConstraintsFor(id)
        JPC_BodyInterface_RemoveBody(bodyInterface, id)
        JPC_BodyInterface_DestroyBody(bodyInterface, id)
        trackedBodyIds.remove(id)
        // Jolt hands the id back out to the next body created, which would inherit this one's
        // contact reporting.
        contacts.setReporting(id, false)
    }

    override fun setActive(handle: BodyHandle, active: Boolean) {
        val id = handle.id.toUInt()
        if (active) {
            JPC_BodyInterface_ActivateBody(bodyInterface, id)
        } else {
            JPC_BodyInterface_DeactivateBody(bodyInterface, id)
        }
    }

    override fun isActive(handle: BodyHandle): Boolean =
        JPC_BodyInterface_IsActive(bodyInterface, handle.id.toUInt())

    override fun createConstraint(constraint: Constraint): ConstraintHandle = memScoped {
        // One lock at a time, released before the next is taken. Holding two write locks at once
        // is what Jolt's own lock-ordering assert fires on -- measured, as a SIGTRAP inside
        // BodyLockInterfaceLocking::LockWrite. The pointer stays valid after the lock is released
        // because the body manager owns it for the body's lifetime; the lock guards against
        // concurrent mutation, and nothing else touches these bodies outside a step.
        val bodyA = lockedBody(constraint.bodyA)
            ?: error("body ${constraint.bodyA.id} is not in this world")
        val bodyB = lockedBody(constraint.bodyB)
            ?: error("body ${constraint.bodyB.id} is not in this world")
        val created = when (constraint) {
            is HingeConstraint -> hingeConstraint(constraint, bodyA, bodyB)
            is DistanceConstraint -> distanceConstraint(constraint, bodyA, bodyB)
            is BallSocketConstraint -> ballSocketConstraint(constraint, bodyA, bodyB)
        }
        JPC_PhysicsSystem_AddConstraint(physicsSystem, created)
        val id = nextConstraintId++
        constraints[id] = created
        constraintBodies[id] = constraint.bodyA.id.toUInt() to constraint.bodyB.id.toUInt()
        ConstraintHandle(id)
    }

    override fun destroyConstraint(handle: ConstraintHandle) {
        val constraint = constraints.remove(handle.id) ?: return
        constraintBodies.remove(handle.id)
        // No delete: a constraint is ref-counted and RemoveConstraint releases the system's
        // reference, so freeing it by hand double-frees.
        JPC_PhysicsSystem_RemoveConstraint(physicsSystem, constraint)
    }

    /** The Body behind a handle, or null when it is not in this world; see [createConstraint]. */
    private fun lockedBody(handle: BodyHandle): CPointer<cnames.structs.JPC_Body>? {
        val lock = JPC_PhysicsSystem_GetBodyLockInterface(physicsSystem)
            ?.let { JPC_BodyLockWrite_new(it, handle.id.toUInt()) }
            ?: return null
        return try {
            JPC_BodyLockWrite_GetBody(lock)?.takeIf { JPC_BodyLockWrite_Succeeded(lock) }
        } finally {
            JPC_BodyLockWrite_delete(lock)
        }
    }

    private fun MemScope.hingeConstraint(
        constraint: HingeConstraint,
        bodyA: CPointer<cnames.structs.JPC_Body>,
        bodyB: CPointer<cnames.structs.JPC_Body>,
    ): CPointer<cnames.structs.JPC_Constraint> {
        val settings = alloc<JPC_HingeConstraintSettings>()
        JPC_HingeConstraintSettings_default(settings.ptr)
        // World space, so Jolt converts to each body's local frame using the poses they hold now.
        settings.Space = JPC_ConstraintSpace.JPC_CONSTRAINT_SPACE_WORLD_SPACE
        settings.Point1.write(constraint.point)
        settings.Point2.write(constraint.point)
        val axis = constraint.axis.normalized()
        val normal = perpendicularTo(axis)
        settings.HingeAxis1.write(axis)
        settings.HingeAxis2.write(axis)
        // Jolt wants a normal axis perpendicular to the hinge and does not derive one.
        settings.NormalAxis1.write(normal)
        settings.NormalAxis2.write(normal)
        constraint.limits?.let {
            settings.LimitsMin = it.start
            settings.LimitsMax = it.endInclusive
        }
        return JPC_HingeConstraintSettings_Create(settings.ptr, bodyA, bodyB)
            ?.reinterpret()
            ?: error("JPC_HingeConstraintSettings_Create failed")
    }

    private fun MemScope.ballSocketConstraint(
        constraint: BallSocketConstraint,
        bodyA: CPointer<cnames.structs.JPC_Body>,
        bodyB: CPointer<cnames.structs.JPC_Body>,
    ): CPointer<cnames.structs.JPC_Constraint> {
        val settings = alloc<JPC_SixDOFConstraintSettings>()
        JPC_SixDOFConstraintSettings_default(settings.ptr)
        settings.Space = JPC_ConstraintSpace.JPC_CONSTRAINT_SPACE_WORLD_SPACE
        settings.Position1.write(constraint.point)
        settings.Position2.write(constraint.point)
        val twist = constraint.twistAxis.normalized()
        val reference = constraint.swingReferenceAxis()
        settings.AxisX1.write(twist)
        settings.AxisX2.write(twist)
        settings.AxisY1.write(reference)
        settings.AxisY2.write(reference)

        // JoltC exposes the limit arrays and no MakeFixedAxis/MakeFreeAxis helper, so the sentinels
        // those helpers write are spelled out here. They are the API: Jolt reads min > max as
        // "locked at zero" and the full float range as "unconstrained".
        fun fixed(axis: Int) {
            settings.LimitMin[axis] = Float.MAX_VALUE
            settings.LimitMax[axis] = -Float.MAX_VALUE
        }
        fun free(axis: Int) {
            settings.LimitMin[axis] = -Float.MAX_VALUE
            settings.LimitMax[axis] = Float.MAX_VALUE
        }
        fun limited(axis: Int, min: Float, max: Float) {
            settings.LimitMin[axis] = min
            settings.LimitMax[axis] = max
        }

        // A ball joint pivots and does not slide, so all three translations are pinned.
        fixed(TRANSLATION_X)
        fixed(TRANSLATION_Y)
        fixed(TRANSLATION_Z)
        // Rotation about X is the twist, Y and Z together are the swing -- Jolt's convention, and
        // why AxisX above is the twist axis.
        val twistLimit = constraint.twistLimit
        if (twistLimit == null) {
            free(ROTATION_X)
        } else {
            limited(ROTATION_X, twistLimit.start, twistLimit.endInclusive)
        }
        if (constraint.swingLimit >= BallSocketConstraint.FREE_SWING) {
            free(ROTATION_Y)
            free(ROTATION_Z)
        } else {
            // Symmetric: the cone swing type reads the maximum and assumes the minimum is its
            // negation. JoltC's settings struct has no swing-type field, so cone is what this gets.
            limited(ROTATION_Y, -constraint.swingLimit, constraint.swingLimit)
            limited(ROTATION_Z, -constraint.swingLimit, constraint.swingLimit)
        }
        return JPC_SixDOFConstraintSettings_Create(settings.ptr, bodyA, bodyB)
            ?.reinterpret()
            ?: error("JPC_SixDOFConstraintSettings_Create failed")
    }

    private fun MemScope.distanceConstraint(
        constraint: DistanceConstraint,
        bodyA: CPointer<cnames.structs.JPC_Body>,
        bodyB: CPointer<cnames.structs.JPC_Body>,
    ): CPointer<cnames.structs.JPC_Constraint> {
        val settings = alloc<JPC_DistanceConstraintSettings>()
        JPC_DistanceConstraintSettings_default(settings.ptr)
        settings.Space = JPC_ConstraintSpace.JPC_CONSTRAINT_SPACE_WORLD_SPACE
        settings.Point1.write(constraint.pointA)
        settings.Point2.write(constraint.pointB)
        settings.MinDistance = constraint.minDistance
        settings.MaxDistance = constraint.maxDistance
        return JPC_DistanceConstraintSettings_Create(settings.ptr, bodyA, bodyB)
            ?.reinterpret()
            ?: error("JPC_DistanceConstraintSettings_Create failed")
    }

    /** Removes every constraint touching this body; see [destroyBody] for why that is mandatory. */
    private fun removeConstraintsFor(bodyId: JPC_BodyID) {
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
        // JoltC puts this on JPC_Body rather than the body interface, so it needs the lock -- and
        // the lock doubles as the "is it still there" check for a handle destroyed since.
        val lock = JPC_PhysicsSystem_GetBodyLockInterface(physicsSystem)
            ?.let { JPC_BodyLockWrite_new(it, handle.id.toUInt()) }
            ?: return
        try {
            // Dynamic only, and the lock has to have succeeded. Jolt's ApplyBuoyancyImpulse reaches
            // for motion properties a static body does not have and aborts the process rather than
            // returning, so this is a hard requirement rather than a tidy-up.
            val body = JPC_BodyLockWrite_GetBody(lock)
                ?.takeIf { JPC_BodyLockWrite_Succeeded(lock) }
                ?.takeIf { JPC_Body_GetMotionType(it) == JPC_MotionType.JPC_MOTION_TYPE_DYNAMIC }
                ?: return
            JPC_Body_ApplyBuoyancyImpulse(
                body,
                vec3Value(Vec3f(0f, surfaceY, 0f)),
                vec3Value(Vec3f(0f, 1f, 0f)),
                buoyancy.strength,
                buoyancy.linearDrag,
                buoyancy.angularDrag,
                vec3Value(buoyancy.fluidVelocity),
                JPC_PhysicsSystem_GetGravity(physicsSystem),
                deltaTime,
            )
        } finally {
            JPC_BodyLockWrite_delete(lock)
        }
    }

    override fun setContinuousCollision(handle: BodyHandle, enabled: Boolean) {
        // Jolt's own name for it: a discrete step integrates then looks, a linear cast sweeps.
        JPC_BodyInterface_SetMotionQuality(
            bodyInterface,
            handle.id.toUInt(),
            if (enabled) {
                JPC_MotionQuality.JPC_MOTION_QUALITY_LINEAR_CAST
            } else {
                JPC_MotionQuality.JPC_MOTION_QUALITY_DISCRETE
            },
        )
    }

    override fun setContactReporting(handle: BodyHandle, enabled: Boolean) {
        contacts.setReporting(handle.id.toUInt(), enabled)
    }

    override fun drainContacts(action: (ContactEvent) -> Unit) = contacts.drain(action)

    override fun step(deltaTime: Float) {
        val collisionSteps = 1
        JPC_PhysicsSystem_Update(
            physicsSystem,
            deltaTime,
            collisionSteps,
            tempAllocator,
            jobSystem.reinterpret<JPC_JobSystem>(),
        )
    }

    override fun setLinearVelocity(handle: BodyHandle, velocity: Vec3f) {
        JPC_BodyInterface_SetLinearVelocity(bodyInterface, handle.id.toUInt(), vec3Value(velocity))
    }

    override fun setAngularVelocity(handle: BodyHandle, velocity: Vec3f) {
        JPC_BodyInterface_SetAngularVelocity(bodyInterface, handle.id.toUInt(), vec3Value(velocity))
    }

    override fun getLinearVelocity(handle: BodyHandle): Vec3f {
        // Returned by value, so it arrives as a CValue and has to be read inside useContents --
        // unlike GetPositionAndRotation, which writes through pointers.
        return JPC_BodyInterface_GetLinearVelocity(bodyInterface, handle.id.toUInt())
            .useContents { Vec3f(x, y, z) }
    }

    override fun addImpulse(handle: BodyHandle, impulse: Vec3f) {
        JPC_BodyInterface_AddImpulse(bodyInterface, handle.id.toUInt(), vec3Value(impulse))
    }

    override fun moveKinematic(
        handle: BodyHandle,
        position: Vec3f,
        rotation: Quat,
        deltaTime: Float,
    ) {
        JPC_BodyInterface_MoveKinematic(
            bodyInterface,
            handle.id.toUInt(),
            vec3Value(position),
            quatValue(rotation.w, rotation.x, rotation.y, rotation.z),
            deltaTime,
        )
    }

    /**
     * Every tracked body, not only the awake ones.
     *
     * JoltC exposes no `GetActiveBodies`, which jolt-jni and JoltPhysics.js both do, so this
     * target does more work per frame for the same answer. The contract allows it -- a sleeping
     * body reports the pose it already had -- and it is the third capability this binding lacks,
     * after heightfields and a prebuilt hit collector.
     */
    override fun forEachBodyTransform(
        action: (handle: BodyHandle, position: Vec3f, rotation: Quat) -> Unit,
    ) = memScoped {
        val position = alloc<JPC_RVec3>()
        val rotation = alloc<JPC_Quat>()
        trackedBodyIds.forEach { id ->
            JPC_BodyInterface_GetPositionAndRotation(bodyInterface, id, position.ptr, rotation.ptr)
            scratchOutPosition.set(position.x, position.y, position.z)
            scratchOutRotation.x = rotation.x
            scratchOutRotation.y = rotation.y
            scratchOutRotation.z = rotation.z
            scratchOutRotation.w = rotation.w
            action(BodyHandle(id.toLong()), scratchOutPosition, scratchOutRotation)
        }
    }

    override fun shiftOrigin(offset: Vec3f) = memScoped {
        // Read-then-write per body, as on desktop: JoltC exposes no bulk translate either, and a
        // sleeping body has to be activated or it stays behind at the old origin.
        val position = alloc<JPC_RVec3>()
        val rotation = alloc<JPC_Quat>()
        trackedBodyIds.forEach { id ->
            JPC_BodyInterface_GetPositionAndRotation(bodyInterface, id, position.ptr, rotation.ptr)
            position.x += offset.x
            position.y += offset.y
            position.z += offset.z
            JPC_BodyInterface_SetPositionAndRotation(
                bodyInterface,
                id,
                // By value, not by pointer: JoltC takes Vec3/Quat parameters by value, which
                // cinterop surfaces as CValue -- see [vec3Value]/[quatValue]'s own note.
                cValue<JPC_Vec3> {
                    x = position.x
                    y = position.y
                    z = position.z
                },
                quatValue(rotation.w, rotation.x, rotation.y, rotation.z),
                JPC_ACTIVATION_ACTIVATE,
            )
        }
    }

    override fun raycast(
        origin: Vec3f,
        direction: Vec3f,
        maxDistance: Float,
        onlyLayer: CollisionLayer?,
    ): RaycastHit? =
        memScoped {
            val normalizedDirection = direction.normalized()
            val castVector = Vec3f(
                normalizedDirection.x * maxDistance,
                normalizedDirection.y * maxDistance,
                normalizedDirection.z * maxDistance,
            )
            val args = alloc<JPC_NarrowPhaseQuery_CastRayArgs>()
            args.Ray.Origin.write(origin)
            args.Ray.Direction.write(castVector)
            args.BroadPhaseLayerFilter = null
            args.ShapeFilter = null
            // These were both null until a commonTest run put this backend on a simulator for the
            // first time: `onlyLayer` was accepted and then ignored, so a camera meant to see only
            // the level was stopped by every crate, and sensors were reported as solid.
            val filters = queryFilters(onlyLayer, ignore = null)
            args.ObjectLayerFilter = filters.layerFilter
            args.BodyFilter = filters.bodyFilter

            val narrowPhaseQuery = JPC_PhysicsSystem_GetNarrowPhaseQuery(physicsSystem)
                ?: error("JPC_PhysicsSystem_GetNarrowPhaseQuery failed")
            val hit = try {
                JPC_NarrowPhaseQuery_CastRay(narrowPhaseQuery, args.ptr)
            } finally {
                filters.dispose()
            }
            if (!hit) return@memScoped null

            val distance = maxDistance * args.Result.Fraction
            val point = Vec3f(
                origin.x + normalizedDirection.x * distance,
                origin.y + normalizedDirection.y * distance,
                origin.z + normalizedDirection.z * distance,
            )
            RaycastHit(BodyHandle(args.Result.BodyID.toLong()), point, distance)
        }

    override fun shapeCast(
        shape: PhysicsShape,
        from: Vec3f,
        to: Vec3f,
        onlyLayer: CollisionLayer?,
        ignore: BodyHandle?,
    ): ShapeCastHit? = memScoped {
        if (shape is HeightFieldShape) {
            throw PhysicsCapabilityException(
                "A HeightFieldShape is terrain to cast against, not a shape to cast with",
            )
        }
        if (shape is MeshShape) {
            throw PhysicsCapabilityException(
                "A MeshShape is a surface with no inside, so it cannot be swept -- " +
                    "use a ConvexHullShape",
            )
        }
        val joltShape = convexShape(shape)
        val best = ClosestShapeCastHit()
        val bestRef = StableRef.create(best)
        val filters = queryFilters(onlyLayer, ignore)
        val collectorFns = alloc<JPC_CastShapeCollectorFns>().apply {
            Reset = staticCFunction(::shapeCastReset)
            AddHit = staticCFunction(::shapeCastAddHit)
        }
        // A real body filter, so Jolt skips the ignored body inside the query and the collector's
        // early-out still reports the nearest OTHER hit -- the JVM backends have to collect every
        // hit instead, because jolt-jni's BodyFilter cannot be overridden.
        val collector = JPC_CastShapeCollector_new(bestRef.asCPointer(), collectorFns.readValue())
        try {
            val narrowPhaseQuery = JPC_PhysicsSystem_GetNarrowPhaseQuery(physicsSystem)
                ?: error("JPC_PhysicsSystem_GetNarrowPhaseQuery failed")
            val args = alloc<JPC_NarrowPhaseQuery_CastShapeArgs>()
            args.ShapeCast.Shape = joltShape
            args.ShapeCast.Scale.write(Vec3f(1f, 1f, 1f))
            // JPC_Mat44 is three Vec4 columns plus a Vec3 translation, so an unrotated start
            // transform is the identity basis with `from` in col3. There is no Position/Rotation
            // pair to set here the way JPC_BodyCreationSettings has.
            val start = args.ShapeCast.CenterOfMassStart
            start.col[0].apply {
                x = 1f
                y = 0f
                z = 0f
                w = 0f
            }
            start.col[1].apply {
                x = 0f
                y = 1f
                z = 0f
                w = 0f
            }
            start.col[2].apply {
                x = 0f
                y = 0f
                z = 1f
                w = 0f
            }
            start.col3.write(from)
            args.ShapeCast.Direction.write(Vec3f(to.x - from.x, to.y - from.y, to.z - from.z))
            JPC_ShapeCastSettings_default(args.Settings.ptr)
            args.Collector = collector
            filters.layerFilter?.let { args.ObjectLayerFilter = it }
            filters.bodyFilter?.let { args.BodyFilter = it }
            JPC_NarrowPhaseQuery_CastShape(narrowPhaseQuery, args.ptr)
        } finally {
            JPC_CastShapeCollector_delete(collector)
            bestRef.dispose()
            filters.dispose()
        }

        if (!best.hasHit) return@memScoped null
        ShapeCastHit(
            handle = BodyHandle(best.bodyId.toLong()),
            point = Vec3f(best.pointX, best.pointY, best.pointZ),
            normal = Vec3f(best.normalX, best.normalY, best.normalZ),
            fraction = best.fraction,
        )
    }

    override fun overlapShape(
        shape: PhysicsShape,
        position: Vec3f,
        onlyLayer: CollisionLayer?,
        onOverlap: (BodyHandle) -> Unit,
    ) = memScoped {
        if (shape is HeightFieldShape || shape is MeshShape) {
            throw PhysicsCapabilityException(
                "A ${shape::class.simpleName} is a surface with no inside, so nothing can be inside it",
            )
        }
        val joltShape = convexShape(shape)
        val hits = OverlapHits()
        val hitsRef = StableRef.create(hits)
        val filters = queryFilters(onlyLayer, ignore = null)
        val collectorFns = alloc<JPC_CollideShapeCollectorFns>().apply {
            Reset = staticCFunction(::overlapReset)
            AddHit = staticCFunction(::overlapAddHit)
        }
        val collector = JPC_CollideShapeCollector_new(hitsRef.asCPointer(), collectorFns.readValue())
        try {
            val narrowPhaseQuery = JPC_PhysicsSystem_GetNarrowPhaseQuery(physicsSystem)
                ?: error("JPC_PhysicsSystem_GetNarrowPhaseQuery failed")
            val args = alloc<JPC_NarrowPhaseQuery_CollideShapeArgs>()
            args.Shape = joltShape
            args.ShapeScale.write(Vec3f(1f, 1f, 1f))
            // Same identity-basis-plus-translation as the shape cast: JPC_Mat44 has no
            // position/rotation pair to set.
            val transform = args.CenterOfMassTransform
            transform.col[0].apply {
                x = 1f
                y = 0f
                z = 0f
                w = 0f
            }
            transform.col[1].apply {
                x = 0f
                y = 1f
                z = 0f
                w = 0f
            }
            transform.col[2].apply {
                x = 0f
                y = 0f
                z = 1f
                w = 0f
            }
            transform.col3.write(position)
            JPC_CollideShapeSettings_default(args.Settings.ptr)
            args.Collector = collector
            filters.layerFilter?.let { args.ObjectLayerFilter = it }
            JPC_NarrowPhaseQuery_CollideShape(narrowPhaseQuery, args.ptr)
        } finally {
            JPC_CollideShapeCollector_delete(collector)
            hitsRef.dispose()
            filters.dispose()
        }
        hits.bodyIds.forEach { onOverlap(BodyHandle(it.toLong())) }
    }

    override fun destroy() {
        trackedBodyIds.forEach { id ->
            JPC_BodyInterface_RemoveBody(bodyInterface, id)
            JPC_BodyInterface_DestroyBody(bodyInterface, id)
        }
        constraints.keys.toList().forEach { destroyConstraint(ConstraintHandle(it)) }
        trackedBodyIds.clear()
        layersRef.dispose()
        JPC_ContactListener_delete(contactListener)
        contactsRef.dispose()
        contacts.dispose()
        JPC_PhysicsSystem_delete(physicsSystem)
        JPC_ObjectLayerPairFilter_delete(objectLayerPairFilter)
        JPC_ObjectVsBroadPhaseLayerFilter_delete(objectVsBroadPhaseLayerFilter)
        JPC_BroadPhaseLayerInterface_delete(broadPhaseLayerInterface)
        JPC_JobSystemThreadPool_delete(jobSystem)
        JPC_TempAllocatorImpl_delete(tempAllocator)
        // JPC_UnregisterTypes()/JPC_FactoryDelete() are deliberately NOT called here -- they
        // are process-wide (matching JPC_RegisterTypes()/JPC_FactoryInit() in the companion
        // `init` above being called at most once too), and a second JoltPhysicsWorld could
        // still be constructed later in the same process.
    }
}

/**
 * Any unit vector at right angles to [axis].
 *
 * Jolt wants a hinge's normal axis as well as its hinge axis and does not derive one; which
 * perpendicular is chosen only decides where a limit's zero angle sits.
 */
private fun perpendicularTo(axis: Vec3f): Vec3f {
    // Cross with whichever cardinal axis this is least aligned to, so the result is never degenerate.
    val reference = if (kotlin.math.abs(axis.y) < 0.9f) Vec3f(0f, 1f, 0f) else Vec3f(1f, 0f, 0f)
    return Vec3f(
        axis.y * reference.z - axis.z * reference.y,
        axis.z * reference.x - axis.x * reference.z,
        axis.x * reference.y - axis.y * reference.x,
    ).normalized()
}

// JPC_SixDOFConstraint_Axis, as indices into the settings struct's limit arrays. Named here rather
// than used as bare numbers because the two halves are easy to transpose and Jolt validates neither.
private const val TRANSLATION_X = 0
private const val TRANSLATION_Y = 1
private const val TRANSLATION_Z = 2
private const val ROTATION_X = 3
private const val ROTATION_Y = 4
private const val ROTATION_Z = 5
