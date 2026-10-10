/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasProjector
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.CameraViewport
import com.awakekt.awake.scene.rendering.mesh.MeshBounds

/**
 * Projects through the camera the scene is drawn with, [viewport], so an element that follows a
 * node lands where the node is drawn. Null from [viewport] before the first drawn frame.
 */
internal class SceneCanvasProjector(
    private val world: World,
    private val clipSpace: ClipSpace,
    private val viewport: () -> CameraViewport?,
) : CanvasProjector {
    override fun project(x: Float, y: Float, z: Float): Vec2? = viewport()?.projectToSurface(Vec3f(x, y, z), clipSpace)

    override fun bounds(entity: Entity): Rectangle? {
        val box = FloatArray(BOX_FLOATS) { if (it < AXES) Float.MAX_VALUE else -Float.MAX_VALUE }
        var found = false
        world.queryEach(MeshBounds::class) { meshEntity, bounds ->
            val transform = world.get<Transform>(meshEntity)
            if (transform != null && world.isAtOrUnder(meshEntity, entity)) {
                val worldBox = bounds.worldBounds(transform)
                box[0] = minOf(box[0], worldBox.min.x)
                box[1] = minOf(box[1], worldBox.min.y)
                box[2] = minOf(box[2], worldBox.min.z)
                box[3] = maxOf(box[3], worldBox.max.x)
                box[4] = maxOf(box[4], worldBox.max.y)
                box[5] = maxOf(box[5], worldBox.max.z)
                found = true
            }
        }
        return if (found) screenBox(box) else null
    }

    /** The screen rectangle around the eight corners of [box], or null when one is behind the camera. */
    private fun screenBox(box: FloatArray): Rectangle? {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (corner in 0 until CORNERS) {
            val point = project(
                if (corner and 1 == 0) box[0] else box[3],
                if (corner and 2 == 0) box[1] else box[4],
                if (corner and 4 == 0) box[2] else box[5],
            ) ?: return null
            left = minOf(left, point.x)
            top = minOf(top, point.y)
            right = maxOf(right, point.x)
            bottom = maxOf(bottom, point.y)
        }
        return Rectangle(left, top, right - left, bottom - top)
    }

    private fun World.isAtOrUnder(entity: Entity, ancestor: Entity): Boolean {
        var node: Entity? = entity
        while (node != null) {
            if (node == ancestor) return true
            node = get<Transform>(node)?.parent
        }
        return false
    }

    private companion object {
        const val AXES = 3
        const val BOX_FLOATS = 6
        const val CORNERS = 8
    }
}
