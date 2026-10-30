/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.error.AppError
import com.awakekt.awake.engine.platform.error.AppErrorHandler
import com.awakekt.awake.engine.platform.error.AppErrorSource
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.dsl.cameraEntity
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.authoring.scenes
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneRouteStatus
import com.awakekt.awake.scene.runtime.SceneRouterRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Core catches the failures of a played project and hands them to the app's [AppErrorHandler]: a scene
 * switch that cannot be carried out, a project that cannot be loaded and a system that throws. The app
 * keeps running in each, and with no handler registered each does what it did before.
 */
class AppErrorRoutingTest {
    private val received = mutableListOf<AppError>()
    private val handler = AppErrorHandler { received += it }
    private val sceneFiles = mutableMapOf(MENU to MENU_SCENE, BROKEN to BROKEN_SCENE, UNDRAWABLE to UNDRAWABLE_SCENE)

    @Test
    fun aSceneThatCannotBeLoadedOrPlayedReachesTheHandlerOnceAndTheSceneKeepsPlaying() = runTest {
        for ((target, why) in listOf(MISSING to "missing", BROKEN to "an unregistered component", UNDRAWABLE to "a mesh nothing resolves")) {
            received.clear()
            val project = loadProject(files())
            project.sceneLoads = Dispatchers.Unconfined
            val game = play(project, handler)

            game.switch.request(target)
            game.frames(FRAMES)

            val error = received.single()
            assertEquals(AppErrorSource.SceneSwitch, error.source, "for $why")
            assertEquals(target, error.subject, "for $why")
            assertNotNull(error.retry, "for $why, a scene switch can be asked for again")
            assertTrue(error.cause.message.orEmpty().isNotEmpty(), "for $why, the cause says what went wrong")
            assertEquals("menu", project.scene.name, "for $why, the scene playing goes on")
            game.frames(FRAMES)
            assertEquals(1, received.size, "for $why, nothing more is reported")
            project.close()
        }
    }

    @Test
    fun aRetryAsksForTheSceneAgain() = runTest {
        val project = loadProject(files())
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project, handler)
        game.switch.request(LEVEL)
        game.frames(FRAMES)
        val retry = assertNotNull(received.single().retry)
        assertEquals("menu", project.scene.name)

        sceneFiles[LEVEL] = LEVEL_SCENE
        retry()
        game.frames(FRAMES)

        assertEquals("level", project.scene.name, "the retry read the scene again and played it")
        assertEquals(1, received.size, "and reported nothing more")
        project.close()
    }

    @Test
    fun withoutAHandlerAFailedSwitchIsOnlyLoggedAndTheSceneKeepsPlaying() = runTest {
        val project = loadProject(files())
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project, handler = null)
        val errors = Errors()
        Log.install(errors)
        try {
            game.switch.request(MISSING)
            game.frames(FRAMES)
        } finally {
            Log.remove(errors)
        }

        val logged = errors.records.single()
        assertTrue(MISSING in logged.message && MENU in logged.message, "one error names both scenes: ${logged.message}")
        assertEquals("menu", project.scene.name)
        project.close()
    }

    @Test
    fun aSystemOfThePlayedSceneThatThrowsReachesTheHandlerOnceAndTheAppKeepsRunning() = runTest {
        val boom = IllegalStateException("bad grapple")
        val capability = FaultyCapability(boom)
        val project = loadProject(files(), listOf(capability))
        val game = play(project, handler)

        game.frames(FRAMES * 2)

        val error = received.single()
        assertEquals(AppErrorSource.System, error.source)
        assertSame(boom, error.cause)
        assertNull(error.retry)
        assertEquals("FaultyFrameSystem", error.subject)
        assertEquals(1, capability.faulty.calls, "the system that threw is skipped from then on")
        assertTrue(capability.healthy.calls >= FRAMES * 2, "the project's other systems keep running: ${capability.healthy.calls}")
        project.close()
    }

    @Test
    fun withoutAHandlerASystemFailureIsThrownOutOfTheFrameAsBefore() = runTest {
        val boom = IllegalStateException("bad grapple")
        val project = loadProject(files(), listOf(FaultyCapability(boom)))

        // The scene's first frame pass runs as the app becomes ready, so that is where it surfaces.
        assertSame(boom, assertFailsWith<IllegalStateException> { play(project, handler = null) })
        project.close()
    }

    @Test
    fun aProjectThatFailsToLoadReachesTheHandlerOnceAndItsRetryLoadsTheRouteAgain() = runTest {
        val boom = IllegalStateException("token refused")
        var attempts = 0
        var source: AssetSource? = null
        val game = routed(handler) {
            attempts++
            source?.let { loadProject(it) } ?: throw boom
        }
        val router = game.requireService<SceneRouterRuntime>()

        router.switchTo("harbor-town")
        game.frames(FRAMES)

        val error = received.single()
        assertEquals(AppErrorSource.ProjectLoad, error.source)
        assertSame(boom, error.cause)
        assertEquals("harbor-town", error.subject)
        assertIs<SceneRouteStatus.Failed>(router.activeRouteStatus, "the route shows its loading scene, as before")
        assertEquals(1, attempts)

        source = files()
        assertNotNull(error.retry).invoke()
        game.frames(FRAMES)

        assertEquals(2, attempts, "the retry ran the load again")
        assertEquals("menu", router.sceneRuntime.sceneName, "and the project's scene plays")
        assertEquals(1, received.size)
    }

    @Test
    fun withoutAHandlerAFailedProjectLoadOnlyFailsTheRouteAsBefore() = runTest {
        val game = routed(handler = null) { throw IllegalStateException("token refused") }
        val router = game.requireService<SceneRouterRuntime>()

        router.switchTo("harbor-town")
        game.frames(FRAMES)

        val failed = assertIs<SceneRouteStatus.Failed>(router.activeRouteStatus)
        assertEquals("token refused", failed.cause.message)
        assertTrue(received.isEmpty())
    }

    private suspend fun routed(handler: AppErrorHandler?, project: suspend () -> LoadedProject): AwakeAppLifecycle {
        val game = app {
            handler?.let { service(AppErrorHandler::class, it) }
            scenes {
                route("sign-in") { cameraEntity("camera") }
                projectRoute("harbor-town", loadContext = Dispatchers.Unconfined, project = project)
            }
        }
        game.ready(TestRenderer())
        game.frames(1)
        return game
    }

    private suspend fun play(project: LoadedProject, handler: AppErrorHandler?): Game {
        val app = app {
            handler?.let { service(AppErrorHandler::class, it) }
            scene("play") { runProject(project) }
        }
        app.ready(TestRenderer())
        return Game(app).also { it.frames(1) }
    }

    private class Game(val app: AwakeAppLifecycle) {
        val switch: SceneSwitch = app.requireService()
        val runtime: SceneAppLifecycleRuntime = app.requireService()

        fun frames(count: Int) = repeat(count) { app.update(DELTA, WIDTH, HEIGHT) }
    }

    private fun AwakeAppLifecycle.frames(count: Int) = repeat(count) { update(DELTA, WIDTH, HEIGHT) }

    private fun files(): AssetSource = AssetSource { path ->
        runCatching { (if (path.value == MANIFEST_PATH) MANIFEST else sceneFiles.getValue(path.value)).encodeToByteArray() }
    }

    /** Collects the error records a test logs. */
    private class Errors : LogSink {
        val records = mutableListOf<LogRecord>()

        override fun emit(record: LogRecord) {
            if (record.level == LogLevel.Error) records += record
        }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val DELTA = 1f / 30f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 3

        const val MANIFEST_PATH = "awake.project.json"
        const val MENU = "scenes/menu.scene.json"
        const val LEVEL = "scenes/level.scene.json"
        const val MISSING = "scenes/missing.scene.json"
        const val BROKEN = "scenes/broken.scene.json"
        const val UNDRAWABLE = "scenes/undrawable.scene.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/menu.scene.json"}"""

        const val MENU_SCENE = """
{ "version": 1, "name": "menu", "nodes": [
  { "name": "MenuCamera", "transform": { "position": { "x": 0.0, "y": 2.0, "z": 6.0 } }, "components": [ { "component": "camera" } ] },
  { "name": "Title", "components": [ { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" } ] }
] }
"""

        const val LEVEL_SCENE = """
{ "version": 1, "name": "level", "nodes": [
  { "name": "LevelCamera", "transform": { "position": { "x": 0.0, "y": 4.0, "z": 8.0 } }, "components": [ { "component": "camera" } ] },
  { "name": "Door", "components": [ { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" } ] }
] }
"""

        const val BROKEN_SCENE = """
{ "version": 1, "name": "broken", "nodes": [
  { "name": "Ghost", "components": [ { "component": "grappling_hook" } ] }
] }
"""

        const val UNDRAWABLE_SCENE = """
{ "version": 1, "name": "undrawable", "nodes": [
  { "name": "Ghost", "components": [ { "component": "meshRenderer", "mesh": "no-such-mesh", "material": "lit-shadow" } ] }
] }
"""
    }
}

/** A capability whose scenes always run a frame system that throws and one that does not. */
private class FaultyCapability(private val failure: Exception) : SceneCapability {
    override val id = "com.example.harbor-town.faulty"
    val faulty = Counting(failure)
    val healthy = Counting(null)

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        plan.frame("faulty") { FaultyFrameSystem(faulty) }
        plan.frame("healthy") { HealthyFrameSystem(healthy) }
    }
}

private class Counting(val failure: Exception?) {
    var calls = 0
}

private class FaultyFrameSystem(private val counting: Counting) : System {
    override fun update(world: World, delta: Float) {
        counting.calls++
        counting.failure?.let { throw it }
    }
}

private class HealthyFrameSystem(private val counting: Counting) : System {
    override fun update(world: World, delta: Float) {
        counting.calls++
    }
}
