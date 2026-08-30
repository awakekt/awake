/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.math

/**
 * Writes [v] at [offset] and returns the offset after it.
 *
 * Returning the next offset is the point: a packer writes several values in sequence, and
 * threading the offset through means no call site does `+ 1`/`+ 2`/`+ 3` arithmetic it can get
 * wrong. Chain them and the offsets take care of themselves:
 *
 * ```
 * var i = 0
 * i = out.putVec3(i, position)
 * i = out.putFloat(i, range)
 * ```
 */
fun FloatArray.putVec3(offset: Int, v: Vec3f): Int {
    this[offset] = v.x
    this[offset + 1] = v.y
    this[offset + 2] = v.z
    return offset + 3
}

/**
 * Writes [v]'s xyz then [w], returning the offset after.
 *
 * The four-float form exists because a uniform block pads every `vec3` to `vec4` anyway
 * ([GpuDataShape.uniformFloats]), so the fourth slot is there whether or not it carries anything.
 * Several shaders use it deliberately -- `lightDirection.w` holds the shadow depth scale,
 * `fogColor.a` holds density, a point light's `w` holds its range.
 */
fun FloatArray.putVec4(
    offset: Int,
    v: Vec3f,
    w: Float = 0f,
): Int {
    putVec3(offset, v)
    this[offset + 3] = w
    return offset + 4
}

/** Writes [v] at [offset], returning the offset after. */
fun FloatArray.putVec4(offset: Int, v: Vec4): Int {
    putVec3(offset, Vec3f(v.x, v.y, v.z))
    this[offset + 3] = v.w
    return offset + 4
}

/** Writes one scalar at [offset], returning the offset after -- so a scalar can sit in a chain
 * of [putVec3]/[putVec4] calls without breaking the pattern. */
fun FloatArray.putFloat(offset: Int, value: Float): Int {
    this[offset] = value
    return offset + 1
}

/**
 * Squared distance from this transform's translation to [point], allocating nothing.
 *
 * Squared, and no intermediate `Vec3`: the caller is a depth sort running per draw per frame, and
 * it needs the ordering, not the distance. A square root and a temporary vector per comparison are
 * both pure cost there -- see `skills/awake-core-math` on allocating inside the frame path.
 */
fun Mat4.squaredDistanceFrom(point: Vec3f): Float {
    val dx = m03 - point.x
    val dy = m13 - point.y
    val dz = m23 - point.z
    return dx * dx + dy * dy + dz * dz
}
