/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.rendering.debug.DebugVisualizationSystem

/** Shared between [RenderSystem3D] and [DebugVisualizationSystem] -- both need "the" camera a
 * frame draws through. */
internal fun primaryCamera(world: World): Camera? {
    val family = world.family<Camera>()
    val cameras = family.components()
    var index = 0
    val count = family.size
    while (index < count) {
        val camera = cameras[index]
        if (camera.isPrimary) {
            return camera
        }
        index += 1
    }
    return null
}

/** Deliberately wider than real viewports for CPU-side culling only. Projection and cascade fitting
 * use the live viewport aspect; using this margin there would lower shadow-map resolution and leave
 * the shadow debugger showing volumes the renderer does not sample. */
internal const val CONSERVATIVE_ASPECT = 3f
