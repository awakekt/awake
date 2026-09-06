/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.editor.core.store.EditorEffect
import com.awakekt.awake.editor.core.store.EditorEntityId
import com.awakekt.awake.editor.core.store.EditorIntent
import com.awakekt.awake.editor.core.store.EditorMode
import com.awakekt.awake.editor.core.store.EditorStore
import com.awakekt.awake.editor.core.store.EditorTool
import com.awakekt.awake.editor.core.store.EditorViewportPoint
import com.awakekt.awake.editor.scene.gizmo.GizmoAxis
import com.awakekt.awake.editor.scene.toEditorEntityId
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.module
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.AppLifecycle
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val FRAME_WIDTH = 1440f
private const val FRAME_HEIGHT = 900f

/**
 * The gizmo through a real frame: pointer coordinates start in frame pixels, the viewport panel
 * is only part of that frame, and the rebasing between the two is exactly what a unit test of the
 * math cannot cover.
 */
class StudioGizmoWiringTest {

    @Test
    fun clickingInsideTheViewportSelectsAndClickingOutsideItDoesNot() = runTest {
        val store = StudioStore()
        val editor = StudioEditorBridge(store)
        val game = app { module(studioModule(store, editorBridge = editor)) }
        game.ready(RecordingCameraRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val input = game.requireService<Input>()
        game.update(1f / 60f, FRAME_WIDTH, FRAME_HEIGHT)

        val viewport = assertNotNull(runtime.uiSemantics.findByTag("studio-panel-viewport"))
        val inspector = assertNotNull(runtime.uiSemantics.findByTag("studio-panel-inspector"))

        // Over the inspector: outside the viewport entirely, so no pick may run. A gizmo handed
        // raw frame coordinates would happily hit-test this against a shifted scene.
        click(
            game,
            input,
            inspector.x + inspector.width / 2f,
            inspector.y + 40f,
        )
        assertEquals(
            null,
            editor.store.state.selection.primary,
            "a click outside must not select",
        )

        // The scene is framed on its camera target, so the middle of the viewport is over geometry.
        click(
            game,
            input,
            viewport.x + viewport.width / 2f,
            viewport.y + viewport.height / 2f,
        )

        val selected = assertNotNull(
            editor.store.state.selection.primary?.value?.toInt(),
            "clicking the middle of the viewport must select the geometry under it",
        )
        var selectedName: String? = null
        runtime.world.queryEach<Name> { entity, name ->
            if (entity.id == selected) selectedName = name.value
        }
        assertTrue(selectedName == "cube" || selectedName == "ground", "selected $selectedName")
    }

    private suspend fun click(appLifecycle: AppLifecycle, input: Input, x: Float, y: Float) {
        input.setPointer(down = true, x = x, y = y)
        appLifecycle.update(AppFrame(1f / 60f, FRAME_WIDTH, FRAME_HEIGHT, input.updateSnapshot()))
        input.setPointer(down = false, x = x, y = y)
        appLifecycle.update(AppFrame(1f / 60f, FRAME_WIDTH, FRAME_HEIGHT, input.updateSnapshot()))
    }

    /**
     * Holding the button after a pick must keep the selection.
     *
     * The press frame selects and every later frame of the same hold reports "no press" -- which
     * the caller used to read as "clicked empty space" and dispatch as a deselect, so a pick
     * visibly reset itself one frame after landing.
     */
    @Test
    fun holdingTheButtonAfterPickingKeepsTheSelection() = runTest {
        val store = StudioStore()
        val editor = StudioEditorBridge(store)
        val game = app { module(studioModule(store, editorBridge = editor)) }
        game.ready(RecordingCameraRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val input = game.requireService<Input>()
        game.update(1f / 60f, FRAME_WIDTH, FRAME_HEIGHT)

        val viewport = assertNotNull(runtime.uiSemantics.findByTag("studio-panel-viewport"))
        val x = viewport.x + viewport.width / 2f
        val y = viewport.y + viewport.height / 2f

        input.setPointer(down = true, x = x, y = y)
        input.updateSnapshot()
        game.update(1f / 60f, FRAME_WIDTH, FRAME_HEIGHT)
        val afterPress = assertNotNull(
            editor.store.state.selection.primary,
            "the press must select something",
        )

        // Still held, pointer unmoved and then nudged -- neither may clear the selection.
        repeat(3) { game.update(1f / 60f, FRAME_WIDTH, FRAME_HEIGHT) }
        input.setPointer(down = true, x = x + 4f, y = y + 2f)
        input.updateSnapshot()
        game.update(1f / 60f, FRAME_WIDTH, FRAME_HEIGHT)

        assertEquals(
            afterPress,
            editor.store.state.selection.primary,
            "holding the button must not reset the selection",
        )
    }

    /** Handles are staged into the 3D pass, not the UI overlay, so they depth-test against the
     * scene -- the renderer is what proves they were emitted at all. */
    @Test
    fun aSelectedEntityStagesOneHandlePerAxis() = runTest {
        val store = StudioStore()
        val editor = StudioEditorBridge(store)
        val renderer = RecordingCameraRenderer()
        val game = app { module(studioModule(store, editorBridge = editor)) }
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        game.update(1f / 60f, FRAME_WIDTH, FRAME_HEIGHT)

        assertTrue(renderer.debugLines.isEmpty(), "nothing selected must draw no handles")

        val cube = assertNotNull(runtime.world.namedEntity("cube"))
        editor.store.dispatch(EditorIntent.SelectEntity(cube.toEditorEntityId()))
        game.update(1f / 60f, FRAME_WIDTH, FRAME_HEIGHT)

        // Each axis and planar handle owns a distinct color, and the selected entity emits its aura.
        assertTrue(renderer.debugLines.any { it.color == GizmoAxis.X.color }, "must draw X handle")
        assertTrue(renderer.debugLines.any { it.color == GizmoAxis.Y.color }, "must draw Y handle")
        assertTrue(renderer.debugLines.any { it.color == GizmoAxis.Z.color }, "must draw Z handle")
        assertTrue(renderer.debugLines.any { it.color == GizmoAxis.PlaneXZ.color }, "must draw PlaneXZ handle")
        assertTrue(renderer.debugLines.isNotEmpty(), "staged ${renderer.debugLines.size} lines")
    }
}
