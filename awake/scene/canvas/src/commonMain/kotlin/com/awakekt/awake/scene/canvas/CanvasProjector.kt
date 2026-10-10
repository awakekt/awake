/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ecs.Entity

/**
 * Where a scene's 3D content lands on the screen, in the canvas's pixels, for the elements that
 * follow a node. The host that draws the scene supplies it from the camera it draws with;
 * `SceneAppLifecycleRuntime` does.
 */
interface CanvasProjector {
    /** Where the world point ([x], [y], [z]) lands, or null when it is behind the camera. */
    fun project(x: Float, y: Float, z: Float): Vec2?

    /**
     * The screen rectangle the meshes of [entity] and everything under it cover, or null when they
     * have no bounds or any of them reaches behind the camera.
     */
    fun bounds(entity: Entity): Rectangle?
}
