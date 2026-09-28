/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.scene.rendering.Camera

/**
 * A world's lighting compiled for a view other than the primary camera's: an editor preview, a
 * render-to-texture camera. Shadow cascades are fitted to the view's own [Camera], exactly as the
 * scene renderer fits them to the primary one, so the extra view lights and shadows the same way.
 *
 * @param clipSpace The renderer's clip space.
 */
class SceneViewLighting(clipSpace: ClipSpace) {
    private val compiler = SceneLightingCompiler(clipSpace)

    /** The sun, point lights and shadow fit for [camera] at [viewportAspect]. */
    fun light(world: World, camera: Camera, viewportAspect: Float): SceneLight =
        compiler.sceneLight(world, camera, viewportAspect)

    /** Sky, fog, shadow toggle and debug view, as the scene renderer reads them. */
    fun environment(world: World): EnvironmentUniforms = compiler.environmentUniforms(world)
}
