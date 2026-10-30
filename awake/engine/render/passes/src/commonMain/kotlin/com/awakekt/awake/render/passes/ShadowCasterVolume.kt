/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.BoundedVolume
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.MAX_SHADOW_CASCADES
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The world-space volume whose geometry can write a texel of one frame's shadow maps.
 *
 * A caster does not have to be in front of the camera to shadow what the camera sees: a tree just
 * off screen, with a low sun, shades the visible ground. So which meshes reach the shadow pass is a
 * different question from which reach the scene pass, and this answers it. Each directional
 * cascade contributes its depth map's box with the side nearest the light removed, so the box
 * reaches back toward the light without limit; a tall caster standing between the light and the
 * box still counts. Each shadowed point light contributes the cube its six faces cover.
 *
 * The same planes the shadow pass culls each cascade's casters with, so a mesh this admits is one
 * at least one shadow pass can draw, and one it rejects is one none of them would.
 *
 * Reused frame to frame: [set] rewrites it in place and nothing here allocates once its arrays have
 * grown to the scene's light count.
 */
class ShadowCasterVolume : BoundedVolume {
    private val cascadePlanes = FloatArray(MAX_SHADOW_CASCADES * CASTING_PLANE_FLOATS)

    /** Per cascade, the four corners of its far side, then its unit direction toward the light. */
    private val cascadeShape = FloatArray(MAX_SHADOW_CASCADES * CASCADE_SHAPE_FLOATS)

    /** Per shadowed point light, the min and max of the cube its faces cover. */
    private var pointBoxes = FloatArray(INITIAL_POINT_CAPACITY * BOX_FLOATS)

    /** A cascade whose far corners could not be solved for, such as a zero matrix: it has no bound. */
    private var unbounded = false

    /** How many directional cascades the volume holds. */
    var cascadeCount: Int = 0
        private set

    /** How many shadowed point lights the volume holds. */
    var pointShadowCount: Int = 0
        private set

    /** True when no shadow map is rendered this frame, so nothing can cast. */
    val isEmpty: Boolean get() = cascadeCount == 0 && pointShadowCount == 0

    /** Empties the volume. */
    fun clear(): ShadowCasterVolume {
        cascadeCount = 0
        pointShadowCount = 0
        unbounded = false
        return this
    }

    /**
     * The volume the shadow passes compiled from [light] and [environment] cover: the same gate
     * `ScenePassCompiler` applies, so a scene with shadows off leaves this empty.
     */
    fun set(light: SceneLight?, environment: EnvironmentUniforms): ShadowCasterVolume {
        clear()
        if (light == null || !environment.shadowsEnabled) return this
        shadowCascadeData(light, environment)?.viewProjections?.forEach(::addCascade)
        for (index in light.points.indices) {
            val point = light.points[index]
            if (point.shadowBaseLayer >= 0) addPointShadow(point.position, point.range)
        }
        return this
    }

    /** Adds the cascade [viewProjection] renders, extended without limit toward its light. */
    fun addCascade(viewProjection: Mat4) {
        require(cascadeCount < MAX_SHADOW_CASCADES) { "At most $MAX_SHADOW_CASCADES cascades cast shadows." }
        val planeOffset = cascadeCount * CASTING_PLANE_FLOATS
        writeCastingPlanes(viewProjection, cascadePlanes, planeOffset)
        if (!writeCascadeShape(cascadePlanes, planeOffset, cascadeShape, cascadeCount * CASCADE_SHAPE_FLOATS)) unbounded = true
        cascadeCount += 1
    }

    /** Adds the cube a point light at [position] shadows out to [range]. */
    fun addPointShadow(position: Vec3f, range: Float) {
        if ((pointShadowCount + 1) * BOX_FLOATS > pointBoxes.size) pointBoxes = pointBoxes.copyOf(pointBoxes.size * 2)
        val offset = pointShadowCount * BOX_FLOATS
        pointBoxes[offset] = position.x - range
        pointBoxes[offset + 1] = position.y - range
        pointBoxes[offset + 2] = position.z - range
        pointBoxes[offset + 3] = position.x + range
        pointBoxes[offset + 4] = position.y + range
        pointBoxes[offset + 5] = position.z + range
        pointShadowCount += 1
    }

    /** Whether a caster with world bounds [box] can write a texel of any shadow map. */
    override fun admits(box: Aabb): Boolean = admits(box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z)

    /** [admits] for a box given as its min and max corners, without building one. */
    @Suppress("LongParameterList")
    override fun admits(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): Boolean =
        admittedByCascade(minX, minY, minZ, maxX, maxY, maxZ) || admittedByPointShadow(minX, minY, minZ, maxX, maxY, maxZ)

    @Suppress("LongParameterList")
    private fun admittedByCascade(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): Boolean {
        for (cascade in 0 until cascadeCount) {
            if (cascadePlanes.admitsBox(cascade * CASTING_PLANE_FLOATS, minX, minY, minZ, maxX, maxY, maxZ)) return true
        }
        return false
    }

    @Suppress("LongParameterList")
    private fun admittedByPointShadow(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): Boolean {
        for (point in 0 until pointShadowCount) {
            val o = point * BOX_FLOATS
            val overlapsX = minX <= pointBoxes[o + 3] && maxX >= pointBoxes[o]
            val overlapsY = minY <= pointBoxes[o + 4] && maxY >= pointBoxes[o + 1]
            val overlapsZ = minZ <= pointBoxes[o + 5] && maxZ >= pointBoxes[o + 2]
            if (overlapsX && overlapsY && overlapsZ) return true
        }
        return false
    }

    /**
     * Writes into [out] a box holding every part of this volume that lies inside [content], the
     * bounds of everything that could cast; false, leaving [out] undefined, when none does.
     *
     * The cascades reach toward their light without limit, but a caster has to lie inside
     * [content], so each is cut where its edges leave [content]'s height range (or, for a light
     * near the horizon, its footprint). This is what lets a spatial index visit a neighbourhood
     * for the casters instead of the whole scene.
     */
    override fun bounds(content: Aabb, out: Aabb): Boolean {
        if (isEmpty) return false
        if (unbounded) {
            out.min.set(content.min.x, content.min.y, content.min.z)
            out.max.set(content.max.x, content.max.y, content.max.z)
        } else {
            writeReach(content, out)
        }
        return out.min.x <= out.max.x && out.min.y <= out.max.y && out.min.z <= out.max.z
    }

    /** Every cascade's far side swept toward its light as far as [content] reaches, and every point cube, cut to [content]. */
    private fun writeReach(content: Aabb, out: Aabb) {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        for (cascade in 0 until cascadeCount) {
            val o = cascade * CASCADE_SHAPE_FLOATS
            val reach = reachTowardLight(cascadeShape, o, content)
            val dx = cascadeShape[o + DIRECTION_OFFSET] * reach
            val dy = cascadeShape[o + DIRECTION_OFFSET + 1] * reach
            val dz = cascadeShape[o + DIRECTION_OFFSET + 2] * reach
            for (corner in 0 until FAR_CORNERS) {
                val x = cascadeShape[o + corner * 3]
                val y = cascadeShape[o + corner * 3 + 1]
                val z = cascadeShape[o + corner * 3 + 2]
                minX = min(minX, min(x, x + dx))
                minY = min(minY, min(y, y + dy))
                minZ = min(minZ, min(z, z + dz))
                maxX = max(maxX, max(x, x + dx))
                maxY = max(maxY, max(y, y + dy))
                maxZ = max(maxZ, max(z, z + dz))
            }
        }
        for (point in 0 until pointShadowCount) {
            val o = point * BOX_FLOATS
            minX = min(minX, pointBoxes[o])
            minY = min(minY, pointBoxes[o + 1])
            minZ = min(minZ, pointBoxes[o + 2])
            maxX = max(maxX, pointBoxes[o + 3])
            maxY = max(maxY, pointBoxes[o + 4])
            maxZ = max(maxZ, pointBoxes[o + 5])
        }
        out.min.set(max(minX, content.min.x), max(minY, content.min.y), max(minZ, content.min.z))
        out.max.set(min(maxX, content.max.x), min(maxY, content.max.y), min(maxZ, content.max.z))
    }
}

/**
 * How far along its light direction a cascade's far side has to travel before every point on it
 * has left [content]: past that, nothing the extended box holds can be a caster.
 */
private fun reachTowardLight(shape: FloatArray, offset: Int, content: Aabb): Float {
    val dy = shape[offset + DIRECTION_OFFSET + 1]
    var reach = 0f
    // Leaving the content's height range, which for any sun above the horizon is what binds.
    if (abs(dy) > HORIZONTAL_LIMIT) {
        val bound = if (dy > 0f) content.max.y else content.min.y
        for (corner in 0 until FAR_CORNERS) reach = max(reach, (bound - shape[offset + corner * 3 + 1]) / dy)
    } else {
        reach = Float.POSITIVE_INFINITY
    }
    // Leaving the box holding both the content and the far side, which bounds a light that grazes
    // the horizon: a unit step longer than that box's diagonal leaves it from any point inside.
    var minX = content.min.x
    var minY = content.min.y
    var minZ = content.min.z
    var maxX = content.max.x
    var maxY = content.max.y
    var maxZ = content.max.z
    for (corner in 0 until FAR_CORNERS) {
        val c = offset + corner * 3
        minX = min(minX, shape[c])
        minY = min(minY, shape[c + 1])
        minZ = min(minZ, shape[c + 2])
        maxX = max(maxX, shape[c])
        maxY = max(maxY, shape[c + 1])
        maxZ = max(maxZ, shape[c + 2])
    }
    val sx = maxX - minX
    val sy = maxY - minY
    val sz = maxZ - minZ
    return min(reach, sqrt(sx * sx + sy * sy + sz * sz))
}

/**
 * Writes the left, right, bottom, top and far clip planes of [viewProjection] into [out] at
 * [offset], as `(x, y, z, w)` quads. The near plane is left out on purpose: see [ShadowCasterVolume].
 */
internal fun writeCastingPlanes(viewProjection: Mat4, out: FloatArray, offset: Int) {
    val m = viewProjection.data
    for (plane in 0 until CASTING_PLANE_COUNT) {
        val row = CASTING_PLANE_ROWS[plane]
        val sign = CASTING_PLANE_SIGNS[plane]
        // Column-major: row r of the matrix is m[r], m[4 + r], m[8 + r], m[12 + r].
        for (column in 0 until PLANE_FLOATS) {
            out[offset + plane * PLANE_FLOATS + column] = m[column * 4 + W_ROW] + sign * m[column * 4 + row]
        }
    }
}

/** Whether a box is at least partly inside every casting plane at [offset]. A zero matrix admits everything. */
@Suppress("LongParameterList")
internal fun FloatArray.admitsBox(offset: Int, minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): Boolean {
    for (plane in 0 until CASTING_PLANE_COUNT) {
        val p = offset + plane * PLANE_FLOATS
        val x = if (this[p] > 0f) maxX else minX
        val y = if (this[p + 1] > 0f) maxY else minY
        val z = if (this[p + 2] > 0f) maxZ else minZ
        if (this[p] * x + this[p + 1] * y + this[p + 2] * z + this[p + 3] < 0f) return false
    }
    return true
}

/**
 * The four far-side corners of the casting planes at [planeOffset], then the unit direction toward
 * the light, into [out] at [outOffset]. False when the planes do not meet in corners.
 */
private fun writeCascadeShape(planes: FloatArray, planeOffset: Int, out: FloatArray, outOffset: Int): Boolean {
    val far = planeOffset + FAR_PLANE * PLANE_FLOATS
    var solved = true
    for (corner in 0 until FAR_CORNERS) {
        val side = planeOffset + (corner and 1) * PLANE_FLOATS
        val vertical = planeOffset + (2 + (corner shr 1)) * PLANE_FLOATS
        solved = solved && intersect(planes, side, vertical, far, out, outOffset + corner * 3)
    }
    // The far plane keeps what is nearer the light than it, so its normal points at the light.
    val nx = planes[far]
    val ny = planes[far + 1]
    val nz = planes[far + 2]
    val length = sqrt(nx * nx + ny * ny + nz * nz)
    if (!solved || length < SINGULAR_LIMIT) return false
    out[outOffset + DIRECTION_OFFSET] = nx / length
    out[outOffset + DIRECTION_OFFSET + 1] = ny / length
    out[outOffset + DIRECTION_OFFSET + 2] = nz / length
    return true
}

/** The point where planes [a], [b] and [c] meet, by Cramer's rule, into [out] at [outOffset]. */
@Suppress("LongParameterList")
private fun intersect(planes: FloatArray, a: Int, b: Int, c: Int, out: FloatArray, outOffset: Int): Boolean {
    val a0 = planes[a]
    val a1 = planes[a + 1]
    val a2 = planes[a + 2]
    val b0 = planes[b]
    val b1 = planes[b + 1]
    val b2 = planes[b + 2]
    val c0 = planes[c]
    val c1 = planes[c + 1]
    val c2 = planes[c + 2]
    val det = a0 * (b1 * c2 - b2 * c1) - a1 * (b0 * c2 - b2 * c0) + a2 * (b0 * c1 - b1 * c0)
    if (abs(det) < SINGULAR_LIMIT) return false
    val da = -planes[a + 3]
    val db = -planes[b + 3]
    val dc = -planes[c + 3]
    out[outOffset] = (da * (b1 * c2 - b2 * c1) - a1 * (db * c2 - b2 * dc) + a2 * (db * c1 - b1 * dc)) / det
    out[outOffset + 1] = (a0 * (db * c2 - b2 * dc) - da * (b0 * c2 - b2 * c0) + a2 * (b0 * dc - db * c0)) / det
    out[outOffset + 2] = (a0 * (b1 * dc - db * c1) - a1 * (b0 * dc - db * c0) + da * (b0 * c1 - b1 * c0)) / det
    return true
}

private const val W_ROW = 3
private const val PLANE_FLOATS = 4

/** Left, right, bottom, top, far: `w + x`, `w - x`, `w + y`, `w - y`, `w - z`. */
private const val CASTING_PLANE_COUNT = 5
private val CASTING_PLANE_ROWS = intArrayOf(0, 0, 1, 1, 2)
private val CASTING_PLANE_SIGNS = floatArrayOf(1f, -1f, 1f, -1f, -1f)
private const val FAR_PLANE = 4

/** Floats one cascade's casting planes take. */
internal const val CASTING_PLANE_FLOATS = CASTING_PLANE_COUNT * PLANE_FLOATS

private const val FAR_CORNERS = 4
private const val DIRECTION_OFFSET = FAR_CORNERS * 3
private const val CASCADE_SHAPE_FLOATS = DIRECTION_OFFSET + 3
private const val BOX_FLOATS = 6
private const val INITIAL_POINT_CAPACITY = 4

/** Below this a light direction's vertical part is too small to bound its reach by height. */
private const val HORIZONTAL_LIMIT = 1e-4f
private const val SINGULAR_LIMIT = 1e-12f
