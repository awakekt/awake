/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A paused app reads 0 fps, not its last frames' rate (#1709). On Android the render loop stops
 * when the surface goes away in the background, so no frame records a time; a report read from
 * another thread kept the last foreground average, 66, for minutes, as if the app still ran.
 */
class ScenePausedFpsTest {
    private val input = Input()

    private val runtime = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = {},
            renderableFactory = { error("unused") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ),
    ).also { it.initialize(InputOnly(input)) }

    private fun frames(count: Int, seconds: Float) = repeat(count) {
        runtime.update(AppFrame(seconds, 800f, 600f, input.snapshot(), density = 1f))
    }

    @Test
    fun aPausedAppReadsNoFramesRatherThanItsLastRate() = runTest {
        runtime.ready(NoopRenderer())
        frames(count = 30, seconds = 1f / 66f)
        assertEquals(66f, runtime.fps, 0.5f)
        assertFalse(runtime.isPaused)

        runtime.pause()

        assertTrue(runtime.isPaused)
        assertEquals(0f, runtime.fps)
        assertEquals(0f, runtime.averageFrameTimeMs)
        assertEquals(0f, runtime.frameStats().frameTimeMs)
    }

    @Test
    fun resumedFramesAreCountedAfreshWithoutTheForegroundOnes() = runTest {
        runtime.ready(NoopRenderer())
        frames(count = 30, seconds = 1f / 66f)
        runtime.pause()

        runtime.resume()
        frames(count = 10, seconds = 1f / 30f)

        assertFalse(runtime.isPaused)
        assertEquals(30f, runtime.fps, 0.5f)
    }

    private class InputOnly(private val input: Input) : AppServiceLookup {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> service(type: KClass<T>): T? = if (type == Input::class) input as T else null
    }
}
