/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.runtime.schedule.SceneSystemObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The UI build is reported in its compose, measure and draw stages, each in a mark of its own, with
 * `ui:build` holding only what runs between them, so no mark encloses another (#1621).
 */
class UiBuildStagesObserverTest {
    @Test
    fun aUiFrameReportsItsThreeStagesInOrderBetweenTheBuildsOwnMarks() = runTest {
        val (app, runtime) = playing()
        val recording = Recording()
        runtime.systemObserver = recording

        app.update(STEP, WIDTH, HEIGHT)

        val build = SceneSystemObserver.UI_BUILD
        val ui = recording.marks.dropWhile { it != "+$build" }.takeWhile { it != "+${SceneSystemObserver.UI_WAIT}" }
        assertEquals(
            listOf(
                "+$build", "-$build",
                "+${SceneSystemObserver.UI_COMPOSE}", "-${SceneSystemObserver.UI_COMPOSE}",
                "+$build", "-$build",
                "+${SceneSystemObserver.UI_MEASURE}", "-${SceneSystemObserver.UI_MEASURE}",
                "+$build", "-$build",
                "+${SceneSystemObserver.UI_DRAW}", "-${SceneSystemObserver.UI_DRAW}",
                "+$build", "-$build",
            ),
            ui,
        )
        var open: String? = null
        for (mark in recording.marks) {
            if (mark[0] == '+') {
                assertEquals(null, open, "$mark begins inside $open")
                open = mark.drop(1)
            } else {
                assertEquals(open, mark.drop(1))
                open = null
            }
        }
    }

    @Test
    fun withNoObserverTheHostIsToldNothing() = runTest {
        val (app, runtime) = playing()
        runtime.systemObserver = Recording()
        app.update(STEP, WIDTH, HEIGHT)

        runtime.systemObserver = null
        app.update(STEP, WIDTH, HEIGHT)

        assertTrue(runtime.uiHost.frameStages == null, "the stage hook is taken off with the observer")
    }

    private class Recording : SceneSystemObserver {
        val marks = ArrayList<String>()

        override fun before(name: String) {
            marks += "+$name"
        }

        override fun after(name: String) {
            marks += "-$name"
        }
    }

    private suspend fun playing(): Pair<AwakeAppLifecycle, SceneAppLifecycleRuntime> {
        val builder = AppSpecBuilder()
        val runtime = SceneAppSpec(
            sceneName = "staged",
            systems = emptyList(),
            scenePopulationBlock = {},
            renderableFactory = { error("unused") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = {},
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ).installInto(builder)
        return builder.build().createLifecycle().also { it.ready(NoopRenderer()) } to runtime
    }

    private companion object {
        const val STEP = 1f / 60f
        const val WIDTH = 320f
        const val HEIGHT = 240f
    }
}
