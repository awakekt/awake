/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

@file:Suppress("TooManyFunctions") // One top-level @JsFun binding per JS call; they cannot be grouped.

package com.awakekt.awake.physics.jolt

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
import com.awakekt.awake.physics.ContactPhase
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
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * `@JsModule("jolt-physics")`'s default export: an Emscripten module-factory function
 * (`declare function Jolt<T>(target?: T): Promise<T & typeof Jolt>`, see the npm package's
 * `dist/types.d.ts`) -- calling it with no argument resolves a `Promise` of the `Jolt`
 * namespace object itself (every `Jolt.XXX` class/constant lives as a property on that one
 * resolved object, not as separate ES module exports Kotlin/Wasm's `@JsModule` could bind
 * per-declaration). Kotlin/Wasm has no `dynamic` type, so nothing about that resolved
 * object's shape can be described with a compile-time-bound `external class`/`external
 * interface` the way `kotlinx-browser`'s DOM bindings are -- every actual Jolt call below
 * goes through an `@JsFun` inline-JS snippet instead, taking the resolved namespace object
 * (opaque `JsAny`) as a plain parameter and indexing into it by property name at runtime,
 * exactly like any other reflective/dynamic JS access. This is the Kotlin/Wasm-idiomatic
 * substitute for what would be `dynamic` on Kotlin/JS.
 */
@JsModule("jolt-physics")
external fun initJoltModule(): Promise<JsAny>

/**
 * Loads the `jolt-physics` WASM module at most once per page -- mirrors the desktop/iOS
 * backends' own "register/init native library exactly once per process" companion-object
 * pattern (`JoltNative.ensureLoaded()` / the iOS `JPC_FactoryInit()` companion `init`), just
 * async since Emscripten's own module bootstrap is a `Promise` here instead of a synchronous
 * `dlopen`.
 */
private object JoltModule {
    private var cached: JsAny? = null

    suspend fun get(): JsAny {
        cached?.let { return it }
        val loaded = initJoltModule().await<JsAny>()
        cached = loaded
        return loaded
    }
}

// @JsFun glue below mirrors jrouwe/JoltPhysics.js's Examples/js/example.js call sequence
// verbatim; `jolt.XXX` values are read from the untyped namespace object [JoltModule.get]
// resolves since Kotlin/Wasm has no per-field typed handle for them (no `dynamic`).

/**
 * [jolt.JoltInterface]'s own object-layer/broadphase-layer collision-filtering setup, plus
 * gravity -- mirrors `Examples/js/example.js`'s `setupCollisionFiltering`/`initPhysics`
 * combined into one call. Two object layers (matching every other platform's own "moving vs.
 * non-moving" scoping, see e.g. the desktop backend's own `NUM_OBJECT_LAYERS` doc comment)
 * mapped 1:1 onto two broadphase layers -- copied verbatim from the upstream example rather
 * than collapsed to one broadphase layer (as the desktop/iOS backends do) specifically
 * because this is the exact shape JoltPhysics.js's own maintained example uses; deviating
 * from it for a slice this thin isn't worth losing that as a reference point.
 *
 * Returns an opaque JS object `{ jolt, joltInterface, physicsSystem, bodyInterface }` --
 * every other glue function below takes that object as its first parameter and reads
 * `.jolt`/`.bodyInterface`/`.physicsSystem` back out of it, since none of those individual
 * values can be given their own Kotlin-typed handle (same "no dynamic type" constraint
 * [JoltModule] documents).
 */
// Called from JoltPhysicsWorld.create() below; detekt's unused-check can't trace calls
// through an external @JsFun-bound declaration.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a layer table.
@JsFun(
    """
    (jolt, gx, gy, gz, layerCount, collidesFlat, movingFlat) => {
        // Tables built from the world's own matrix rather than hardcoded: `collidesFlat` is the
        // layerCount x layerCount matrix row-major, `movingFlat` says which layers can move and so
        // which broadphase tree they belong in.
        const NUM_BROAD_PHASE_LAYERS = 2;
        const BP_STATIC = 0;
        const BP_MOVING = 1;

        const settings = new jolt.JoltSettings();
        const objectFilter = new jolt.ObjectLayerPairFilterTable(layerCount);
        for (let a = 0; a < layerCount; a++) {
            for (let b = a; b < layerCount; b++) {
                if (collidesFlat[a * layerCount + b]) {
                    objectFilter.EnableCollision(a, b);
                } else {
                    objectFilter.DisableCollision(a, b);
                }
            }
        }

        const bpInterface = new jolt.BroadPhaseLayerInterfaceTable(layerCount, NUM_BROAD_PHASE_LAYERS);
        for (let i = 0; i < layerCount; i++) {
            bpInterface.MapObjectToBroadPhaseLayer(
                i, new jolt.BroadPhaseLayer(movingFlat[i] ? BP_MOVING : BP_STATIC)
            );
        }

        settings.mObjectLayerPairFilter = objectFilter;
        settings.mBroadPhaseLayerInterface = bpInterface;
        settings.mObjectVsBroadPhaseLayerFilter = new jolt.ObjectVsBroadPhaseLayerFilterTable(
            bpInterface, NUM_BROAD_PHASE_LAYERS, objectFilter, layerCount
        );

        const joltInterface = new jolt.JoltInterface(settings);
        jolt.destroy(settings);
        const physicsSystem = joltInterface.GetPhysicsSystem();
        physicsSystem.SetGravity(new jolt.Vec3(gx, gy, gz));
        const bodyInterface = physicsSystem.GetBodyInterface();

        // Contacts are buffered here rather than delivered as they happen: JoltPhysics.js calls
        // these from inside Update(), so acting on one would re-enter a running simulation.
        // Nothing in this runtime is threaded, so a plain array is all the synchronisation needed
        // -- unlike the JVM and native backends, where Jolt's worker threads make this a lock.
        const world = {
            jolt, joltInterface, physicsSystem, bodyInterface, objectFilter,
            contacts: [], reporting: new Set(),
        };
        const record = (id1, id2, phase) => {
            // Jolt reports every touching pair in the scene, so without this filter a settled pile
            // of crates would buffer an event per pair per step for events nothing reads.
            if (!world.reporting.has(id1) && !world.reporting.has(id2)) return;
            world.contacts.push(id1, id2, phase);
        };
        const listener = new jolt.ContactListenerJS();
        // All four slots are assigned because ContactListenerJS requires them; only the two
        // transitions record anything.
        listener.OnContactValidate = () => jolt.ValidateResult_AcceptAllContactsForThisBodyPair;
        listener.OnContactAdded = (body1, body2) => {
            record(
                jolt.wrapPointer(body1, jolt.Body).GetID().GetIndexAndSequenceNumber(),
                jolt.wrapPointer(body2, jolt.Body).GetID().GetIndexAndSequenceNumber(),
                0
            );
        };
        listener.OnContactPersisted = () => {};
        listener.OnContactRemoved = (subShapePair) => {
            // Removal carries the sub-shape pair and nothing else, and either body may already
            // have been destroyed -- so only ids are read here, never the bodies behind them.
            const pair = jolt.wrapPointer(subShapePair, jolt.SubShapeIDPair);
            record(
                pair.GetBody1ID().GetIndexAndSequenceNumber(),
                pair.GetBody2ID().GetIndexAndSequenceNumber(),
                1
            );
        };
        physicsSystem.SetContactListener(listener);
        world.contactListener = listener;
        return world;
    }
    """,
)
private external fun joltCreateWorld(
    jolt: JsAny,
    gravityX: Double,
    gravityY: Double,
    gravityZ: Double,
    layerCount: Int,
    collidesFlat: JsAny,
    movingFlat: JsAny,
): JsAny

/** A body's object layer is its collision layer index; the tables above give it meaning. */
// Called from JoltPhysicsWorld.createBody() below; detekt's unused-check can't trace
// calls through an external @JsFun-bound declaration.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a shape type.
@JsFun(
    """
    (world, shapeKind, hx, hy, hz, radius, halfHeight, px, py, pz, qw, qx, qy, qz, motionTypeCode, isStatic, layerIndex, isSensor) => {
        const jolt = world.jolt;
        const shape = shapeKind === 0
            ? new jolt.BoxShape(new jolt.Vec3(hx, hy, hz), 0.05, null)
            : (shapeKind === 1
                ? new jolt.SphereShape(radius, null)
                : new jolt.CapsuleShape(halfHeight, radius, null));
        const motionType = motionTypeCode === 0
            ? jolt.EMotionType_Static
            : (motionTypeCode === 1 ? jolt.EMotionType_Kinematic : jolt.EMotionType_Dynamic);

        const bodyCreationSettings = new jolt.BodyCreationSettings(
            shape, new jolt.RVec3(px, py, pz), new jolt.Quat(qx, qy, qz, qw), motionType, layerIndex
        );
        bodyCreationSettings.mIsSensor = isSensor;
        const body = world.bodyInterface.CreateBody(bodyCreationSettings);
        jolt.destroy(bodyCreationSettings);
        const activation = isStatic ? jolt.EActivation_DontActivate : jolt.EActivation_Activate;
        world.bodyInterface.AddBody(body.GetID(), activation);
        return body.GetID().GetIndexAndSequenceNumber();
    }
    """,
)
private external fun joltCreateBody(
    world: JsAny,
    shapeKind: Int,
    halfExtentX: Double,
    halfExtentY: Double,
    halfExtentZ: Double,
    radius: Double,
    halfHeight: Double,
    positionX: Double,
    positionY: Double,
    positionZ: Double,
    rotationW: Double,
    rotationX: Double,
    rotationY: Double,
    rotationZ: Double,
    motionTypeCode: Int,
    isStatic: Boolean,
    layerIndex: Int,
    isSensor: Boolean,
): Int

/**
 * A convex [PhysicsShape] flattened into the primitives the `@JsFun` boundary can carry.
 *
 * Shared by body creation and the shape cast, which both need the same six numbers and had
 * grown the same `when` twice.
 */
private class FlatShape(
    val kind: Int,
    val halfExtentX: Float,
    val halfExtentY: Float,
    val halfExtentZ: Float,
    val radius: Float,
    val halfHeight: Float,
)

private fun flatten(shape: PhysicsShape, unsupported: String): FlatShape = when (shape) {
    is BoxShape -> FlatShape(
        SHAPE_KIND_BOX,
        shape.halfExtents.x,
        shape.halfExtents.y,
        shape.halfExtents.z,
        0f,
        0f,
    )

    is SphereShape -> FlatShape(SHAPE_KIND_SPHERE, 0f, 0f, 0f, shape.radius, 0f)
    is CapsuleShape -> FlatShape(SHAPE_KIND_CAPSULE, 0f, 0f, 0f, shape.radius, shape.halfHeight)
    // Neither flattens to six numbers: a hull is a point cloud and a mesh is a triangle list, so
    // both take their own path rather than pretending to fit here.
    // A hull is a point cloud, so it takes its own path rather than these six numbers. It cannot
    // be swept on this backend for the same reason -- nothing sweeps a hull today; characters
    // sweep capsules.
    is ConvexHullShape -> throw PhysicsCapabilityException(
        "A ConvexHullShape cannot be swept on the JoltPhysics.js backend",
    )

    is MeshShape -> throw PhysicsCapabilityException(unsupported)
    is HeightFieldShape -> throw PhysicsCapabilityException(unsupported)
}

/**
 * Builds a body from the convex hull of a flat point array.
 *
 * Its own entry point rather than a case of [joltCreateBody]: a hull is a point cloud, which does
 * not flatten into the six numbers that path carries.
 */
// Called from JoltPhysicsWorld.createBody() below; detekt cannot trace @JsFun calls.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a shape type.
@JsFun(
    """
    (world, pts, px, py, pz, qw, qx, qy, qz, motionTypeCode, isStatic, layerIndex, isSensor) => {
        const jolt = world.jolt;
        const points = new jolt.ArrayVec3();
        for (let i = 0; i < pts.length; i += 3) {
            points.push_back(new jolt.Vec3(pts[i], pts[i + 1], pts[i + 2]));
        }
        const settings = new jolt.ConvexHullShapeSettings();
        settings.mPoints = points;
        const shape = settings.Create().Get();
        const motionType = motionTypeCode === 0
            ? jolt.EMotionType_Static
            : (motionTypeCode === 1 ? jolt.EMotionType_Kinematic : jolt.EMotionType_Dynamic);
        const bodyCreationSettings = new jolt.BodyCreationSettings(
            shape, new jolt.RVec3(px, py, pz), new jolt.Quat(qx, qy, qz, qw), motionType, layerIndex
        );
        bodyCreationSettings.mIsSensor = isSensor;
        const body = world.bodyInterface.CreateBody(bodyCreationSettings);
        jolt.destroy(bodyCreationSettings);
        jolt.destroy(settings);
        jolt.destroy(points);
        const activation = isStatic ? jolt.EActivation_DontActivate : jolt.EActivation_Activate;
        world.bodyInterface.AddBody(body.GetID(), activation);
        return body.GetID().GetIndexAndSequenceNumber();
    }
    """,
)
private external fun joltCreateHullBody(
    world: JsAny,
    points: JsAny,
    px: Double,
    py: Double,
    pz: Double,
    qw: Double,
    qx: Double,
    qy: Double,
    qz: Double,
    motionTypeCode: Int,
    isStatic: Boolean,
    layerIndex: Int,
    isSensor: Boolean,
): Int

/**
 * Builds a static triangle-mesh body from a flat vertex array and a flat index array.
 *
 * The arrays cross as JS arrays and are rebuilt into Jolt's own `TriangleList` on the far side,
 * for the same reason the heightfield's samples are: the `@JsFun` boundary carries primitives.
 */
// Called from JoltPhysicsWorld.createBody() below; detekt cannot trace @JsFun calls.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a mesh type.
@JsFun(
    """
    (world, verts, indices, px, py, pz, qw, qx, qy, qz, layerIndex) => {
        const jolt = world.jolt;
        const triangles = new jolt.TriangleList();
        const count = indices.length / 3;
        triangles.resize(count);
        for (let t = 0; t < count; t++) {
            const a = indices[t * 3] * 3, b = indices[t * 3 + 1] * 3, c = indices[t * 3 + 2] * 3;
            const tri = triangles.at(t);
            tri.set_mV(0, new jolt.Float3(verts[a], verts[a + 1], verts[a + 2]));
            tri.set_mV(1, new jolt.Float3(verts[b], verts[b + 1], verts[b + 2]));
            tri.set_mV(2, new jolt.Float3(verts[c], verts[c + 1], verts[c + 2]));
        }
        const settings = new jolt.MeshShapeSettings(triangles);
        const shape = settings.Create().Get();
        const bodyCreationSettings = new jolt.BodyCreationSettings(
            shape, new jolt.RVec3(px, py, pz), new jolt.Quat(qx, qy, qz, qw),
            jolt.EMotionType_Static, layerIndex
        );
        const body = world.bodyInterface.CreateBody(bodyCreationSettings);
        jolt.destroy(bodyCreationSettings);
        jolt.destroy(settings);
        jolt.destroy(triangles);
        world.bodyInterface.AddBody(body.GetID(), jolt.EActivation_DontActivate);
        return body.GetID().GetIndexAndSequenceNumber();
    }
    """,
)
private external fun joltCreateMeshBody(
    world: JsAny,
    verts: JsAny,
    indices: JsAny,
    px: Double,
    py: Double,
    pz: Double,
    qw: Double,
    qx: Double,
    qy: Double,
    qz: Double,
    layerIndex: Int,
): Int

/** A growable JS array to stage heightfield samples in -- see [joltCreateHeightFieldBody]. */
// Called from JoltPhysicsWorld.createBody() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun("() => []")
private external fun jsNewArray(): JsAny

// Called from JoltPhysicsWorld.createBody() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun("(arr, value) => { arr.push(value); }")
private external fun jsArrayPush(arr: JsAny, value: Double)

/**
 * Creates a static heightfield body from samples staged in [samples].
 *
 * [offsetX]/[offsetZ] carry the same centring the JVM backends apply: Jolt lays a field out
 * from its own corner while the engine's heightmaps are centred on their origin, and a
 * disagreement here puts the collider half a tile from the terrain it belongs to.
 */
// Called from JoltPhysicsWorld.createBody() below; detekt cannot trace @JsFun calls.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a shape type.
@JsFun(
    """
    (world, samples, sampleCount, offsetX, offsetZ, sx, sy, sz, px, py, pz) => {
        const jolt = world.jolt;
        const heights = new jolt.ArrayFloat();
        heights.reserve(samples.length);
        for (let i = 0; i < samples.length; i++) heights.push_back(samples[i]);
        const settings = new jolt.HeightFieldShapeSettings();
        settings.mHeightSamples = heights;
        settings.mSampleCount = sampleCount;
        settings.mOffset = new jolt.Vec3(offsetX, 0, offsetZ);
        settings.mScale = new jolt.Vec3(sx, sy, sz);
        const shape = settings.Create().Get();
        const bodyCreationSettings = new jolt.BodyCreationSettings(
            shape, new jolt.RVec3(px, py, pz), new jolt.Quat(0, 0, 0, 1),
            jolt.EMotionType_Static, 0
        );
        const body = world.bodyInterface.CreateBody(bodyCreationSettings);
        jolt.destroy(bodyCreationSettings);
        jolt.destroy(settings);
        jolt.destroy(heights);
        world.bodyInterface.AddBody(body.GetID(), jolt.EActivation_DontActivate);
        return body.GetID().GetIndexAndSequenceNumber();
    }
    """,
)
private external fun joltCreateHeightFieldBody(
    world: JsAny,
    samples: JsAny,
    sampleCount: Int,
    offsetX: Double,
    offsetZ: Double,
    scaleX: Double,
    scaleY: Double,
    scaleZ: Double,
    positionX: Double,
    positionY: Double,
    positionZ: Double,
): Int

// Shape discriminators for [joltCreateBody]/[joltShapeCast]'s flattened parameter lists -- the
// @JsFun boundary carries primitives, not a sealed type.
private const val SHAPE_KIND_BOX = 0
private const val SHAPE_KIND_SPHERE = 1
private const val SHAPE_KIND_CAPSULE = 2

/**
 * Sweeps a convex shape and returns `[hit, bodyId, px, py, pz, nx, ny, nz, fraction]`, with
 * `hit` 0 or 1 -- one crossing per cast rather than one per field.
 *
 * `mPenetrationAxis` points INTO the surface and is unnormalized, so it is negated and
 * normalized here; a caller sliding along the raw axis would drive straight into the wall it
 * just hit.
 */
// Called from JoltPhysicsWorld.shapeCast() below; detekt cannot trace @JsFun calls.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a shape type.
@JsFun(
    """
    (world, shapeKind, hx, hy, hz, radius, halfHeight, fx, fy, fz, tx, ty, tz, onlyLayer, ignoreId) => {
        const jolt = world.jolt;
        const shape = shapeKind === 0
            ? new jolt.BoxShape(new jolt.Vec3(hx, hy, hz), 0.05, null)
            : (shapeKind === 1
                ? new jolt.SphereShape(radius, null)
                : new jolt.CapsuleShape(halfHeight, radius, null));
        const start = jolt.RMat44.prototype.sTranslation(new jolt.RVec3(fx, fy, fz));
        const direction = new jolt.Vec3(tx - fx, ty - fy, tz - fz);
        const cast = new jolt.RShapeCast(shape, new jolt.Vec3(1, 1, 1), start, direction);
        const settings = new jolt.ShapeCastSettings();
        const collector = new jolt.CastShapeClosestHitCollisionCollector();
        const layerFilter = onlyLayer < 0
            ? new jolt.ObjectLayerFilter()
            : new jolt.SpecifiedObjectLayerFilter(onlyLayer);
        // Skips sensors as well as the ignored body, and does it inside the query so the collector
        // still reports the nearest body that genuinely blocks. A sensor is not solid, and
        // reporting one turns every trigger volume into an invisible wall.
        const bodyFilter = new jolt.BodyFilterJS();
        // Both callbacks are handed Emscripten POINTERS, not values -- the id form gets a BodyID*,
        // not the id itself, so comparing it to a body id directly never matches and the exclusion
        // silently does nothing. Only running this backend showed that.
        bodyFilter.ShouldCollide =
            (bodyId) => jolt.wrapPointer(bodyId, jolt.BodyID).GetIndexAndSequenceNumber() !== ignoreId;
        bodyFilter.ShouldCollideLocked =
            (body) => !jolt.wrapPointer(body, jolt.Body).IsSensor();
        world.physicsSystem.GetNarrowPhaseQuery().CastShape(
            cast, settings, new jolt.RVec3(0, 0, 0), collector,
            new jolt.BroadPhaseLayerFilter(), layerFilter,
            bodyFilter, new jolt.ShapeFilter()
        );
        const hadHit = collector.HadHit();
        let out = [0, 0, 0, 0, 0, 0, 0, 0, 0];
        if (hadHit) {
            const hit = collector.mHit;
            const point = hit.mContactPointOn2;
            const axis = hit.mPenetrationAxis.Normalized();
            out = [
                1, hit.mBodyID2.GetIndexAndSequenceNumber(),
                point.GetX(), point.GetY(), point.GetZ(),
                -axis.GetX(), -axis.GetY(), -axis.GetZ(),
                hit.mFraction,
            ];
        }
        jolt.destroy(collector);
        jolt.destroy(settings);
        jolt.destroy(cast);
        jolt.destroy(shape);
        return out;
    }
    """,
)
private external fun joltShapeCast(
    world: JsAny,
    shapeKind: Int,
    halfExtentX: Double,
    halfExtentY: Double,
    halfExtentZ: Double,
    radius: Double,
    halfHeight: Double,
    fromX: Double,
    fromY: Double,
    fromZ: Double,
    toX: Double,
    toY: Double,
    toZ: Double,
    onlyLayer: Int,
    ignoreId: Int,
): JsAny

/**
 * Every body overlapping a convex shape, as `[count, id, id, ...]` -- see [jsArrayGet].
 *
 * `CollideShapeAllHitCollisionCollector` rather than a closest-hit one: the whole point of an
 * overlap is that a body behind another is still inside the volume.
 */
// Called from JoltPhysicsWorld.overlapShape() below; detekt cannot trace @JsFun calls.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a shape type.
@JsFun(
    """
    (world, shapeKind, hx, hy, hz, radius, halfHeight, px, py, pz, onlyLayer) => {
        const jolt = world.jolt;
        const shape = shapeKind === 0
            ? new jolt.BoxShape(new jolt.Vec3(hx, hy, hz), 0.05, null)
            : (shapeKind === 1
                ? new jolt.SphereShape(radius, null)
                : new jolt.CapsuleShape(halfHeight, radius, null));
        const transform = jolt.RMat44.prototype.sTranslation(new jolt.RVec3(px, py, pz));
        const settings = new jolt.CollideShapeSettings();
        const collector = new jolt.CollideShapeAllHitCollisionCollector();
        const layerFilter = onlyLayer < 0
            ? new jolt.ObjectLayerFilter()
            : new jolt.SpecifiedObjectLayerFilter(onlyLayer);
        world.physicsSystem.GetNarrowPhaseQuery().CollideShape(
            shape, new jolt.Vec3(1, 1, 1), transform, settings, new jolt.RVec3(0, 0, 0), collector,
            new jolt.BroadPhaseLayerFilter(), layerFilter,
            new jolt.BodyFilter(), new jolt.ShapeFilter()
        );
        const hits = collector.mHits;
        const out = [hits.size()];
        for (let i = 0; i < hits.size(); i++) {
            out.push(hits.at(i).mBodyID2.GetIndexAndSequenceNumber());
        }
        jolt.destroy(collector);
        jolt.destroy(settings);
        jolt.destroy(shape);
        return out;
    }
    """,
)
private external fun joltOverlapShape(
    world: JsAny,
    shapeKind: Int,
    halfExtentX: Double,
    halfExtentY: Double,
    halfExtentZ: Double,
    radius: Double,
    halfHeight: Double,
    positionX: Double,
    positionY: Double,
    positionZ: Double,
    onlyLayer: Int,
): JsAny

// Called from JoltPhysicsWorld.destroyBody()/destroy() below; detekt's unused-check
// can't trace calls through an external @JsFun-bound declaration.
@Suppress("unused")
@JsFun(
    """
    (world, idNum) => {
        const id = new world.jolt.BodyID(idNum);
        world.bodyInterface.RemoveBody(id);
        world.bodyInterface.DestroyBody(id);
    }
    """,
)
private external fun joltDestroyBody(world: JsAny, idNum: Int)

/** Jolt's own name for it: a discrete step integrates then looks, a linear cast sweeps. */
// Called from JoltPhysicsWorld.setContinuousCollision() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun(
    """
    (world, idNum, enabled) => {
        const jolt = world.jolt;
        world.bodyInterface.SetMotionQuality(
            new jolt.BodyID(idNum),
            enabled ? jolt.EMotionQuality_LinearCast : jolt.EMotionQuality_Discrete
        );
    }
    """,
)
private external fun joltSetMotionQuality(world: JsAny, idNum: Int, enabled: Boolean)

// Called from JoltPhysicsWorld.setActive() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun(
    """
    (world, idNum, active) => {
        const id = new world.jolt.BodyID(idNum);
        if (active) world.bodyInterface.ActivateBody(id); else world.bodyInterface.DeactivateBody(id);
    }
    """,
)
private external fun joltSetActive(world: JsAny, idNum: Int, active: Boolean)

// Called from JoltPhysicsWorld.isActive() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun("(world, idNum) => world.bodyInterface.IsActive(new world.jolt.BodyID(idNum))")
private external fun joltIsActive(world: JsAny, idNum: Int): Boolean

/**
 * Creates a hinge or a distance constraint and adds it, returning the JS object to hold onto.
 *
 * World space, so Jolt converts to each body's local frame using the poses they hold right now --
 * whatever arrangement they are in becomes the constraint's rest pose.
 */
// Called from JoltPhysicsWorld.createConstraint() below; detekt cannot trace @JsFun calls.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a constraint type.
@JsFun(
    """
    (world, hinge, idA, idB, ax, ay, az, bx, by, bz, dx, dy, dz, nx, ny, nz, low, high) => {
        const jolt = world.jolt;
        const lock = world.physicsSystem.GetBodyLockInterfaceNoLock();
        const bodyA = lock.TryGetBody(new jolt.BodyID(idA));
        const bodyB = lock.TryGetBody(new jolt.BodyID(idB));
        let settings;
        if (hinge) {
            settings = new jolt.HingeConstraintSettings();
            settings.mSpace = jolt.EConstraintSpace_WorldSpace;
            settings.mPoint1 = new jolt.RVec3(ax, ay, az);
            settings.mPoint2 = new jolt.RVec3(ax, ay, az);
            settings.mHingeAxis1 = new jolt.Vec3(dx, dy, dz);
            settings.mHingeAxis2 = new jolt.Vec3(dx, dy, dz);
            // Jolt requires a normal axis perpendicular to the hinge axis and does not derive one.
            settings.mNormalAxis1 = new jolt.Vec3(nx, ny, nz);
            settings.mNormalAxis2 = new jolt.Vec3(nx, ny, nz);
            if (low <= high) { settings.mLimitsMin = low; settings.mLimitsMax = high; }
        } else {
            settings = new jolt.DistanceConstraintSettings();
            settings.mSpace = jolt.EConstraintSpace_WorldSpace;
            settings.mPoint1 = new jolt.RVec3(ax, ay, az);
            settings.mPoint2 = new jolt.RVec3(bx, by, bz);
            settings.mMinDistance = low;
            settings.mMaxDistance = high;
        }
        const constraint = settings.Create(bodyA, bodyB);
        world.physicsSystem.AddConstraint(constraint);
        jolt.destroy(settings);
        return constraint;
    }
    """,
)
private external fun joltCreateConstraint(
    world: JsAny,
    hinge: Boolean,
    idA: Int,
    idB: Int,
    ax: Double,
    ay: Double,
    az: Double,
    bx: Double,
    by: Double,
    bz: Double,
    dx: Double,
    dy: Double,
    dz: Double,
    nx: Double,
    ny: Double,
    nz: Double,
    low: Double,
    high: Double,
): JsAny

/**
 * Creates a ball-and-socket constraint and adds it, returning the JS object to hold onto.
 *
 * Its own binding rather than another branch of [joltCreateConstraint]: that one already carries
 * eighteen loose primitives because `@JsFun` cannot take a constraint type, and a third shape's
 * worth would make which argument means what unreadable.
 */
// Called from JoltPhysicsWorld.createConstraint() below; detekt cannot trace @JsFun calls.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a constraint type.
@JsFun(
    """
    (world, idA, idB, px, py, pz, tx, ty, tz, rx, ry, rz, twistMin, twistMax, swing) => {
        const jolt = world.jolt;
        const lock = world.physicsSystem.GetBodyLockInterfaceNoLock();
        const bodyA = lock.TryGetBody(new jolt.BodyID(idA));
        const bodyB = lock.TryGetBody(new jolt.BodyID(idB));
        const settings = new jolt.SixDOFConstraintSettings();
        settings.mSpace = jolt.EConstraintSpace_WorldSpace;
        settings.mPosition1 = new jolt.RVec3(px, py, pz);
        settings.mPosition2 = new jolt.RVec3(px, py, pz);
        settings.mAxisX1 = new jolt.Vec3(tx, ty, tz);
        settings.mAxisX2 = new jolt.Vec3(tx, ty, tz);
        settings.mAxisY1 = new jolt.Vec3(rx, ry, rz);
        settings.mAxisY2 = new jolt.Vec3(rx, ry, rz);
        // A ball joint pivots and does not slide, so all three translations are pinned.
        settings.MakeFixedAxis(jolt.SixDOFConstraintSettings_EAxis_TranslationX);
        settings.MakeFixedAxis(jolt.SixDOFConstraintSettings_EAxis_TranslationY);
        settings.MakeFixedAxis(jolt.SixDOFConstraintSettings_EAxis_TranslationZ);
        // Rotation about X is the twist, Y and Z together are the swing.
        if (twistMin <= twistMax) {
            settings.SetLimitedAxis(jolt.SixDOFConstraintSettings_EAxis_RotationX, twistMin, twistMax);
        } else {
            settings.MakeFreeAxis(jolt.SixDOFConstraintSettings_EAxis_RotationX);
        }
        if (swing < 0) {
            settings.MakeFreeAxis(jolt.SixDOFConstraintSettings_EAxis_RotationY);
            settings.MakeFreeAxis(jolt.SixDOFConstraintSettings_EAxis_RotationZ);
        } else {
            settings.SetLimitedAxis(jolt.SixDOFConstraintSettings_EAxis_RotationY, -swing, swing);
            settings.SetLimitedAxis(jolt.SixDOFConstraintSettings_EAxis_RotationZ, -swing, swing);
        }
        const constraint = settings.Create(bodyA, bodyB);
        world.physicsSystem.AddConstraint(constraint);
        jolt.destroy(settings);
        return constraint;
    }
    """,
)
private external fun joltCreateBallSocket(
    world: JsAny,
    idA: Int,
    idB: Int,
    px: Double,
    py: Double,
    pz: Double,
    tx: Double,
    ty: Double,
    tz: Double,
    rx: Double,
    ry: Double,
    rz: Double,
    twistMin: Double,
    twistMax: Double,
    swing: Double,
): JsAny

// Called from JoltPhysicsWorld.destroyConstraint() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun(
    """
    (world, constraint) => {
        // No jolt.destroy here: a constraint is ref-counted and RemoveConstraint releases the
        // system's reference. Freeing it by hand double-frees, and in a shared wasm heap that
        // surfaces as unrelated tests failing later rather than as a fault here.
        world.physicsSystem.RemoveConstraint(constraint);
    }
    """,
)
private external fun joltRemoveConstraint(world: JsAny, constraint: JsAny)

/**
 * One step's worth of buoyancy. Dynamic bodies only -- Jolt reaches for motion properties a static
 * body does not have.
 */
// Called from JoltPhysicsWorld.applyBuoyancy() below; detekt cannot trace @JsFun calls.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a settings type.
@JsFun(
    """
    (world, idNum, surfaceY, strength, linearDrag, angularDrag, fx, fy, fz, deltaTime) => {
        const jolt = world.jolt;
        const id = new jolt.BodyID(idNum);
        if (world.bodyInterface.GetMotionType(id) !== jolt.EMotionType_Dynamic) return;
        const g = world.physicsSystem.GetGravity();
        world.bodyInterface.ApplyBuoyancyImpulse(
            id,
            new jolt.RVec3(0, surfaceY, 0),
            new jolt.Vec3(0, 1, 0),
            strength, linearDrag, angularDrag,
            new jolt.Vec3(fx, fy, fz),
            g,
            deltaTime
        );
    }
    """,
)
private external fun joltApplyBuoyancy(
    world: JsAny,
    idNum: Int,
    surfaceY: Double,
    strength: Double,
    linearDrag: Double,
    angularDrag: Double,
    fluidX: Double,
    fluidY: Double,
    fluidZ: Double,
    deltaTime: Double,
): Unit

/** Jolt hands ids back out, so a destroyed body's reporting must not outlive it. */
// Called from JoltPhysicsWorld.createBody()/destroyBody() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun(
    """
    (world, idNum, reporting) => {
        if (reporting) world.reporting.add(idNum); else world.reporting.delete(idNum);
    }
    """,
)
private external fun joltSetReporting(world: JsAny, idNum: Int, reporting: Boolean)

/** Returns `[count, id1, id2, phase, ...]` -- one crossing for the frame, see [jsArrayGet]. */
// Called from JoltPhysicsWorld.drainContacts() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun(
    """
    (world) => {
        const out = [world.contacts.length / 3].concat(world.contacts);
        world.contacts.length = 0;
        return out;
    }
    """,
)
private external fun joltDrainContacts(world: JsAny): JsAny

// Called from JoltPhysicsWorld.setLinearVelocity() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun(
    """
    (world, idNum, vx, vy, vz) => {
        world.bodyInterface.SetLinearVelocity(new world.jolt.BodyID(idNum), new world.jolt.Vec3(vx, vy, vz));
    }
    """,
)
private external fun joltSetLinearVelocity(world: JsAny, idNum: Int, vx: Double, vy: Double, vz: Double)

// Called from JoltPhysicsWorld.setAngularVelocity() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun(
    """
    (world, idNum, vx, vy, vz) => {
        world.bodyInterface.SetAngularVelocity(new world.jolt.BodyID(idNum), new world.jolt.Vec3(vx, vy, vz));
    }
    """,
)
private external fun joltSetAngularVelocity(world: JsAny, idNum: Int, vx: Double, vy: Double, vz: Double)

/** Returns `[x, y, z]` -- one crossing rather than three. */
// Called from JoltPhysicsWorld.getLinearVelocity() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun(
    """
    (world, idNum) => {
        const v = world.bodyInterface.GetLinearVelocity(new world.jolt.BodyID(idNum));
        return [v.GetX(), v.GetY(), v.GetZ()];
    }
    """,
)
private external fun joltGetLinearVelocity(world: JsAny, idNum: Int): JsAny

// Called from JoltPhysicsWorld.addImpulse() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun(
    """
    (world, idNum, ix, iy, iz) => {
        world.bodyInterface.AddImpulse(new world.jolt.BodyID(idNum), new world.jolt.Vec3(ix, iy, iz));
    }
    """,
)
private external fun joltAddImpulse(world: JsAny, idNum: Int, ix: Double, iy: Double, iz: Double)

// Called from JoltPhysicsWorld.moveKinematic() below; detekt cannot trace @JsFun calls.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a pose type.
@JsFun(
    """
    (world, idNum, px, py, pz, qw, qx, qy, qz, deltaTime) => {
        world.bodyInterface.MoveKinematic(
            new world.jolt.BodyID(idNum),
            new world.jolt.RVec3(px, py, pz),
            new world.jolt.Quat(qx, qy, qz, qw),
            deltaTime
        );
    }
    """,
)
private external fun joltMoveKinematic(
    world: JsAny,
    idNum: Int,
    px: Double,
    py: Double,
    pz: Double,
    qw: Double,
    qx: Double,
    qy: Double,
    qz: Double,
    deltaTime: Double,
)

// Called from JoltPhysicsWorld.step() below; detekt's unused-check can't trace calls
// through an external @JsFun-bound declaration.
@Suppress("unused")
@JsFun("(world, deltaTime, collisionSteps) => { world.joltInterface.Step(deltaTime, collisionSteps); }")
private external fun joltStep(world: JsAny, deltaTime: Double, collisionSteps: Int)

/** `[id, px, py, pz, qw, qx, qy, qz]` per body -- see [joltActiveBodyTransforms]. */
private const val VALUES_PER_BODY = 8

/** A drained contact is two body ids and a phase code. */
private const val VALUES_PER_CONTACT = 3

/**
 * Every awake body's id and pose in one array: `[count, then VALUES_PER_BODY per body]`.
 *
 * One crossing for the whole set. Reading them a body at a time would put a JS boundary hop
 * between each, which is the cost this readback exists to stop scaling with the population.
 */
// Called from JoltPhysicsWorld.forEachBodyTransform() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun(
    """
    (world) => {
        const jolt = world.jolt;
        const ids = new jolt.BodyIDVector();
        world.physicsSystem.GetActiveBodies(jolt.EBodyType_RigidBody, ids);
        const out = [ids.size()];
        for (let i = 0; i < ids.size(); i++) {
            const id = ids.at(i);
            const p = world.bodyInterface.GetPosition(id);
            const r = world.bodyInterface.GetRotation(id);
            out.push(
                id.GetIndexAndSequenceNumber(),
                p.GetX(), p.GetY(), p.GetZ(),
                r.GetW(), r.GetX(), r.GetY(), r.GetZ()
            );
        }
        jolt.destroy(ids);
        return out;
    }
    """,
)
private external fun joltActiveBodyTransforms(world: JsAny): JsAny

/** Returns a plain JS array `[px, py, pz, qw, qx, qy, qz]` -- see [jsArrayGet] for why a raw
 * JS array (rather than 7 separate calls, or a Kotlin-typed return) is the simplest thing
 * that can cross this boundary (no `dynamic`, see [JoltModule]'s doc comment). */
// Called from JoltPhysicsWorld.syncTransforms() below; detekt's unused-check can't
// trace calls through an external @JsFun-bound declaration.
@Suppress("unused")
@JsFun(
    """
    (world, idNum) => {
        const id = new world.jolt.BodyID(idNum);
        const position = world.bodyInterface.GetPosition(id);
        const rotation = world.bodyInterface.GetRotation(id);
        return [
            position.GetX(), position.GetY(), position.GetZ(),
            rotation.GetW(), rotation.GetX(), rotation.GetY(), rotation.GetZ()
        ];
    }
    """,
)
private external fun joltGetBodyTransform(world: JsAny, idNum: Int): JsAny

/**
 * Moves one body by an offset, keeping its rotation.
 *
 * Activated on purpose: a sleeping body keeps its old position until something wakes it, so
 * shifting the world under one without activating leaves it behind at the old origin while
 * everything else moved.
 */
// Called from JoltPhysicsWorld.shiftOrigin() below; detekt cannot trace @JsFun calls.
@Suppress("unused")
@JsFun(
    """
    (world, idNum, dx, dy, dz) => {
        const id = new world.jolt.BodyID(idNum);
        const position = world.bodyInterface.GetPosition(id);
        const moved = new world.jolt.RVec3(
            position.GetX() + dx, position.GetY() + dy, position.GetZ() + dz
        );
        const rotation = world.bodyInterface.GetRotation(id);
        world.bodyInterface.SetPositionAndRotation(id, moved, rotation, world.jolt.EActivation_Activate);
        world.jolt.destroy(moved);
    }
    """,
)
private external fun joltTranslateBody(world: JsAny, idNum: Int, dx: Double, dy: Double, dz: Double)

/** Returns `null` on no hit, else `[fraction, bodyIdIndexAndSequenceNumber]` -- same "plain
 * JS array, no dynamic type" reasoning as [joltGetBodyTransform]. The four filter arguments
 * `NarrowPhaseQuery.CastRay` requires are each constructed from their accept-everything base
 * class (`new jolt.BroadPhaseLayerFilter()` etc.) -- the exact JS-side equivalent of the
 * desktop/iOS backends passing `null` broadphase/object-layer/body/shape filters into their
 * own raycast call. */
// Called from JoltPhysicsWorld.raycast() below; detekt's unused-check can't trace
// calls through an external @JsFun-bound declaration.
@Suppress("unused", "LongParameterList") // @JsFun carries primitives, not a ray type.
@JsFun(
    """
    (world, ox, oy, oz, dx, dy, dz, onlyLayer) => {
        const jolt = world.jolt;
        const ray = new jolt.RRayCast(new jolt.RVec3(ox, oy, oz), new jolt.Vec3(dx, dy, dz));
        const raySettings = new jolt.RayCastSettings();
        const collector = new jolt.CastRayClosestHitCollisionCollector();
        const layerFilter = onlyLayer < 0
            ? new jolt.ObjectLayerFilter()
            : new jolt.SpecifiedObjectLayerFilter(onlyLayer);
        // A sensor is not solid, so it is not what a ray is stopped by -- the same rule the sweep
        // above follows.
        const bodyFilter = new jolt.BodyFilterJS();
        bodyFilter.ShouldCollide = () => true;
        bodyFilter.ShouldCollideLocked =
            (body) => !jolt.wrapPointer(body, jolt.Body).IsSensor();
        world.physicsSystem.GetNarrowPhaseQuery().CastRay(
            ray, raySettings, collector,
            new jolt.BroadPhaseLayerFilter(), layerFilter,
            bodyFilter, new jolt.ShapeFilter()
        );
        if (!collector.HadHit()) {
            return null;
        }
        const hit = collector.mHit;
        return [hit.mFraction, hit.mBodyID.GetIndexAndSequenceNumber()];
    }
    """,
)
private external fun joltRaycast(
    world: JsAny,
    originX: Double,
    originY: Double,
    originZ: Double,
    directionX: Double,
    directionY: Double,
    directionZ: Double,
    onlyLayer: Int,
): JsAny?

// Called from syncTransforms()/raycast() below; detekt's unused-check can't trace
// calls through an external @JsFun-bound declaration.
@Suppress("unused")
@JsFun("(arr, index) => arr[index]")
private external fun jsArrayGet(arr: JsAny, index: Int): Double

// Called from JoltPhysicsWorld.destroy() below; detekt's unused-check can't trace
// calls through an external @JsFun-bound declaration.
@Suppress("unused")
@JsFun("(world) => { world.jolt.destroy(world.joltInterface); }")
private external fun joltDestroyWorld(world: JsAny)

/**
 * jolt-physics (JoltPhysics.js)-backed [PhysicsWorld] -- wasmJs only. Mirrors the desktop/
 * Android (`jolt-jni`) and iOS (`JoltC`) backends' own semantics (gravity default, body-
 * creation/tracked-body-list/syncTransforms/raycast/destroy shape) as closely as
 * JoltPhysics.js's JS-shaped API allows; see those classes' own doc comments for the parts of
 * this design this class intentionally duplicates rather than shares (no common intermediate
 * layer exists since none of the three native bindings share any code).
 *
 * Constructed via the `suspend` [create] factory, not directly -- unlike the other three
 * platforms' plain no-arg constructor, this backend needs the `jolt-physics` WASM module's
 * own async `Promise`-returning bootstrap to resolve first (see [JoltModule]'s doc comment),
 * which is why `awake:backend:jolt`'s wasmJs actual is the one platform where
 * `PhysicsWorldFactory.createPhysicsWorld()` had to become `suspend` across every platform's
 * `expect`/`actual` (see that file's own doc comment) -- `PhysicsDemo.ready()` was already
 * `suspend`, so this ripples no further than that one call site.
 */
class JoltPhysicsWorld private constructor(
    private val world: JsAny,
    override val layers: CollisionLayers,
) : PhysicsWorld {
    // Every body this PhysicsWorld created, so syncTransforms() knows what to read back and
    // destroy() knows what to tear down -- same reasoning (and the same "no cheap bulk query"
    // constraint) as the desktop/iOS backends' own `trackedBodyIds`.
    private val trackedBodyIds = mutableListOf<Int>()

    /** Live constraints and the bodies each joins; see [destroyBody] for why the pairing is kept. */
    // LinkedHashMap and LinkedHashSet, not the hash forms: teardown iterates these, and
    // destruction order decides which body ids Jolt hands back out next, which feeds its
    // island ordering and so the simulation. Insertion order replays; hash order does not.
    private val constraints = LinkedHashMap<Long, JsAny>()
    private val constraintBodies = LinkedHashMap<Long, Pair<Int, Int>>()
    private var nextConstraintId = 1L

    // Readback scratch, reused across every visited body -- see PhysicsWorld's own note.
    private val scratchOutPosition = Vec3f()
    private val scratchOutRotation = Quat()

    override fun createBody(
        shape: PhysicsShape,
        position: Vec3f,
        rotation: Quat,
        motionType: MotionType,
        layer: CollisionLayer,
        sensor: Boolean,
    ): BodyHandle {
        if (sensor) shape.requireCanBeSensor()
        val motionTypeCode = when (motionType) {
            MotionType.STATIC -> 0
            MotionType.KINEMATIC -> 1
            MotionType.DYNAMIC -> 2
        }
        val isStatic = motionType == MotionType.STATIC
        // Three shapes do not flatten into the six numbers joltCreateBody carries -- a heightfield
        // is a sample grid, a mesh a triangle list, a hull a point cloud -- so each has its own
        // entry point and this dispatches rather than falling through a chain of guards.
        val ownEntryPoint = when (shape) {
            is HeightFieldShape -> createHeightFieldBody(shape, position, motionType)
            is MeshShape -> createMeshBody(shape, position, rotation, motionType, layer)
            is ConvexHullShape ->
                createHullBody(shape, position, rotation, motionType, layer, sensor)
            else -> null
        }
        if (ownEntryPoint != null) return ownEntryPoint
        val flat = flatten(
            shape,
            "HeightFieldShape is not supported by the current JoltPhysics.js binding on wasmJs",
        )
        val idNum = joltCreateBody(
            world,
            flat.kind,
            flat.halfExtentX.toDouble(),
            flat.halfExtentY.toDouble(),
            flat.halfExtentZ.toDouble(),
            flat.radius.toDouble(),
            flat.halfHeight.toDouble(),
            position.x.toDouble(),
            position.y.toDouble(),
            position.z.toDouble(),
            rotation.w.toDouble(),
            rotation.x.toDouble(),
            rotation.y.toDouble(),
            rotation.z.toDouble(),
            motionTypeCode,
            isStatic,
            layer.index,
            sensor,
        )
        trackedBodyIds.add(idNum)
        if (sensor) joltSetReporting(world, idNum, true)
        return BodyHandle(idNum.toLong())
    }

    /**
     * ponytail: one wasm/JS crossing per height sample. Kotlin/Wasm-GC arrays have no linear
     * memory to bulk-copy from, so a per-element pass is the only way across (the same ceiling
     * `:awake:backend:webgpu`'s own `fastArrayBufferOf` documents). Fine at a body's one-time
     * cost; if streamed terrain ever makes it a frame cost, the upgrade is a bulk
     * array-to-typed-array interop intrinsic, not a different shape here.
     */
    /**
     * ponytail: vertices and indices cross one element at a time, the same ceiling the heightfield
     * samples hit -- Kotlin/Wasm-GC arrays have no linear memory to bulk-copy from. Fine at a
     * body's one-time cost; if streamed level geometry ever makes it a frame cost, the fix is a
     * bulk interop intrinsic rather than a different shape here.
     */
    @Suppress("LongParameterList") // Mirrors createBody's own parameters, one per body property.
    private fun createHullBody(
        shape: ConvexHullShape,
        position: Vec3f,
        rotation: Quat,
        motionType: MotionType,
        layer: CollisionLayer,
        sensor: Boolean,
    ): BodyHandle {
        val points = jsNewArray()
        shape.points.forEach { jsArrayPush(points, it.toDouble()) }
        val idNum = joltCreateHullBody(
            world,
            points,
            position.x.toDouble(),
            position.y.toDouble(),
            position.z.toDouble(),
            rotation.w.toDouble(),
            rotation.x.toDouble(),
            rotation.y.toDouble(),
            rotation.z.toDouble(),
            when (motionType) {
                MotionType.STATIC -> 0
                MotionType.KINEMATIC -> 1
                MotionType.DYNAMIC -> 2
            },
            motionType == MotionType.STATIC,
            layer.index,
            sensor,
        )
        trackedBodyIds.add(idNum)
        if (sensor) joltSetReporting(world, idNum, true)
        return BodyHandle(idNum.toLong())
    }

    private fun createMeshBody(
        shape: MeshShape,
        position: Vec3f,
        rotation: Quat,
        motionType: MotionType,
        layer: CollisionLayer,
    ): BodyHandle {
        shape.requireSupportedMotionType(motionType)
        val verts = jsNewArray()
        shape.vertices.forEach { jsArrayPush(verts, it.toDouble()) }
        val indices = jsNewArray()
        shape.indices.forEach { jsArrayPush(indices, it.toDouble()) }
        val idNum = joltCreateMeshBody(
            world,
            verts,
            indices,
            position.x.toDouble(),
            position.y.toDouble(),
            position.z.toDouble(),
            rotation.w.toDouble(),
            rotation.x.toDouble(),
            rotation.y.toDouble(),
            rotation.z.toDouble(),
            layer.index,
        )
        trackedBodyIds.add(idNum)
        return BodyHandle(idNum.toLong())
    }

    private fun createHeightFieldBody(
        shape: HeightFieldShape,
        position: Vec3f,
        motionType: MotionType,
    ): BodyHandle {
        shape.requireSupportedMotionType(motionType)
        val samples = jsNewArray()
        shape.heights.forEach { jsArrayPush(samples, it.toDouble()) }
        val idNum = joltCreateHeightFieldBody(
            world,
            samples,
            shape.sampleCount,
            (-(shape.sampleCount - 1) * shape.scale.x * 0.5f).toDouble(),
            (-(shape.sampleCount - 1) * shape.scale.z * 0.5f).toDouble(),
            shape.scale.x.toDouble(),
            shape.scale.y.toDouble(),
            shape.scale.z.toDouble(),
            position.x.toDouble(),
            position.y.toDouble(),
            position.z.toDouble(),
        )
        trackedBodyIds.add(idNum)
        return BodyHandle(idNum.toLong())
    }

    override fun destroyBody(handle: BodyHandle) {
        val idNum = handle.id.toInt()
        // Constraints first: Jolt does not detach them, so one still referencing a freed body
        // crashes on the next step.
        removeConstraintsFor(idNum)
        joltDestroyBody(world, idNum)
        trackedBodyIds.remove(idNum)
        // Jolt hands the id back out to the next body created, which would inherit this one's
        // contact reporting.
        joltSetReporting(world, idNum, false)
    }

    override fun setActive(handle: BodyHandle, active: Boolean) {
        joltSetActive(world, handle.id.toInt(), active)
    }

    override fun isActive(handle: BodyHandle): Boolean = joltIsActive(world, handle.id.toInt())

    override fun createConstraint(constraint: Constraint): ConstraintHandle {
        val created = when (constraint) {
            is HingeConstraint -> {
                val axis = constraint.axis.normalized()
                val normal = perpendicularTo(axis)
                joltCreateConstraint(
                    world, true,
                    constraint.bodyA.id.toInt(), constraint.bodyB.id.toInt(),
                    constraint.point.x.toDouble(), constraint.point.y.toDouble(), constraint.point.z.toDouble(),
                    0.0, 0.0, 0.0,
                    axis.x.toDouble(), axis.y.toDouble(), axis.z.toDouble(),
                    normal.x.toDouble(), normal.y.toDouble(), normal.z.toDouble(),
                    // low > high tells the far side there are no limits, since a null cannot cross.
                    (constraint.limits?.start ?: 1f).toDouble(),
                    (constraint.limits?.endInclusive ?: -1f).toDouble(),
                )
            }

            is DistanceConstraint -> joltCreateConstraint(
                world, false,
                constraint.bodyA.id.toInt(), constraint.bodyB.id.toInt(),
                constraint.pointA.x.toDouble(), constraint.pointA.y.toDouble(), constraint.pointA.z.toDouble(),
                constraint.pointB.x.toDouble(), constraint.pointB.y.toDouble(), constraint.pointB.z.toDouble(),
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                constraint.minDistance.toDouble(), constraint.maxDistance.toDouble(),
            )

            is BallSocketConstraint -> {
                val twist = constraint.twistAxis.normalized()
                val reference = constraint.swingReferenceAxis()
                joltCreateBallSocket(
                    world,
                    constraint.bodyA.id.toInt(), constraint.bodyB.id.toInt(),
                    constraint.point.x.toDouble(), constraint.point.y.toDouble(), constraint.point.z.toDouble(),
                    twist.x.toDouble(), twist.y.toDouble(), twist.z.toDouble(),
                    reference.x.toDouble(), reference.y.toDouble(), reference.z.toDouble(),
                    // min > max tells the far side there is no twist limit, since a null cannot cross.
                    (constraint.twistLimit?.start ?: 1f).toDouble(),
                    (constraint.twistLimit?.endInclusive ?: -1f).toDouble(),
                    // Negative means no swing limit, for the same reason.
                    if (constraint.swingLimit >= BallSocketConstraint.FREE_SWING) -1.0 else constraint.swingLimit.toDouble(),
                )
            }
        }
        val id = nextConstraintId++
        constraints[id] = created
        constraintBodies[id] = constraint.bodyA.id.toInt() to constraint.bodyB.id.toInt()
        return ConstraintHandle(id)
    }

    override fun destroyConstraint(handle: ConstraintHandle) {
        val constraint = constraints.remove(handle.id) ?: return
        constraintBodies.remove(handle.id)
        joltRemoveConstraint(world, constraint)
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
        joltApplyBuoyancy(
            world,
            handle.id.toInt(),
            surfaceY.toDouble(),
            buoyancy.strength.toDouble(),
            buoyancy.linearDrag.toDouble(),
            buoyancy.angularDrag.toDouble(),
            buoyancy.fluidVelocity.x.toDouble(),
            buoyancy.fluidVelocity.y.toDouble(),
            buoyancy.fluidVelocity.z.toDouble(),
            deltaTime.toDouble(),
        )
    }

    override fun setContinuousCollision(handle: BodyHandle, enabled: Boolean) {
        joltSetMotionQuality(world, handle.id.toInt(), enabled)
    }

    override fun setContactReporting(handle: BodyHandle, enabled: Boolean) {
        joltSetReporting(world, handle.id.toInt(), enabled)
    }

    override fun drainContacts(action: (ContactEvent) -> Unit) {
        // One crossing for the whole frame's events, the same shape forEachBodyTransform uses.
        val drained = joltDrainContacts(world)
        val count = jsArrayGet(drained, 0).toInt()
        for (index in 0 until count) {
            val base = 1 + index * VALUES_PER_CONTACT
            action(
                ContactEvent(
                    a = BodyHandle(jsArrayGet(drained, base).toLong()),
                    b = BodyHandle(jsArrayGet(drained, base + 1).toLong()),
                    phase = if (jsArrayGet(drained, base + 2).toInt() == 0) {
                        ContactPhase.BEGAN
                    } else {
                        ContactPhase.ENDED
                    },
                ),
            )
        }
    }

    override fun setLinearVelocity(handle: BodyHandle, velocity: Vec3f) {
        joltSetLinearVelocity(
            world,
            handle.id.toInt(),
            velocity.x.toDouble(),
            velocity.y.toDouble(),
            velocity.z.toDouble(),
        )
    }

    override fun setAngularVelocity(handle: BodyHandle, velocity: Vec3f) {
        joltSetAngularVelocity(
            world,
            handle.id.toInt(),
            velocity.x.toDouble(),
            velocity.y.toDouble(),
            velocity.z.toDouble(),
        )
    }

    override fun getLinearVelocity(handle: BodyHandle): Vec3f {
        val velocity = joltGetLinearVelocity(world, handle.id.toInt())
        return Vec3f(
            jsArrayGet(velocity, 0).toFloat(),
            jsArrayGet(velocity, 1).toFloat(),
            jsArrayGet(velocity, 2).toFloat(),
        )
    }

    override fun addImpulse(handle: BodyHandle, impulse: Vec3f) {
        joltAddImpulse(
            world,
            handle.id.toInt(),
            impulse.x.toDouble(),
            impulse.y.toDouble(),
            impulse.z.toDouble(),
        )
    }

    override fun moveKinematic(
        handle: BodyHandle,
        position: Vec3f,
        rotation: Quat,
        deltaTime: Float,
    ) {
        joltMoveKinematic(
            world,
            handle.id.toInt(),
            position.x.toDouble(),
            position.y.toDouble(),
            position.z.toDouble(),
            rotation.w.toDouble(),
            rotation.x.toDouble(),
            rotation.y.toDouble(),
            rotation.z.toDouble(),
            deltaTime.toDouble(),
        )
    }

    override fun step(deltaTime: Float) {
        val collisionSteps = 1
        joltStep(world, deltaTime.toDouble(), collisionSteps)
    }

    override fun forEachBodyTransform(
        action: (handle: BodyHandle, position: Vec3f, rotation: Quat) -> Unit,
    ) {
        // One crossing for the whole active set rather than one per body: on this backend every
        // call is a JS boundary hop, and the body count is exactly what must not scale it.
        val active = joltActiveBodyTransforms(world)
        val count = jsArrayGet(active, 0).toInt()
        for (index in 0 until count) {
            val base = 1 + index * VALUES_PER_BODY
            scratchOutPosition.set(
                jsArrayGet(active, base + 1).toFloat(),
                jsArrayGet(active, base + 2).toFloat(),
                jsArrayGet(active, base + 3).toFloat(),
            )
            scratchOutRotation.x = jsArrayGet(active, base + 5).toFloat()
            scratchOutRotation.y = jsArrayGet(active, base + 6).toFloat()
            scratchOutRotation.z = jsArrayGet(active, base + 7).toFloat()
            scratchOutRotation.w = jsArrayGet(active, base + 4).toFloat()
            action(
                BodyHandle(jsArrayGet(active, base).toLong()),
                scratchOutPosition,
                scratchOutRotation,
            )
        }
    }

    override fun overlapShape(
        shape: PhysicsShape,
        position: Vec3f,
        onlyLayer: CollisionLayer?,
        onOverlap: (BodyHandle) -> Unit,
    ) {
        val flat = flatten(
            shape,
            "A HeightFieldShape is a surface with no inside, so nothing can be inside it",
        )
        // One crossing for the whole result, the same shape forEachBodyTransform uses.
        val result = joltOverlapShape(
            world,
            flat.kind,
            flat.halfExtentX.toDouble(),
            flat.halfExtentY.toDouble(),
            flat.halfExtentZ.toDouble(),
            flat.radius.toDouble(),
            flat.halfHeight.toDouble(),
            position.x.toDouble(),
            position.y.toDouble(),
            position.z.toDouble(),
            onlyLayer?.index ?: -1,
        )
        val count = jsArrayGet(result, 0).toInt()
        for (index in 0 until count) {
            onOverlap(BodyHandle(jsArrayGet(result, 1 + index).toLong()))
        }
    }

    override fun shiftOrigin(offset: Vec3f) {
        trackedBodyIds.forEach { idNum ->
            joltTranslateBody(
                world,
                idNum,
                offset.x.toDouble(),
                offset.y.toDouble(),
                offset.z.toDouble(),
            )
        }
    }

    override fun raycast(
        origin: Vec3f,
        direction: Vec3f,
        maxDistance: Float,
        onlyLayer: CollisionLayer?,
    ): RaycastHit? {
        val normalizedDirection = direction.normalized()
        val castVector = Vec3f(
            normalizedDirection.x * maxDistance,
            normalizedDirection.y * maxDistance,
            normalizedDirection.z * maxDistance,
        )
        val result = joltRaycast(
            world,
            origin.x.toDouble(),
            origin.y.toDouble(),
            origin.z.toDouble(),
            castVector.x.toDouble(),
            castVector.y.toDouble(),
            castVector.z.toDouble(),
            onlyLayer?.index ?: -1,
        ) ?: return null

        val fraction = jsArrayGet(result, 0).toFloat()
        val bodyIdNum = jsArrayGet(result, 1).toInt()
        val distance = maxDistance * fraction
        val point = Vec3f(
            origin.x + normalizedDirection.x * distance,
            origin.y + normalizedDirection.y * distance,
            origin.z + normalizedDirection.z * distance,
        )
        return RaycastHit(BodyHandle(bodyIdNum.toLong()), point, distance)
    }

    override fun shapeCast(
        shape: PhysicsShape,
        from: Vec3f,
        to: Vec3f,
        onlyLayer: CollisionLayer?,
        ignore: BodyHandle?,
    ): ShapeCastHit? {
        val flat = flatten(
            shape,
            "A HeightFieldShape is terrain to cast against, not a shape to cast with",
        )
        val result = joltShapeCast(
            world,
            flat.kind,
            flat.halfExtentX.toDouble(),
            flat.halfExtentY.toDouble(),
            flat.halfExtentZ.toDouble(),
            flat.radius.toDouble(),
            flat.halfHeight.toDouble(),
            from.x.toDouble(),
            from.y.toDouble(),
            from.z.toDouble(),
            to.x.toDouble(),
            to.y.toDouble(),
            to.z.toDouble(),
            onlyLayer?.index ?: -1,
            // IgnoreSingleBodyFilter does the skipping inside the query, so the hit reported is
            // the nearest OTHER body -- unlike the JVM backends, which have to collect every hit
            // because jolt-jni's BodyFilter cannot be overridden.
            ignore?.id?.toInt() ?: -1,
        )
        if (jsArrayGet(result, 0).toInt() == 0) return null
        return ShapeCastHit(
            handle = BodyHandle(jsArrayGet(result, 1).toLong()),
            point = Vec3f(
                jsArrayGet(result, 2).toFloat(),
                jsArrayGet(result, 3).toFloat(),
                jsArrayGet(result, 4).toFloat(),
            ),
            normal = Vec3f(
                jsArrayGet(result, 5).toFloat(),
                jsArrayGet(result, 6).toFloat(),
                jsArrayGet(result, 7).toFloat(),
            ),
            fraction = jsArrayGet(result, 8).toFloat(),
        )
    }

    override fun destroy() {
        constraints.keys.toList().forEach { destroyConstraint(ConstraintHandle(it)) }
        trackedBodyIds.forEach { idNum -> joltDestroyBody(world, idNum) }
        trackedBodyIds.clear()
        joltDestroyWorld(world)
    }

    companion object {
        suspend fun create(
            gravity: Vec3f = Vec3f(0f, -9.81f, 0f),
            layers: CollisionLayers = CollisionLayers.Default,
        ): JoltPhysicsWorld {
            val jolt = JoltModule.get()
            // The matrix crosses as a flat row-major array, and which layers move as a parallel
            // one: the @JsFun boundary carries primitives, so the table is rebuilt on the far side
            // rather than passed as an object.
            val collides = jsNewArray()
            for (a in 0 until layers.count) {
                for (b in 0 until layers.count) {
                    val value = if (layers.collides(CollisionLayer(a), CollisionLayer(b))) 1.0 else 0.0
                    jsArrayPush(collides, value)
                }
            }
            val moving = jsNewArray()
            for (index in 0 until layers.count) {
                jsArrayPush(moving, if (layers.isMoving(CollisionLayer(index))) 1.0 else 0.0)
            }
            val world = joltCreateWorld(
                jolt,
                gravity.x.toDouble(),
                gravity.y.toDouble(),
                gravity.z.toDouble(),
                layers.count,
                collides,
                moving,
            )
            return JoltPhysicsWorld(world, layers)
        }
    }
}

/**
 * Any unit vector at right angles to [axis].
 *
 * Jolt wants a hinge's normal axis as well as its hinge axis and does not derive one; which
 * perpendicular is chosen only decides where a limit's zero angle sits, and this backend exposes
 * limits relative to the bodies' arrangement rather than to that axis.
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
