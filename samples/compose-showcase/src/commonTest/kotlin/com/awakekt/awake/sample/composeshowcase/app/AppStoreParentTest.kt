/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.app

import com.awakekt.awake.compose.state.rememberStore
import com.awakekt.awake.core.state.Store
import com.awakekt.awake.engine.compose.composeAppModule
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import com.awakekt.awake.engine.platform.error.AppError
import com.awakekt.awake.engine.platform.error.AppErrorHandler
import com.awakekt.awake.engine.platform.error.AppErrorSource
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The wiring the showcase uses, end to end in an app: a store remembered under the app's
 * [AppCoroutineScope] reports its failures to the app's [AppErrorHandler], and disposing the app
 * cancels its work.
 */
class AppStoreParentTest {
    private val errors = mutableListOf<AppError>()
    private var store: Store<Int>? = null

    private val app = AppSpecBuilder().run {
        service(AppErrorHandler::class, AppErrorHandler { errors += it })
        // Unconfined, so a launched store coroutine runs and fails before `launch` returns.
        val appScope = AppCoroutineScope(serviceLookup(), Dispatchers.Unconfined)
        service(AppCoroutineScope::class, appScope)
        composeAppModule(content = { store = rememberStore(appScope) { 0 } }).install(this)
        build().createLifecycle()
    }

    private suspend fun firstFrame(): Store<Int> {
        app.ready(NoopRenderer())
        app.update(1f / 60f, 320f, 240f)
        return assertNotNull(store, "the UI remembered its store")
    }

    @Test
    fun aStoreFailureReachesTheAppErrorHandler() = runTest {
        val boom = IllegalStateException("store work failed")

        firstFrame().coroutineScope.launch { throw boom }

        val error = errors.single()
        assertEquals(AppErrorSource.Coroutine, error.source)
        assertSame(boom, error.cause)
    }

    @Test
    fun disposingTheAppCancelsTheStoresWork() = runTest {
        val work = firstFrame().coroutineScope.launch { awaitCancellation() }

        app.dispose()

        assertTrue(work.isCancelled)
        assertTrue(errors.isEmpty(), "a cancellation is not a failure")
    }
}
