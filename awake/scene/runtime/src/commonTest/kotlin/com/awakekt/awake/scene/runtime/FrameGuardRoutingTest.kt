/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.error.AppError
import com.awakekt.awake.engine.platform.error.AppErrorHandler
import com.awakekt.awake.engine.platform.error.AppErrorSource
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The frame work an app supplies, its update block and its UI build, and the switch of a routed scene
 * reach the app's [AppErrorHandler] when they throw, once, and the app keeps running. With no handler
 * registered each is thrown as before.
 */
class FrameGuardRoutingTest {
    private val received = mutableListOf<AppError>()
    private val handler = AppErrorHandler { received += it }

    @Test
    fun anUpdateBlockThatThrowsIsReportedOnceSkippedAndTheScenesSystemsKeepRunning() = runTest {
        val boom = IllegalStateException("bad game logic")
        var calls = 0
        val system = Counter()
        val app = playing(handler, system = system, update = {
            calls++
            throw boom
        })

        repeat(FRAMES) { app.frame() }

        val error = received.single()
        assertEquals(AppErrorSource.System, error.source)
        assertSame(boom, error.cause)
        assertEquals("updateBlock", error.subject)
        assertNull(error.retry)
        assertEquals(1, calls, "the block that failed is skipped from then on")
        assertTrue(system.calls >= FRAMES, "the scene's systems run every step: ${system.calls}")
    }

    @Test
    fun withoutAHandlerAnUpdateBlockFailureIsThrownEveryStepAsBefore() = runTest {
        val boom = IllegalStateException("bad game logic")
        var calls = 0
        val app = playing(handler = null, update = {
            calls++
            throw boom
        })

        assertSame(boom, assertFailsWith<IllegalStateException> { app.frame() })
        assertSame(boom, assertFailsWith<IllegalStateException> { app.frame() })
        assertEquals(2, calls, "it is not skipped when nobody was told")
    }

    @Test
    fun aUiBuildThatThrowsIsReportedOnceDrawsNothingThatFrameAndBuildsAgainTheNext() = runTest {
        val boom = IllegalStateException("bad composable")
        var builds = 0
        var failing = true
        val system = Counter()
        val app = playing(handler, system = system, ui = {
            builds++
            if (failing) throw boom
        })

        repeat(FRAMES) { app.frame() }

        val error = received.single()
        assertEquals(AppErrorSource.System, error.source)
        assertSame(boom, error.cause)
        assertEquals("ui", error.subject)
        assertEquals(FRAMES, builds, "the UI is built again every frame")
        assertTrue(system.calls >= FRAMES, "the simulation ran every frame: ${system.calls}")

        failing = false
        app.frame()
        failing = true
        app.frame()

        assertEquals(2, received.size, "a build that succeeded in between re-arms the report")
    }

    @Test
    fun withoutAHandlerAUiBuildFailureIsThrownOutOfTheFrameAsBefore() = runTest {
        val boom = IllegalStateException("bad composable")
        val app = playing(handler = null, ui = { throw boom })

        // The scene's first UI build runs with its first frame; it is not part of ready.
        assertSame(boom, assertFailsWith<IllegalStateException> { app.frame() })
        assertSame(boom, assertFailsWith<IllegalStateException> { app.frame() })
    }

    @Test
    fun aRouteWhoseSceneFailsToGoInIsReportedOnceAndTheRouteBeforeItPlaysOn() = runTest {
        val boom = IllegalStateException("bad population")
        val home = Counter()
        val game = Counter()
        val app = routed(handler, home = spec(system = home), game = spec(system = game, population = { throw boom }))
        val router = app.requireRouter()

        router.switchTo("game")
        repeat(FRAMES) { app.frame() }

        val error = received.single()
        assertEquals(AppErrorSource.SceneSwitch, error.source)
        assertSame(boom, error.cause)
        assertEquals("game", error.subject)
        assertNotNull(error.retry)
        assertEquals("home", router.activeSceneId, "the app has the scene that was active")
        assertTrue(home.calls >= FRAMES, "and it runs: ${home.calls}")
    }

    @Test
    fun theRetryOfARouteSwitchEntersTheRouteAgain() = runTest {
        var failing = true
        val app = routed(handler, game = spec(population = { if (failing) error("not yet") }))
        val router = app.requireRouter()
        router.switchTo("game")
        app.frame()
        val retry = assertNotNull(received.single().retry)

        failing = false
        retry()
        repeat(FRAMES) { app.frame() }

        assertEquals("game", router.activeSceneId)
        assertEquals(1, received.size)
    }

    @Test
    fun whenTheRouteBeforeItFailsAsWellEachFailureIsReportedAndTheRouterIdlesUntilARetry() = runTest {
        var homeStarts = 0
        var homeFailing = false
        val app = routed(
            handler,
            home = spec(population = {
                homeStarts++
                if (homeFailing) error("home gone")
            }),
            game = spec(population = { error("game gone") }),
        )
        val router = app.requireRouter()
        homeFailing = true

        router.switchTo("game")
        repeat(FRAMES) { app.frame() }

        assertEquals(listOf("game", "home"), received.map { it.subject })
        assertTrue(received.all { it.source == AppErrorSource.SceneSwitch })
        assertEquals(2, homeStarts, "home started once, then was put back once")

        homeFailing = false
        assertNotNull(received.last().retry).invoke()
        repeat(FRAMES) { app.frame() }

        assertEquals("home", router.activeSceneId)
        assertEquals(3, homeStarts, "the retry put it in again")
        assertEquals(2, received.size)
    }

    @Test
    fun withoutAHandlerARouteThatFailsToGoInIsThrownAsBefore() = runTest {
        val boom = IllegalStateException("bad population")
        val app = routed(handler = null, game = spec(population = { throw boom }))

        app.requireRouter().switchTo("game")

        assertSame(boom, assertFailsWith<IllegalStateException> { app.frame() })
    }

    private fun AwakeAppLifecycle.requireRouter(): SceneRouterRuntime = assertNotNull(service(SceneRouterRuntime::class))

    private fun spec(
        system: System? = null,
        population: () -> Unit = {},
        update: () -> Unit = {},
        ui: (() -> Unit)? = null,
    ) = SceneAppSpec(
        sceneName = "frames",
        systems = listOfNotNull(
            system?.let { counter -> SceneSystemRegistration(SceneSystemHandle<System>("counter"), SceneSystemPhase.Frame) { counter } },
        ),
        scenePopulationBlock = { population() },
        renderableFactory = { error("unused") },
        assetLibraryFactory = null,
        updateBlock = { _, _ -> update() },
        ui = ui?.let { block -> { block() } },
        onReadyBlock = {},
        onDisposeBlock = {},
        serviceRegistrations = emptyList(),
        infrastructureSystemsFactory = { emptyList() },
    )

    private suspend fun playing(
        handler: AppErrorHandler?,
        system: System? = null,
        update: () -> Unit = {},
        ui: (() -> Unit)? = null,
    ): AwakeAppLifecycle {
        val builder = AppSpecBuilder()
        handler?.let { builder.service(AppErrorHandler::class, it) }
        spec(system = system, update = update, ui = ui).installInto(builder)
        return builder.build().createLifecycle().also { it.ready(NoopRenderer()) }
    }

    private suspend fun routed(
        handler: AppErrorHandler?,
        home: SceneAppSpec = spec(),
        game: SceneAppSpec = spec(),
    ): AwakeAppLifecycle {
        val builder = AppSpecBuilder()
        handler?.let { builder.service(AppErrorHandler::class, it) }
        SceneRouterSpec(listOf(SceneRoute("home", "Home", home), SceneRoute("game", "Game", game)), "home").install(builder)
        return builder.build().createLifecycle().also { it.ready(NoopRenderer()) }
    }

    private fun AwakeAppLifecycle.frame() = update(STEP, WIDTH, HEIGHT)

    private class Counter : System {
        var calls = 0

        override fun update(world: World, delta: Float) {
            calls++
        }
    }

    private companion object {
        const val STEP = 1f / 30f
        const val WIDTH = 320f
        const val HEIGHT = 240f
        const val FRAMES = 5
    }
}
