/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.rendering.debug.RenderDiagnostics

class RenderSystem3D(
    private val renderer: Renderer,
    /**
     * Optional explicit seam for tests or custom bootstrap code. Production construction must
     * provide a renderer that exposes the render-pipeline resolver; silently compiling authored
     * scene commands without lowering them would reintroduce the legacy backend path.
     */
    drawPreparer: GpuDrawPreparer? =
        (renderer as? GpuDrawPreparationSource)?.gpuDrawPreparer,
    private val features: List<RenderFeature3D> = emptyList(),
    private val viewportProvider: () -> RenderViewport? = { null },
) : System {
    private val planner = SceneRenderPlanner3D(
        rendererClipSpace = renderer.clipSpace,
        rendererAspect = { renderer.surfaceAspect },
        rendererViewport = viewportProvider,
        drawPreparer = drawPreparer,
        features = features,
    )
    private var elapsedTimeSeconds = 0f

    /** Number of entities rejected by occlusion during the previous extraction. */
    val lastOccludedCount: Int get() = planner.lastOccludedCount

    /** Number of entities rejected by the camera frustum during the previous extraction. */
    val lastFrustumCulledCount: Int get() = planner.lastFrustumCulledCount

    override fun update(world: World, delta: Float) {
        elapsedTimeSeconds += delta.coerceAtLeast(0f)
        RenderDiagnostics.surfaceAspect = renderer.surfaceAspect
        val camera = primaryCamera(world) ?: run {
            // No scene camera -- e.g. a UI-only sample with an empty World (see ui-showcase's
            // GameModule). There is nothing 3D to draw, but the swapchain must still be
            // presented: `drawUi()` already staged this frame's UI overlay, and `renderer.draw()`
            // is the only call that acquires/submits/presents a frame. Skipping it here left the
            // window showing nothing at all, forever, even though the UI pass had real content.
            renderer.presentWithoutScene()
            return
        }
        val plannedFrame = planner.plan(world, camera, elapsedTimeSeconds)
        RenderDiagnostics.frustumCulled = plannedFrame.frustumCulled
        RenderDiagnostics.occluded = plannedFrame.occluded
        RenderDiagnostics.submittedDrawCalls = plannedFrame.submittedDrawCalls
        RenderDiagnostics.submittedInstances = plannedFrame.submittedInstances
        RenderDiagnostics.unresolvedDrawCalls =
            (plannedFrame.submittedDrawCalls - plannedFrame.passInput.resolvedDraws.size).coerceAtLeast(0)
        renderer.draw(plannedFrame.passInput)
    }
}
