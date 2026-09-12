/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.RenderTarget

/** Test-only scene authoring adapter. Production callers submit generic packets directly. */
internal fun Renderer.renderSceneToTexture(
    target: RenderTarget,
    camera: Lens,
    drawCalls: List<RenderDrawCommand>,
    light: SceneLight = DEFAULT_SCENE_LIGHT,
    viewport: RenderViewport? = null,
) {
    renderToTexture(
        target,
        ScenePassCompiler.compile(
            lens = camera,
            drawCalls = drawCalls,
            light = light,
            clipSpace = clipSpace,
            aspect = viewport?.clampedTo(target.width.toFloat(), target.height.toFloat())?.aspect
                ?: target.width.toFloat() / target.height.toFloat(),
            viewport = viewport,
            drawPreparer = requireNotNull((this@renderSceneToTexture as? GpuDrawPreparationSource)?.gpuDrawPreparer),
        ),
    )
}

internal fun Renderer.renderToTexture(
    target: RenderTarget,
    camera: Lens,
    drawCalls: List<RenderDrawCommand>,
    light: SceneLight = DEFAULT_SCENE_LIGHT,
    viewport: RenderViewport? = null,
) = renderSceneToTexture(target, camera, drawCalls, light, viewport)
