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
import com.awakekt.awake.scene.canvas.canvasOcclusion
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.CameraViewport
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.ui.canvas.CanvasProjection
import kotlin.math.sqrt

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

    /**
     * In front of the near plane, where [project] puts the point, through the backend's clip space,
     * so Vulkan's flipped Y lands where it is drawn. Behind it, by the depth along the camera's view
     * rather than the clip `w`, the point's side of the camera in view space, right and up, sets
     * which way from the view's centre it lies; the point given is far off the view that way.
     *
     * Written out in floats: it runs for every follower every frame, and allocates only its answer.
     */
    @Suppress("ReturnCount") // No view, a degenerate one, in front and behind each answer differently.
    override fun projectFollow(x: Float, y: Float, z: Float): CanvasProjection? {
        val view = viewport() ?: return null
        val lens = view.lens
        val toX = x - lens.eye.x
        val toY = y - lens.eye.y
        val toZ = z - lens.eye.z
        val distance = sqrt(toX * toX + toY * toY + toZ * toZ)
        var forwardX = lens.center.x - lens.eye.x
        var forwardY = lens.center.y - lens.eye.y
        var forwardZ = lens.center.z - lens.eye.z
        val forwardLength = sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ)
        if (forwardLength < DEGENERATE) return null
        forwardX /= forwardLength
        forwardY /= forwardLength
        forwardZ /= forwardLength
        val depth = toX * forwardX + toY * forwardY + toZ * forwardZ
        if (depth > lens.near) {
            val point = view.projectToSurface(Vec3f(x, y, z), clipSpace) ?: return null
            return CanvasProjection(point.x, point.y, depth, distance, behind = false)
        }
        // Right is forward x up, and the view's own up is right x forward.
        val up = lens.up
        var rightX = forwardY * up.z - forwardZ * up.y
        var rightY = forwardZ * up.x - forwardX * up.z
        var rightZ = forwardX * up.y - forwardY * up.x
        val rightLength = sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
        if (rightLength < DEGENERATE) return null
        rightX /= rightLength
        rightY /= rightLength
        rightZ /= rightLength
        val viewUpX = rightY * forwardZ - rightZ * forwardY
        val viewUpY = rightZ * forwardX - rightX * forwardZ
        val viewUpZ = rightX * forwardY - rightY * forwardX
        val across = toX * rightX + toY * rightY + toZ * rightZ
        // The screen's y runs down, the view's up runs up.
        val down = -(toX * viewUpX + toY * viewUpY + toZ * viewUpZ)
        return offView(view, across, down, depth, distance)
    }

    /** Far off [view] in the screen direction ([across], [down]) from its centre; straight behind is taken as below. */
    private fun offView(view: CameraViewport, across: Float, down: Float, depth: Float, distance: Float): CanvasProjection {
        val layout = view.layout
        val far = layout.width + layout.height
        val sideways = sqrt(across * across + down * down)
        val dx = if (sideways < DEGENERATE) 0f else across / sideways
        val dy = if (sideways < DEGENERATE) 1f else down / sideways
        return CanvasProjection(layout.x + layout.width / 2f + dx * far, layout.y + layout.height / 2f + dy * far, depth, distance, behind = true)
    }

    /**
     * From the eye of the camera the scene is drawn with, as [world]'s [canvasOcclusion] answers;
     * nothing is occluded in a world without one.
     */
    override fun occluded(target: Entity, x: Float, y: Float, z: Float): Boolean {
        val occlusion = world.canvasOcclusion() ?: return false
        val eye = viewport()?.lens?.eye
        return eye != null && occlusion.occluded(target, eye, x, y, z)
    }

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
        const val DEGENERATE = 1e-6f
        const val AXES = 3
        const val BOX_FLOATS = 6
        const val CORNERS = 8
    }
}
