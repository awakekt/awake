/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics

import io.github.awakelab.awake.core.math.GridOrigin
import io.github.awakelab.awake.core.math.Vec3f

/** Backend-neutral collision shape description -- a plain data description, not a live
 * native handle, so `PhysicsWorld.createBody` (the only place a [PhysicsShape] is consumed)
 * can build whatever native shape object its own backend needs (jolt-jni's `BoxShape`/
 * `SphereShape`, eventually JoltC's C shape structs) without this module knowing any of
 * that exists. */
sealed interface PhysicsShape

/** [halfExtents] matches Jolt Physics' own `BoxShape` convention (half the box's total
 * width/height/depth along each axis), not a full-extent size. */
data class BoxShape(val halfExtents: Vec3f) : PhysicsShape

/** The cheapest shape to collide and the only one with no orientation to get wrong. */
data class SphereShape(val radius: Float) : PhysicsShape

/**
 * An upright capsule: a cylinder of `2 * [halfHeight]` topped and tailed by hemispheres of
 * [radius]. Total height is therefore `2 * (halfHeight + radius)`, which is the number that
 * matters when sizing a character and the one Jolt's own constructor does *not* take -- it
 * takes the cylinder's half height, and this matches it rather than quietly redefining it.
 *
 * The character shape. A capsule slides over steps and ledges where a box catches on them,
 * which is why every engine's character controller is built on one.
 */
data class CapsuleShape(val halfHeight: Float, val radius: Float) : PhysicsShape {
    init {
        require(halfHeight.isFinite() && halfHeight > 0f) { "halfHeight must be positive: $halfHeight" }
        require(radius.isFinite() && radius > 0f) { "radius must be positive: $radius" }
    }
}

/**
 * A square, row-major heightfield collision surface. The local body origin is sample (0, 0):
 * its world position is [PhysicsWorld.createBody]'s [PhysicsWorld.createBody] position. A
 * sample at `(x, z)` is located at `(x * scale.x, heights[z * sampleCount + x] * scale.y,
 * z * scale.z)` relative to that origin.
 *
 * The array is intentionally not copied: constructing a terrain-sized collider must not make
 * an implicit second terrain-sized allocation. Callers must therefore not mutate [heights]
 * after passing this value to a [PhysicsWorld]. It is not suitable as a structural cache key;
 * Kotlin arrays use referential equality in data classes.
 *
 * The field is **centred on the body position** by default, like [BoxShape] and [SphereShape]:
 * sample `(x, z)` sits at `position + (x * scale.x - halfWidth, height * scale.y, z * scale.z -
 * halfDepth)`. A corner-anchored field put the body's position off one edge of its own collider,
 * which disagrees with every other shape and with the meshes it is built from.
 *
 * Pass [GridOrigin.Corner] for a field built from a corner-anchored heightmap -- the collider has
 * to sit where its mesh sits, so this must match the map it came from.
 */
data class HeightFieldShape(
    val heights: FloatArray,
    val sampleCount: Int,
    val scale: Vec3f,
    val origin: GridOrigin = GridOrigin.Centered,
) : PhysicsShape {
    init {
        require(sampleCount >= MIN_SAMPLE_COUNT) {
            "sampleCount must be at least $MIN_SAMPLE_COUNT for a Jolt heightfield: $sampleCount"
        }
        val expectedHeightCount = sampleCount.toLong() * sampleCount
        require(expectedHeightCount <= Int.MAX_VALUE && heights.size == expectedHeightCount.toInt()) {
            "heights must contain sampleCount * sampleCount values: " +
                "expected $expectedHeightCount, got ${heights.size}"
        }
        require(scale.x.isFinite() && scale.y.isFinite() && scale.z.isFinite()) {
            "scale must contain only finite values: $scale"
        }
        require(scale.x > 0f && scale.y > 0f && scale.z > 0f) {
            "scale components must be positive: $scale"
        }
        require(heights.all(Float::isFinite)) { "heights must contain only finite values" }
    }

    /** Height sample at [x], [z], using the documented row-major layout. */
    fun heightAt(x: Int, z: Int): Float {
        require(x in 0 until sampleCount) { "x is outside the heightfield: $x" }
        require(z in 0 until sampleCount) { "z is outside the heightfield: $z" }
        return heights[z * sampleCount + x]
    }

    /** Jolt heightfields cannot be dynamic or kinematic bodies. */
    fun requireSupportedMotionType(motionType: MotionType) {
        if (motionType != MotionType.STATIC) {
            throw PhysicsCapabilityException("HeightFieldShape supports STATIC motion only, not $motionType")
        }
    }

    private companion object {
        // Jolt's default block size is two samples; it requires at least two blocks per edge.
        const val MIN_SAMPLE_COUNT = 4
    }
}

/**
 * An arbitrary triangle mesh: the shape for authored level geometry that is not a box.
 *
 * [vertices] is `x, y, z` per vertex, and [indices] three per triangle into it -- the same layout
 * the renderer's own geometry uses, so a collider can be built from the mesh being drawn rather
 * than from a second description of it that can disagree.
 *
 * **Winding decides which side is solid.** Jolt collides with one face of a triangle, the one its
 * winding faces. A floor whose triangles wind the wrong way is not a floor with a subtle bug in
 * it -- bodies fall straight through, while rays still hit it from both sides, which is a
 * combination that reads as anything except a winding problem. Wind counter-clockwise as seen
 * from the side that should be solid.
 *
 * **Static only.** A triangle mesh is a surface rather than a solid: it has no inside, so it has
 * no volume, no mass and no sensible answer for what "inside it" means. Jolt refuses to simulate
 * one, and so does [requireSupportedMotionType]. A dynamic prop wants [ConvexHullShape].
 *
 * Neither array is copied, for the same reason [HeightFieldShape]'s is not: a level-sized collider
 * must not silently make a second level-sized allocation. Do not mutate either after handing it to
 * a [PhysicsWorld].
 */
data class MeshShape(
    val vertices: FloatArray,
    val indices: IntArray,
) : PhysicsShape {
    init {
        require(vertices.isNotEmpty()) { "a mesh shape needs vertices" }
        require(vertices.size % VALUES_PER_VERTEX == 0) {
            "vertices must be x, y, z per vertex: ${vertices.size} is not a multiple of $VALUES_PER_VERTEX"
        }
        require(indices.isNotEmpty() && indices.size % INDICES_PER_TRIANGLE == 0) {
            "indices must be three per triangle: ${indices.size}"
        }
        val vertexCount = vertices.size / VALUES_PER_VERTEX
        require(indices.all { it in 0 until vertexCount }) {
            "every index must point at one of the $vertexCount vertices"
        }
        require(vertices.all(Float::isFinite)) { "vertices must contain only finite values" }
    }

    /** How many triangles this mesh collides as. */
    val triangleCount: Int get() = indices.size / INDICES_PER_TRIANGLE

    /** Jolt cannot simulate a surface with no inside. */
    fun requireSupportedMotionType(motionType: MotionType) {
        if (motionType != MotionType.STATIC) {
            throw PhysicsCapabilityException(
                "MeshShape supports STATIC motion only, not $motionType -- use a ConvexHullShape " +
                    "for something that moves",
            )
        }
    }

    private companion object {
        const val VALUES_PER_VERTEX = 3
        const val INDICES_PER_TRIANGLE = 3
    }
}

/**
 * The convex hull of a point cloud: the shape for a prop that moves and is not a box.
 *
 * Convex because the solver needs it to be. A hull has an inside, so it has volume and mass and
 * can be dynamic -- which is exactly what [MeshShape] cannot do. Concavity is lost: hand it a
 * chair and it collides as the shrink-wrap around one. A concave dynamic object is several hulls,
 * which is a compound shape and not this.
 *
 * [points] is `x, y, z` per point, and Jolt builds the hull; interior points are simply ignored,
 * so passing a mesh's vertices is a legitimate way to get a hull of it.
 */
data class ConvexHullShape(val points: FloatArray) : PhysicsShape {
    init {
        require(points.size >= VALUES_PER_POINT * MIN_POINTS) {
            "a hull needs at least $MIN_POINTS points: ${points.size / VALUES_PER_POINT}"
        }
        require(points.size % VALUES_PER_POINT == 0) {
            "points must be x, y, z per point: ${points.size} is not a multiple of $VALUES_PER_POINT"
        }
        require(points.all(Float::isFinite)) { "points must contain only finite values" }
    }

    /** How many points the hull is built from, before Jolt discards the interior ones. */
    val pointCount: Int get() = points.size / VALUES_PER_POINT

    private companion object {
        const val VALUES_PER_POINT = 3

        /** Fewer than four points cannot enclose a volume. */
        const val MIN_POINTS = 4
    }
}

/** A requested physics capability is not available on the selected backend or body type. */
class PhysicsCapabilityException(message: String) : UnsupportedOperationException(message)

/**
 * Rejects a sensor whose shape encloses nothing.
 *
 * A sensor answers "what is inside me", and [MeshShape] and [HeightFieldShape] are surfaces with no
 * inside -- so the question has no answer for either, on any backend. Rejecting it here beats a
 * trigger volume that is built without complaint and then never fires.
 */
fun PhysicsShape.requireCanBeSensor() {
    if (this is MeshShape || this is HeightFieldShape) {
        throw PhysicsCapabilityException(
            "${this::class.simpleName} is a surface with no inside, so it cannot be a sensor -- " +
                "use a BoxShape or a ConvexHullShape for a trigger volume",
        )
    }
}
