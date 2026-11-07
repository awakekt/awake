/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlin.math.abs
import kotlin.math.cbrt

/**
 * An emitter entity's frame, and its inverse, kept as two 3 x 4 affine matrices that are rewritten in
 * place: what a [ParticleSpace.Local] emitter maps its particles through, from the world into its pool
 * when they spawn and back out when they are drawn or reported. Loading and mapping allocate nothing.
 *
 * Translation comes from [EmitterPlacement.position] and the axes, scale included, from
 * [EmitterPlacement.orientation]: a placement's position can be fresher than its matrix (a scene's root
 * node is where its own position says before the transform pass has run), and an emitter following its
 * entity spawns at that position, so its local origin is exactly zero.
 */
internal class EmitterFrame {
    private val forward = FloatArray(AFFINE_FLOATS)
    private val inverse = FloatArray(AFFINE_FLOATS)
    private val position = Vec3f(0f, 0f, 0f)

    /** How much the frame scales a particle's size: the cube root of its axes' volume. */
    var sizeScale: Float = 1f
        private set

    init {
        setIdentity()
    }

    /** Loads [entity]'s frame as [placement] reports it; the world's own frame where it reports none. */
    fun load(placement: EmitterPlacement, world: World, entity: Entity) {
        val matrix = placement.orientation(world, entity)
        if (matrix == null) {
            setIdentity()
        } else {
            forward.setRow(0, matrix.m00, matrix.m01, matrix.m02, matrix.m03)
            forward.setRow(1, matrix.m10, matrix.m11, matrix.m12, matrix.m13)
            forward.setRow(2, matrix.m20, matrix.m21, matrix.m22, matrix.m23)
        }
        if (placement.position(world, entity, position)) {
            forward[TX] = position.x
            forward[TY] = position.y
            forward[TZ] = position.z
        }
        invert()
    }

    /** Copies [other]'s frame into this one. */
    fun copyFrom(other: EmitterFrame) {
        other.forward.copyInto(forward)
        other.inverse.copyInto(inverse)
        sizeScale = other.sizeScale
    }

    /** The world's own frame: mapping through it changes nothing. */
    fun setIdentity() {
        forward.fill(0f)
        forward[0] = 1f
        forward[ROW + 1] = 1f
        forward[2 * ROW + 2] = 1f
        forward.copyInto(inverse)
        sizeScale = 1f
    }

    /** Writes `(x, y, z)` mapped from this frame into the world into [into]; a [point] is also moved. */
    fun toWorld(x: Float, y: Float, z: Float, into: Vec3f, point: Boolean = true) = forward.mapInto(x, y, z, into, point)

    /** Writes `(x, y, z)` mapped from the world into this frame into [into]; a [point] is also moved. */
    fun toLocal(x: Float, y: Float, z: Float, into: Vec3f, point: Boolean = true) = inverse.mapInto(x, y, z, into, point)

    // The inverse of an affine matrix: the 3 x 3's adjugate over its determinant, and the translation
    // undone through it. Axes that collapse a dimension have no inverse, so the axes are left out of it.
    private fun invert() {
        val f = forward
        val c00 = f[ROW + 1] * f[2 * ROW + 2] - f[ROW + 2] * f[2 * ROW + 1]
        val c01 = f[2] * f[2 * ROW + 1] - f[1] * f[2 * ROW + 2]
        val c02 = f[1] * f[ROW + 2] - f[2] * f[ROW + 1]
        val det = f[0] * c00 + f[ROW] * c01 + f[2 * ROW] * c02
        if (abs(det) < MIN_DETERMINANT) {
            inverse.fill(0f)
            inverse[0] = 1f
            inverse[ROW + 1] = 1f
            inverse[2 * ROW + 2] = 1f
            sizeScale = 0f
        } else {
            val s = 1f / det
            inverse.setRow(0, c00 * s, c01 * s, c02 * s, 0f)
            inverse.setRow(
                1,
                (f[ROW + 2] * f[2 * ROW] - f[ROW] * f[2 * ROW + 2]) * s,
                (f[0] * f[2 * ROW + 2] - f[2] * f[2 * ROW]) * s,
                (f[2] * f[ROW] - f[0] * f[ROW + 2]) * s,
                0f,
            )
            inverse.setRow(
                2,
                (f[ROW] * f[2 * ROW + 1] - f[ROW + 1] * f[2 * ROW]) * s,
                (f[1] * f[2 * ROW] - f[0] * f[2 * ROW + 1]) * s,
                (f[0] * f[ROW + 1] - f[1] * f[ROW]) * s,
                0f,
            )
            sizeScale = cbrt(abs(det))
        }
        inverse.mapInto(f[TX], f[TY], f[TZ], position, point = false)
        inverse[TX] = -position.x
        inverse[TY] = -position.y
        inverse[TZ] = -position.z
    }
}

private fun FloatArray.setRow(row: Int, x: Float, y: Float, z: Float, w: Float) {
    val at = row * ROW
    this[at] = x
    this[at + 1] = y
    this[at + 2] = z
    this[at + 3] = w
}

private fun FloatArray.mapInto(x: Float, y: Float, z: Float, into: Vec3f, point: Boolean) {
    val w = if (point) 1f else 0f
    into.set(
        this[0] * x + this[1] * y + this[2] * z + this[TX] * w,
        this[ROW] * x + this[ROW + 1] * y + this[ROW + 2] * z + this[TY] * w,
        this[2 * ROW] * x + this[2 * ROW + 1] * y + this[2 * ROW + 2] * z + this[TZ] * w,
    )
}

private const val ROW = 4
private const val AFFINE_FLOATS = 12
private const val TX = 3
private const val TY = ROW + 3
private const val TZ = 2 * ROW + 3
private const val MIN_DETERMINANT = 1e-12f
