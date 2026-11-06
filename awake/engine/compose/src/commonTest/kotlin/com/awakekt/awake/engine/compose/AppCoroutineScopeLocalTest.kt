/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** The app's Compose host gives its UI the app's registered [AppCoroutineScope]. */
class AppCoroutineScopeLocalTest {

    @Test
    fun theUiReadsTheScopeTheAppRegistered() = runTest {
        var read: AppCoroutineScope? = null
        val builder = AppSpecBuilder()
        // Installed before the scope is registered: the host looks the scope up when it composes.
        composeAppModule(content = { read = LocalAppCoroutineScope.current }).install(builder)
        val appScope = AppCoroutineScope(builder.serviceLookup())
        builder.service(AppCoroutineScope::class, appScope)
        val app = builder.build().createLifecycle()

        app.ready(NoopRenderer())
        app.update(1f / 60f, 320f, 240f)

        assertSame(appScope, read)
        app.dispose()
    }

    @Test
    fun readingItWithNoScopeRegisteredSaysHowToRegisterOne() {
        val host = ComposeHost()

        val failure = assertFailsWith<IllegalStateException> {
            host.frame(FrameInput(viewportWidth = 10, viewportHeight = 10)) {
                ProvideAppCoroutineScope(null) { LocalAppCoroutineScope.current }
            }
        }

        assertContains(failure.message.orEmpty(), "service(AppCoroutineScope::class")
    }
}
