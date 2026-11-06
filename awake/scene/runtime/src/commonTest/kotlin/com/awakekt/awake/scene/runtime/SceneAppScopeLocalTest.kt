/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.engine.compose.LocalAppCoroutineScope
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertSame

/** A scene's legacy `content { }` UI reads the app's registered [AppCoroutineScope]. */
class SceneAppScopeLocalTest {
    private val input = Input()
    private val services = Services()
    private val appScope = AppCoroutineScope(services)
    private var read: AppCoroutineScope? = null

    private val runtime = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = {},
            renderableFactory = { error("unused") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = { read = LocalAppCoroutineScope.current },
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ),
    ).also { it.initialize(services) }

    @Test
    fun theSceneUiReadsTheAppScope() = runTest {
        runtime.ready(NoopRenderer())
        runtime.update(AppFrame(1f / 60f, 800f, 600f, input.snapshot(), density = 1f))

        assertSame(appScope, read)
        runtime.dispose()
        appScope.close()
    }

    private inner class Services : AppServiceLookup {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> service(type: KClass<T>): T? = when (type) {
            Input::class -> input as T
            AppCoroutineScope::class -> appScope as T
            else -> null
        }
    }
}
