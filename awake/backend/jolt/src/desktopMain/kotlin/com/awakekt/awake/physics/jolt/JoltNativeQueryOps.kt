/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.github.stephengold.joltjni.AllHitCastRayCollector
import com.github.stephengold.joltjni.AllHitCastShapeCollector
import com.github.stephengold.joltjni.AllHitCollideShapeCollector
import com.github.stephengold.joltjni.BroadPhaseCastResult
import com.github.stephengold.joltjni.ClosestHitCastRayCollector
import com.github.stephengold.joltjni.ClosestHitCastShapeCollector
import com.github.stephengold.joltjni.CollideShapeResult
import com.github.stephengold.joltjni.NarrowPhaseQuery
import com.github.stephengold.joltjni.RRayCast
import com.github.stephengold.joltjni.RShapeCast
import com.github.stephengold.joltjni.ShapeCastResult
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.nio.FloatBuffer

/**
 * Zero-wrapper [MethodHandle] bindings to jolt-jni's static native narrow-phase query methods.
 *
 * `jolt-jni`'s public `RShapeCast` and `RRayCast` constructors have no setters and register every
 * transient instance with `JoltPhysicsObject`'s `java.lang.ref.Cleaner` (allocating an `AtomicLong`,
 * `AtomicReference`, lambda, `CleanerRunnable`, and `PhantomCleanableRef` that stays queued even
 * after `close()`), while collector hit getters allocate a fresh `JoltPhysicsObject` wrapper and
 * `Vec3` per read. Invoking the underlying static native methods directly passes raw native
 * addresses (`Long`) and reuses a world-owned direct `FloatBuffer`, eliminating all steady-state
 * query wrapper and `Cleaner` allocations while freeing native cast descriptors deterministically
 * in `finally`.
 *
 * If a future `jolt-jni` version changes any private native signature or a strict module boundary
 * denies reflection, [bound] resolves to `null` and [JoltPhysicsWorld] falls back cleanly to the
 * public `jolt-jni` API.
 */
internal object JoltNativeQueryOps {
    val bound: Bound? = bindOrNull()

    private fun bindOrNull(): Bound? = try {
        Bound(MethodHandles.lookup())
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun MethodHandles.Lookup.unreflectPrivate(method: Method): MethodHandle {
        method.isAccessible = true
        return unreflect(method)
    }

    @Suppress("TooManyFunctions") // One thin primitive call per bound jolt-jni native method.
    internal class Bound(lookup: MethodHandles.Lookup) {
        private val createRShapeCastNoBoundsHandle = lookup.unreflectPrivate(
            RShapeCast::class.java.getDeclaredMethod(
                "createRShapeCastNoBounds",
                Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ),
        )
        private val freeRShapeCastHandle = lookup.unreflectPrivate(
            RShapeCast::class.java.getDeclaredMethod("free", Long::class.javaPrimitiveType),
        )
        private val castShapeHandle = lookup.unreflectPrivate(
            NarrowPhaseQuery::class.java.getDeclaredMethod(
                "castShape",
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
            ),
        )
        private val createRRayCastHandle = lookup.unreflectPrivate(
            RRayCast::class.java.getDeclaredMethod(
                "createRRayCast",
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ),
        )
        private val freeRRayCastHandle = lookup.unreflectPrivate(
            RRayCast::class.java.getDeclaredMethod("free", Long::class.javaPrimitiveType),
        )
        private val castRayHandle = lookup.unreflectPrivate(
            NarrowPhaseQuery::class.java.getDeclaredMethod(
                "castRay",
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
            ),
        )
        private val closestCastShapeHitHandle = lookup.unreflectPrivate(
            ClosestHitCastShapeCollector::class.java.getDeclaredMethod("getHit", Long::class.javaPrimitiveType),
        )
        private val allCastShapeHitHandle = lookup.unreflectPrivate(
            AllHitCastShapeCollector::class.java.getDeclaredMethod(
                "getHit",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ),
        )
        private val shapeCastFractionHandle = lookup.unreflectPrivate(
            ShapeCastResult::class.java.getDeclaredMethod("getFraction", Long::class.javaPrimitiveType),
        )
        private val collideShapeBodyId2Handle = lookup.unreflectPrivate(
            CollideShapeResult::class.java.getDeclaredMethod("getBodyId2", Long::class.javaPrimitiveType),
        )
        private val collideShapeContactPointOn2Handle = lookup.unreflectPrivate(
            CollideShapeResult::class.java.getDeclaredMethod(
                "getContactPointOn2",
                Long::class.javaPrimitiveType,
                FloatBuffer::class.java,
            ),
        )
        private val collideShapePenetrationAxisHandle = lookup.unreflectPrivate(
            CollideShapeResult::class.java.getDeclaredMethod(
                "getPenetrationAxis",
                Long::class.javaPrimitiveType,
                FloatBuffer::class.java,
            ),
        )
        private val closestCastRayHitHandle = lookup.unreflectPrivate(
            ClosestHitCastRayCollector::class.java.getDeclaredMethod("getHit", Long::class.javaPrimitiveType),
        )
        private val allCastRayHitHandle = lookup.unreflectPrivate(
            AllHitCastRayCollector::class.java.getDeclaredMethod(
                "getHit",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ),
        )
        private val rayBodyIdHandle = lookup.unreflectPrivate(
            BroadPhaseCastResult::class.java.getDeclaredMethod("getBodyId", Long::class.javaPrimitiveType),
        )
        private val rayFractionHandle = lookup.unreflectPrivate(
            BroadPhaseCastResult::class.java.getDeclaredMethod("getFraction", Long::class.javaPrimitiveType),
        )
        private val allCollideShapeHitHandle = lookup.unreflectPrivate(
            AllHitCollideShapeCollector::class.java.getDeclaredMethod(
                "getHit",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ),
        )

        @Suppress("LongParameterList") // Matches RShapeCast.createRShapeCastNoBounds JNI signature verbatim.
        fun createRShapeCastNoBounds(
            shapeVa: Long,
            sx: Float,
            sy: Float,
            sz: Float,
            comStartVa: Long,
            dx: Float,
            dy: Float,
            dz: Float,
        ): Long = createRShapeCastNoBoundsHandle.invokeExact(shapeVa, sx, sy, sz, comStartVa, dx, dy, dz) as Long

        fun freeRShapeCast(castVa: Long) {
            freeRShapeCastHandle.invokeExact(castVa)
        }

        @Suppress("LongParameterList") // Matches NarrowPhaseQuery.castShape JNI signature verbatim.
        fun castShape(
            queryVa: Long,
            shapeCastVa: Long,
            settingsVa: Long,
            baseX: Double,
            baseY: Double,
            baseZ: Double,
            collectorVa: Long,
            bplFilterVa: Long,
            olFilterVa: Long,
            bodyFilterVa: Long,
            shapeFilterVa: Long,
        ) {
            castShapeHandle.invokeExact(
                queryVa,
                shapeCastVa,
                settingsVa,
                baseX,
                baseY,
                baseZ,
                collectorVa,
                bplFilterVa,
                olFilterVa,
                bodyFilterVa,
                shapeFilterVa,
            )
        }

        @Suppress("LongParameterList") // Matches RRayCast.createRRayCast JNI signature verbatim.
        fun createRRayCast(
            originX: Double,
            originY: Double,
            originZ: Double,
            dx: Float,
            dy: Float,
            dz: Float,
        ): Long = createRRayCastHandle.invokeExact(originX, originY, originZ, dx, dy, dz) as Long

        fun freeRRayCast(rayVa: Long) {
            freeRRayCastHandle.invokeExact(rayVa)
        }

        @Suppress("LongParameterList") // Matches NarrowPhaseQuery.castRay JNI signature verbatim.
        fun castRay(
            queryVa: Long,
            rayVa: Long,
            settingsVa: Long,
            collectorVa: Long,
            bplFilterVa: Long,
            olFilterVa: Long,
            bodyFilterVa: Long,
            shapeFilterVa: Long,
        ) {
            castRayHandle.invokeExact(
                queryVa,
                rayVa,
                settingsVa,
                collectorVa,
                bplFilterVa,
                olFilterVa,
                bodyFilterVa,
                shapeFilterVa,
            )
        }

        fun closestCastShapeHit(collectorVa: Long): Long =
            closestCastShapeHitHandle.invokeExact(collectorVa) as Long

        fun allCastShapeHit(collectorVa: Long, index: Int): Long =
            allCastShapeHitHandle.invokeExact(collectorVa, index) as Long

        fun shapeCastFraction(hitVa: Long): Float =
            shapeCastFractionHandle.invokeExact(hitVa) as Float

        fun collideShapeBodyId2(hitVa: Long): Int =
            collideShapeBodyId2Handle.invokeExact(hitVa) as Int

        fun collideShapeContactPointOn2(hitVa: Long, storeFloats: FloatBuffer) {
            collideShapeContactPointOn2Handle.invokeExact(hitVa, storeFloats)
        }

        fun collideShapePenetrationAxis(hitVa: Long, storeFloats: FloatBuffer) {
            collideShapePenetrationAxisHandle.invokeExact(hitVa, storeFloats)
        }

        fun closestCastRayHit(collectorVa: Long): Long =
            closestCastRayHitHandle.invokeExact(collectorVa) as Long

        fun allCastRayHit(collectorVa: Long, index: Int): Long =
            allCastRayHitHandle.invokeExact(collectorVa, index) as Long

        fun rayBodyId(hitVa: Long): Int =
            rayBodyIdHandle.invokeExact(hitVa) as Int

        fun rayFraction(hitVa: Long): Float =
            rayFractionHandle.invokeExact(hitVa) as Float

        fun allCollideShapeHit(collectorVa: Long, index: Int): Long =
            allCollideShapeHitHandle.invokeExact(collectorVa, index) as Long
    }
}
