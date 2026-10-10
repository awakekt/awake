/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/** A scene's dispose hooks undo what its ready hooks did, so they run only for a scene that became ready. */
class SceneDisposeHooksTest {
    private var disposed = 0
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
            onDisposeBlock = { disposed++ },
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ),
    ).also { it.initialize(NoServices) }

    @Test
    fun aSceneThatBecameReadyRunsItsDisposeHooks() = runTest {
        runtime.ready(NoopRenderer())

        runtime.dispose()

        assertEquals(1, disposed)
    }

    @Test
    fun aSceneWhoseBackendNeverStartedSkipsThem() {
        // As when the GPU backend fails to start: the app is disposed without ever becoming ready.
        runtime.dispose()

        assertEquals(0, disposed, "nothing was readied, so there is nothing to undo")
    }

    private object NoServices : AppServiceLookup {
        override fun <T : Any> service(type: KClass<T>): T? = null
    }
}
