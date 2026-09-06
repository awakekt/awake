/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.module
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.studio.state.StudioStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Shell chrome contracts that only show up through a real [SceneAppLifecycleRuntime] frame.
 */
class StudioShellChromeTest {

    @Test
    fun fastClickOpensTheScenePickerInTheRealSceneRuntime() = runTest {
        val renderer = RecordingCameraRenderer()
        val store = StudioStore()
        val game = app { module(studioModule(store)) }
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val input = game.requireService<Input>()

        game.update(1f / 60f, 1440f, 900f)
        val trigger = assertNotNull(runtime.uiSemantics.findByTag("studio-top-bar-scene"))
        assertTrue(trigger.width >= 168, "scene trigger shrank to ${trigger.width}px")
        val x = (trigger.x + trigger.width - 2).toFloat()
        val y = (trigger.y + trigger.height / 2).toFloat()
        input.setPointer(down = true, x = x, y = y)
        input.setPointer(down = false, x = x, y = y)
        input.updateSnapshot()
        game.update(1f / 60f, 1440f, 900f)

        assertNotNull(
            runtime.uiSemantics.findByTag("studio-top-bar-scene-menu.item.0"),
            "the scene selector lost the click before Compose could open its menu",
        )
    }

    @Test
    fun hoveringAWorkspaceHandlePublishesTheResizeCursor() = runTest {
        val renderer = RecordingCameraRenderer()
        val store = StudioStore()
        val game = app { module(studioModule(store)) }
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val input = game.requireService<Input>()

        game.update(1f / 60f, 1440f, 900f)
        assertEquals(PointerCursor.Default, runtime.cursor, "no hover -> default cursor")

        val handle = assertNotNull(
            runtime.uiSemantics.findByTag("studio-panel-handle-left"),
            "left workspace handle must exist",
        )
        // Integer centre. The handle is `w-px` -- one pixel wide -- so a float midpoint lands on
        // .5, rounds up on the way into the frame input and misses it entirely.
        input.setPointer(
            down = false,
            x = (handle.x + handle.width / 2).toFloat(),
            y = (handle.y + handle.height / 2).toFloat(),
        )
        input.updateSnapshot()
        game.update(1f / 60f, 1440f, 900f)

        assertEquals(
            PointerCursor.ResizeHorizontal,
            runtime.cursor,
            "hovering the left panel handle must publish the horizontal resize cursor to the host",
        )
    }
}
