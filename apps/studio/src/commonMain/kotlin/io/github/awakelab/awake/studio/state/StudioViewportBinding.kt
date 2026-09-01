/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.state

import io.github.awakelab.awake.editor.scene.viewport.SceneCameraPreview
import io.github.awakelab.awake.editor.scene.viewport.SceneCameraProjection
import io.github.awakelab.awake.editor.scene.gizmo.SceneOrientationGizmo
import io.github.awakelab.awake.editor.scene.viewport.SceneViewportControlActions
import io.github.awakelab.awake.editor.scene.viewport.SceneViewportControlState
import io.github.awakelab.awake.render.renderer.Renderer
import io.github.awakelab.awake.scene.controls.camera.CameraMode
import io.github.awakelab.awake.scene.rendering.debug.WorldDebugSettings

/**
 * What the viewport's control pills read and write, gathered from the four places Studio keeps it.
 *
 * The state is deliberately spread: camera mode is Studio's own, wireframe and shadows are the
 * renderer's, the debug flags are a component in the world, and the preview and orientation gizmo
 * are host-owned objects the panels render. The pills need one view of all four, and this is the
 * only place that knows how to build it -- which is why it is a binding beside
 * [StudioEditorBridge] rather than something living in the shell it feeds.
 *
 * Not a snapshot to hold: read it each frame. Three of the four sources are mutated in place by
 * whoever owns them, so a retained copy would show the values as of whenever it was built.
 */
internal fun viewportControlBinding(
    store: StudioStore,
    renderer: Renderer,
    debugSettings: WorldDebugSettings?,
    cameraPreview: SceneCameraPreview,
    orientationGizmo: SceneOrientationGizmo,
): Pair<SceneViewportControlState, SceneViewportControlActions> {
    val state = store.state.value
    val viewState = SceneViewportControlState(
        mode = state.camera.mode,
        projection = state.camera.projection.toSceneCameraProjection(),
        wireframe = renderer.wireframe,
        shadows = renderer.shadowsEnabled,
        environment = renderer.showEnvironment,
        debugFrustum = debugSettings?.showFrustum ?: false,
        debugBounds = debugSettings?.showBounds ?: false,
        debugOcclusion = debugSettings?.showOcclusion ?: false,
        debugLights = debugSettings?.showLights ?: false,
        debugShadowFrustum = debugSettings?.showShadowFrustum ?: false,
        cameraPreview = cameraPreview.enabled,
        orientationGizmo = orientationGizmo.enabled,
    )
    val viewActions = SceneViewportControlActions(
        onCycleCameraMode = {
            val modes = CameraMode.entries
            val next = modes[(modes.indexOf(state.camera.mode) + 1) % modes.size]
            store.dispatch(StudioContract.Intent.SetCameraMode(next))
        },
        onToggleProjection = {
            val next = if (state.camera.projection == StudioContract.Projection.Perspective) {
                StudioContract.Projection.Orthographic
            } else {
                StudioContract.Projection.Perspective
            }
            store.dispatch(StudioContract.Intent.SetProjection(next))
        },
        onWireframeChange = { renderer.wireframe = it },
        onShadowsChange = { renderer.shadowsEnabled = it },
        onEnvironmentChange = { renderer.showEnvironment = it },
        onDebugFrustumChange = { debugSettings?.showFrustum = it },
        onDebugBoundsChange = { debugSettings?.showBounds = it },
        onDebugOcclusionChange = { debugSettings?.showOcclusion = it },
        onDebugLightsChange = { debugSettings?.showLights = it },
        onDebugShadowFrustumChange = { debugSettings?.showShadowFrustum = it },
        onCameraPreviewChange = { cameraPreview.enabled = it },
        onOrientationGizmoChange = { orientationGizmo.enabled = it },
    )
    return viewState to viewActions
}
