/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio

import io.github.awakelab.awake.core.logging.LogRingBuffer
import io.github.awakelab.awake.editor.scene.gizmo.SceneOrientationGizmo
import io.github.awakelab.awake.editor.scene.viewport.SceneCameraPreview
import io.github.awakelab.awake.editor.scene.viewport.SceneViewportRect
import io.github.awakelab.awake.studio.ui.StudioFileContents

/**
 * The long-lived objects Studio owns and its panels only observe.
 *
 * One value the module builds and hands down, rather than four parameters threaded through every
 * layer that happens to sit between the module and the panel that needs one. It was a private
 * holder inside `StudioShell.kt`, constructed there from four separate parameters -- so the module
 * passed four, the shell packed them, and anything wanting a single field had to reach through a
 * type it could not name.
 *
 * All four outlive a scene reload, which is why the module creates them before the scene rather
 * than inside `onReady`: the console keeps its history across a reload, and the viewport keeps its
 * rect, preview and orientation gizmo.
 */
internal data class StudioHostResources(
    val viewportRect: SceneViewportRect,
    val cameraPreview: SceneCameraPreview,
    val orientationGizmo: SceneOrientationGizmo,
    val logBuffer: LogRingBuffer,
    val files: StudioFileContents = StudioFileContents(),
) {
    /** Releases the GPU resources the preview panels hold. */
    fun dispose() {
        cameraPreview.dispose()
        orientationGizmo.dispose()
    }
}

/**
 * A resource set with every field defaulted, for a test that cares about one of them.
 *
 * The defaults are the objects the module builds, except the orientation gizmo's material factory,
 * which throws. A test with no renderer cannot create a material, and the factory is only called
 * when the gizmo actually draws -- so a test that never renders it never notices, and one that does
 * gets told why rather than a null-shaped failure somewhere later.
 */
internal fun studioHostResources(
    viewportRect: SceneViewportRect = SceneViewportRect(),
    cameraPreview: SceneCameraPreview = SceneCameraPreview(),
    orientationGizmo: SceneOrientationGizmo = SceneOrientationGizmo { error("This gizmo has no material factory; it is a test default.") },
    logBuffer: LogRingBuffer = LogRingBuffer(),
    files: StudioFileContents = StudioFileContents(),
): StudioHostResources = StudioHostResources(viewportRect, cameraPreview, orientationGizmo, logBuffer, files)

