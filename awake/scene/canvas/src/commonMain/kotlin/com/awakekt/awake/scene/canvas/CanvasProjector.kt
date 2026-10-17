/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ui.canvas.CanvasProjection

/**
 * Where a scene's 3D content lands on the screen, for the elements that follow a node: in the
 * frame's pixels, from the top-left of the window the canvas is drawn in, as a camera reports where
 * it drew a point. [SceneCanvas] moves each point into its own box, so a canvas drawn over part of
 * the window, such as an editor's game view, still lands on what it follows. For a canvas that fills
 * the window the two are the same.
 *
 * The host that draws the scene supplies it from the camera it draws with.
 * `SceneAppLifecycleRuntime` does for the canvas it draws, and its `canvasProjector(world)` gives one
 * to a host that draws `SceneCanvas` itself.
 */
interface CanvasProjector {
    /** Where the world point ([x], [y], [z]) lands in the frame, or null when it is behind the camera. */
    fun project(x: Float, y: Float, z: Float): Vec2?

    /**
     * The frame rectangle the meshes of [entity] and everything under it cover, or null when they
     * have no bounds or any of them reaches behind the camera.
     */
    fun bounds(entity: Entity): Rectangle?

    /**
     * Where the world point ([x], [y], [z]) lands in the frame, with its depth along the camera's
     * view and its distance from the camera, for a follower's culling, ranking, curves and edge
     * pinning; null only when there is no view. A point behind the camera, by its view depth, comes
     * back [CanvasProjection.behind], at a point off the view in the direction it lies.
     *
     * By default it is [project] at no known distance, and a point behind the camera has none.
     */
    fun projectFollow(x: Float, y: Float, z: Float): CanvasProjection? = project(x, y, z)?.let { CanvasProjection(it.x, it.y) }

    /**
     * Whether something stands between the camera and the world point ([x], [y], [z]), for a
     * follower whose rules ask: a host with a physics or depth query answers it. False, the default,
     * hides none.
     */
    fun occluded(x: Float, y: Float, z: Float): Boolean = false
}
