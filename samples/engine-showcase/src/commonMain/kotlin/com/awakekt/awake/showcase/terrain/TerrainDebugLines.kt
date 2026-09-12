/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.terrain

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.normalAt
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Draws the two independent heightfield consumers on top of each other:
 *
 * - cyan: the heightmap mesh's grid;
 * - magenta crosses: the samples supplied to Jolt;
 * - yellow: the shared heightmap normals; and
 * - red/green/blue: the terrain's local X/Y/Z axes.
 *
 * A cyan grid and magenta crosses that do not coincide identify an origin, row-order, scale, or
 * transform mismatch without needing a backend-specific mesh inspector. The normal arrows make a
 * winding or Y-up error immediately visible. This is showcase content: it composes ordinary
 * [LineSegment]s and adds no terrain vocabulary to the renderer.
 */
internal fun heightfieldDiagnosticLines(world: World): List<LineSegment> {
    var transform: Transform? = null
    world.queryEach(Name::class, Transform::class) { _, name, candidate ->
        if (name.value == HEIGHTFIELD_NODE_NAME) transform = candidate
    }
    return transform?.let { heightfieldDiagnosticLines(TerrainExampleAsset.heightmap, TerrainExampleAsset.collisionShape, it.worldMatrix) }
        ?: emptyList()
}

/** Kept separate from the world lookup so the diagnostic's coordinate contract is directly testable. */
internal fun heightfieldDiagnosticLines(
    heightmap: Heightmap,
    collision: HeightFieldShape,
    worldMatrix: Mat4,
): List<LineSegment> {
    require(heightmap.width == collision.sampleCount && heightmap.depth == collision.sampleCount) {
        "Heightfield diagnostic requires matching visual and collision sample counts."
    }
    val lines = ArrayList<LineSegment>()
    for (z in 0 until heightmap.depth) {
        for (x in 0 until heightmap.width) {
            val renderPoint = heightmapPoint(heightmap, x, z)
            if (x + 1 < heightmap.width) {
                lines += line(renderPoint, heightmapPoint(heightmap, x + 1, z), worldMatrix, RENDER_GRID_COLOR)
            }
            if (z + 1 < heightmap.depth) {
                lines += line(renderPoint, heightmapPoint(heightmap, x, z + 1), worldMatrix, RENDER_GRID_COLOR)
            }
            if (x % NORMAL_SAMPLE_STRIDE == 0 && z % NORMAL_SAMPLE_STRIDE == 0) {
                lines += line(
                    renderPoint,
                    renderPoint + heightmap.normalAt(x, z) * NORMAL_LENGTH,
                    worldMatrix,
                    NORMAL_COLOR,
                )
            }
            addCollisionCross(lines, collisionPoint(collision, x, z), worldMatrix)
        }
    }
    addAxes(lines, worldMatrix)
    return lines
}

private fun heightmapPoint(heightmap: Heightmap, x: Int, z: Int): Vec3f = Vec3f(
    heightmap.minX + x * heightmap.scale.x,
    heightmap.heightAt(x, z) * heightmap.scale.y,
    heightmap.minZ + z * heightmap.scale.z,
)

private fun collisionPoint(collision: HeightFieldShape, x: Int, z: Int): Vec3f {
    val halfX = (collision.sampleCount - 1) * collision.scale.x * HALF
    val halfZ = (collision.sampleCount - 1) * collision.scale.z * HALF
    val offsetX = if (collision.origin == GridOrigin.Centered) -halfX else 0f
    val offsetZ = if (collision.origin == GridOrigin.Centered) -halfZ else 0f
    return Vec3f(
        offsetX + x * collision.scale.x,
        collision.heightAt(x, z) * collision.scale.y,
        offsetZ + z * collision.scale.z,
    )
}

private fun addCollisionCross(lines: MutableList<LineSegment>, point: Vec3f, matrix: Mat4) {
    lines += line(point - Vec3f(COLLISION_MARKER_HALF_SIZE, 0f, 0f), point + Vec3f(COLLISION_MARKER_HALF_SIZE, 0f, 0f), matrix, COLLISION_SAMPLE_COLOR)
    lines += line(point - Vec3f(0f, COLLISION_MARKER_HALF_SIZE, 0f), point + Vec3f(0f, COLLISION_MARKER_HALF_SIZE, 0f), matrix, COLLISION_SAMPLE_COLOR)
    lines += line(point - Vec3f(0f, 0f, COLLISION_MARKER_HALF_SIZE), point + Vec3f(0f, 0f, COLLISION_MARKER_HALF_SIZE), matrix, COLLISION_SAMPLE_COLOR)
}

private fun addAxes(lines: MutableList<LineSegment>, matrix: Mat4) {
    lines += line(Vec3f(), Vec3f(AXIS_LENGTH, 0f, 0f), matrix, AXIS_X_COLOR)
    lines += line(Vec3f(), Vec3f(0f, AXIS_LENGTH, 0f), matrix, AXIS_Y_COLOR)
    lines += line(Vec3f(), Vec3f(0f, 0f, AXIS_LENGTH), matrix, AXIS_Z_COLOR)
}

private fun line(start: Vec3f, end: Vec3f, matrix: Mat4, color: Color): LineSegment =
    LineSegment(start = worldPoint(start, matrix), end = worldPoint(end, matrix), color = color)

private fun worldPoint(point: Vec3f, matrix: Mat4): Vec3f {
    val transformed = Vec4(point.x, point.y, point.z, 1f) * matrix
    return Vec3f(transformed.x, transformed.y, transformed.z)
}

private const val HEIGHTFIELD_NODE_NAME = "heightfield-terrain"
private const val NORMAL_SAMPLE_STRIDE = 2
private const val NORMAL_LENGTH = 0.7f
private const val COLLISION_MARKER_HALF_SIZE = 0.08f
private const val AXIS_LENGTH = 1.25f
private const val HALF = 0.5f

private val RENDER_GRID_COLOR = Color.fromHex(0x22D3EE)
private val COLLISION_SAMPLE_COLOR = Color.fromHex(0xF472B6)
private val NORMAL_COLOR = Color.fromHex(0xFACC15)
private val AXIS_X_COLOR = Color.fromHex(0xEF4444)
private val AXIS_Y_COLOR = Color.fromHex(0x22C55E)
private val AXIS_Z_COLOR = Color.fromHex(0x3B82F6)
