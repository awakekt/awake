/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.rendering.camera.Camera
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
    /**
     * Selects the world whose camera and renderable components are presented for this frame.
     * Simulation systems still receive the scheduled world in [update]; this seam is for hosts
     * that keep an isolated preview/play world and choose which one is visible in the viewport.
     */
    private val renderWorldProvider: (World) -> World = { it },
    /**
     * Whether the 3D scene renders continuously in real time.
     * When false, scene re-planning is bypassed unless the camera, viewport, or scene is dirty.
     */
    private val isRealtimeProvider: () -> Boolean = { true },
    /**
     * Supplies an external dirtiness signal (e.g. active gizmo drag, editor history changes, component mutations).
     */
    private val isDirtyProvider: () -> Boolean = { false },
) : System {
    private val planner = SceneRenderPlanner3D(
        rendererClipSpace = renderer.clipSpace,
        rendererAspect = { renderer.surfaceAspect },
        rendererViewport = viewportProvider,
        drawPreparer = drawPreparer,
        features = features,
    )
    private var elapsedTimeSeconds = 0f
    private var lastPlannedFrame: SceneRenderPlanner3D.PlannedFrame? = null

    // Tracked camera and viewport primitive properties for allocation-free dirtiness detection
    private var lastCameraEyeX: Float = Float.NaN
    private var lastCameraEyeY: Float = Float.NaN
    private var lastCameraEyeZ: Float = Float.NaN
    private var lastCameraCenterX: Float = Float.NaN
    private var lastCameraCenterY: Float = Float.NaN
    private var lastCameraCenterZ: Float = Float.NaN
    private var lastCameraUpX: Float = Float.NaN
    private var lastCameraUpY: Float = Float.NaN
    private var lastCameraUpZ: Float = Float.NaN
    private var lastCameraFov: Float = Float.NaN
    private var lastCameraNear: Float = Float.NaN
    private var lastCameraFar: Float = Float.NaN
    private var lastCameraProjection: Lens.Projection? = null
    private var lastCameraOrthoHeight: Float = Float.NaN
    private var lastViewportX: Float? = null
    private var lastViewportY: Float? = null
    private var lastViewportWidth: Float? = null
    private var lastViewportHeight: Float? = null
    private var lastSurfaceAspect: Float = Float.NaN

    /** Number of entities rejected by occlusion during the previous extraction. */
    val lastOccludedCount: Int get() = planner.lastOccludedCount

    /** Number of entities rejected by the camera frustum during the previous extraction. */
    val lastFrustumCulledCount: Int get() = planner.lastFrustumCulledCount

    /** Whether the previous frame triggered a full scene plan or reused a clean frame. */
    var lastFramePlanned: Boolean = false
        private set

    override fun update(world: World, delta: Float) {
        elapsedTimeSeconds += delta.coerceAtLeast(0f)
        RenderDiagnostics.surfaceAspect = renderer.surfaceAspect
        val renderWorld = renderWorldProvider(world)
        val camera = primaryCamera(renderWorld) ?: run {
            // No scene camera -- e.g. a UI-only sample with an empty World (see ui-showcase's
            // GameModule). There is nothing 3D to draw, but the swapchain must still be
            // presented: `drawUi()` already staged this frame's UI overlay, and `renderer.draw()`
            // is the only call that acquires/submits/presents a frame. Skipping it here left the
            // window showing nothing at all, forever, even though the UI pass had real content.
            renderer.presentWithoutScene()
            lastPlannedFrame = null
            lastFramePlanned = false
            return
        }

        val currentViewport = viewportProvider()
        val currentAspect = renderer.surfaceAspect
        val viewDirty = isViewDirty(camera, currentViewport, currentAspect)
        val isRealtime = isRealtimeProvider()
        val isDirty = isRealtime || isDirtyProvider() || viewDirty || lastPlannedFrame == null

        val plannedFrame = if (isDirty) {
            recordCameraState(camera, currentViewport, currentAspect)
            lastFramePlanned = true
            planner.plan(renderWorld, camera, elapsedTimeSeconds).also {
                lastPlannedFrame = it
            }
        } else {
            lastFramePlanned = false
            checkNotNull(lastPlannedFrame)
        }

        RenderDiagnostics.frustumCulled = plannedFrame.frustumCulled
        RenderDiagnostics.occluded = plannedFrame.occluded
        RenderDiagnostics.submittedDrawCalls = plannedFrame.submittedDrawCalls
        RenderDiagnostics.submittedInstances = plannedFrame.submittedInstances
        RenderDiagnostics.unresolvedDrawCalls =
            (plannedFrame.submittedDrawCalls - plannedFrame.passInput.resolvedDraws.size).coerceAtLeast(0)
        renderer.draw(plannedFrame.passInput)
    }

    private fun isViewDirty(camera: Camera, viewport: RenderViewport?, surfaceAspect: Float): Boolean =
        isCameraDirty(camera) || isViewportDirty(viewport, surfaceAspect)

    private fun isCameraDirty(camera: Camera): Boolean {
        val lens = camera.lens
        val eye = lens.eye
        val center = lens.center
        val up = lens.up
        return eye.x != lastCameraEyeX || eye.y != lastCameraEyeY || eye.z != lastCameraEyeZ ||
            center.x != lastCameraCenterX || center.y != lastCameraCenterY || center.z != lastCameraCenterZ ||
            up.x != lastCameraUpX || up.y != lastCameraUpY || up.z != lastCameraUpZ ||
            lens.fovYRadians != lastCameraFov || lens.near != lastCameraNear || lens.far != lastCameraFar ||
            lens.projection != lastCameraProjection || lens.orthoHalfHeight != lastCameraOrthoHeight
    }

    private fun isViewportDirty(viewport: RenderViewport?, surfaceAspect: Float): Boolean =
        viewport?.x != lastViewportX || viewport?.y != lastViewportY ||
            viewport?.width != lastViewportWidth || viewport?.height != lastViewportHeight ||
            surfaceAspect != lastSurfaceAspect

    private fun recordCameraState(camera: Camera, viewport: RenderViewport?, surfaceAspect: Float) {
        val lens = camera.lens
        lastCameraEyeX = lens.eye.x
        lastCameraEyeY = lens.eye.y
        lastCameraEyeZ = lens.eye.z
        lastCameraCenterX = lens.center.x
        lastCameraCenterY = lens.center.y
        lastCameraCenterZ = lens.center.z
        lastCameraUpX = lens.up.x
        lastCameraUpY = lens.up.y
        lastCameraUpZ = lens.up.z
        lastCameraFov = lens.fovYRadians
        lastCameraNear = lens.near
        lastCameraFar = lens.far
        lastCameraProjection = lens.projection
        lastCameraOrthoHeight = lens.orthoHalfHeight
        lastViewportX = viewport?.x
        lastViewportY = viewport?.y
        lastViewportWidth = viewport?.width
        lastViewportHeight = viewport?.height
        lastSurfaceAspect = surfaceAspect
    }
}
