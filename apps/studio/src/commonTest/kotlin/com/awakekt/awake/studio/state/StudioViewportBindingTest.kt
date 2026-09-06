/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.state

import com.awakekt.awake.editor.scene.gizmo.SceneOrientationGizmo
import com.awakekt.awake.editor.scene.viewport.SceneCameraPreview
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.rendering.debug.WorldDebugSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudioViewportBindingTest {

    @Test
    fun gridToggleMutatesDebugSettings() {
        val store = StudioStore()
        val renderer = NoopRenderer()
        val debugSettings = WorldDebugSettings(showGrid = false, showAxisLines = false)
        val preview = SceneCameraPreview()
        val gizmo = SceneOrientationGizmo { error("test") }

        val (state, actions) = viewportControlBinding(
            store = store,
            renderer = renderer,
            debugSettings = debugSettings,
            cameraPreview = preview,
            orientationGizmo = gizmo,
        )

        assertFalse(state.debugGrid)
        assertFalse(state.debugAxisLines)

        actions.onDebugGridChange(true)
        assertTrue(debugSettings.showGrid)
        assertTrue(debugSettings.showAxisLines)

        val (nextState, _) = viewportControlBinding(
            store = store,
            renderer = renderer,
            debugSettings = debugSettings,
            cameraPreview = preview,
            orientationGizmo = gizmo,
        )
        assertTrue(nextState.debugGrid)
        assertTrue(nextState.debugAxisLines)
    }
}
