/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.runtime.RememberObserver
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/** Disposing a scene app disposes its legacy UI host, so what that UI remembered is released. */
class SceneUiHostDisposeTest {
    private var forgotten = 0
    private val input = Input()

    private fun runtime(ui: SceneContent?) = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = {},
            renderableFactory = { error("unused") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = ui,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ),
    ).also { it.initialize(InputOnly(input)) }

    @Test
    fun disposingTheAppForgetsWhatTheSceneUiRemembered() = runTest {
        val runtime = runtime { remember<RememberObserver> { RememberObserver { forgotten++ } } }
        runtime.ready(NoopRenderer())
        runtime.update(AppFrame(1f / 60f, 800f, 600f, input.snapshot(), density = 1f))

        runtime.dispose()

        assertEquals(1, forgotten)
    }

    @Test
    fun anAppWithoutUiDisposesCleanly() = runTest {
        val runtime = runtime(ui = null)
        runtime.ready(NoopRenderer())
        runtime.update(AppFrame(1f / 60f, 800f, 600f, input.snapshot(), density = 1f))

        runtime.dispose()

        assertEquals(0, forgotten)
    }

    private class InputOnly(private val input: Input) : AppServiceLookup {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> service(type: KClass<T>): T? = if (type == Input::class) input as T else null
    }
}
