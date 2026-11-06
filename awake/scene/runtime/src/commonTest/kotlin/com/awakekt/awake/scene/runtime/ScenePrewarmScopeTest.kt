/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A prewarm lives in the session's scope, and one left open is reported when the scene is torn down. */
class ScenePrewarmScopeTest : PrewarmFixture() {
    private val records = mutableListOf<LogRecord>()

    @BeforeTest
    fun captureLogs() {
        Log.reset()
        Log.install(LogSink { records += it })
    }

    @AfterTest
    fun releaseLogs() = Log.reset()

    /** A loading screen that closes while it waits leaks nothing: the next frame closes the prewarm. */
    @Test
    fun aCallerCancelledWhileWaitingGivesThePrewarmUp() = runTest {
        val prewarm = runtime.prewarm(BOAT)
        library.runPrewarms()
        val waiting = launch { prewarm.await() }
        testScheduler.runCurrent()

        waiting.cancelAndJoin()
        library.runPrewarms()

        assertFalse(prewarm.isReady)
        assertEquals(0, library.meshHolderCount("hull"))
        assertTrue(built.getValue("hull").single().destroyed)
    }

    /** The session's asset work is a child of the app's scope, so closing the app stops a prewarm. */
    @Test
    fun closingTheAppScopeStopsAPrewarm() = runTest {
        val app = AppCoroutineScope(NoServices)
        val appRuntime = SceneAppLifecycleRuntime(spec()).apply { initialize(Services(app)) }
        val prewarm = appRuntime.prewarm(BOAT)

        app.close()

        assertFailsWith<CancellationException> { prewarm.await() }
    }

    /** The session's own scope runs on the spec's dispatcher, and ends with the session. */
    @Test
    fun theSessionScopeRunsOnTheSpecDispatcherAndEndsWithTheSession() = runTest {
        val owner = SceneAppLifecycleRuntime(spec().apply { sessionDispatcher = StandardTestDispatcher(testScheduler) })
        owner.initialize(NoServices)
        var started = false
        val work = owner.session.coroutineScope.launch {
            started = true
            awaitCancellation()
        }
        testScheduler.runCurrent()

        owner.session.dispose(owner)

        assertTrue(started, "it ran on the dispatcher the spec gave")
        assertTrue(work.isCancelled)
    }

    /** A ready prewarm nobody closed held its assets for the whole scene: teardown names it. */
    @Test
    fun aReadyPrewarmNeverClosedIsReportedAtTeardown() {
        runtime.prewarm(SceneAssetReferences(renderables = setOf(SceneRenderableKey("hull", "lit"))))
        library.runPrewarms()

        library.dispose()

        val warnings = records.filter { it.tag == "scene-prewarm" && it.level == LogLevel.Warn }.map { it.message }
        assertEquals(1, warnings.size)
        assertTrue("hull + lit" in warnings.single(), warnings.single())
    }

    /** The positive control: a closed prewarm, or one stopped while still loading, is no leak. */
    @Test
    fun aClosedOrStillLoadingPrewarmIsNotReported() {
        runtime.prewarm(SceneAssetReferences(renderables = setOf(SceneRenderableKey("hull", "lit")))).also { library.runPrewarms() }.close()
        runtime.prewarm(BOAT)

        library.dispose()

        assertEquals(emptyList(), records.filter { it.tag == "scene-prewarm" && it.level == LogLevel.Warn })
    }

    private class Services(private val app: AppCoroutineScope) : AppServiceLookup {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> service(type: KClass<T>): T? = if (type == AppCoroutineScope::class) app as T else null
    }
}
