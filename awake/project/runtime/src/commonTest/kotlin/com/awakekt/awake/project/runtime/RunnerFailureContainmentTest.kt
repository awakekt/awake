/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
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
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.document.SceneDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The runner's own steps, which run inside the schedule's `project-fixed` and `project-frame` systems, are
 * contained: a switch step that fails after the last scene is gone is reported to the app's
 * [AppErrorHandler] once, the new scene plays, and the runner switches again afterwards. With no handler
 * the failure is thrown as before.
 */
class RunnerFailureContainmentTest {
    private val received = mutableListOf<AppError>()
    private val handler = AppErrorHandler { received += it }

    @Test
    fun aTeardownThatFailsIsReportedOnceTheNewSceneStaysAndTheRunnerSwitchesAgain() = runTest {
        val boom = IllegalStateException("could not close the menu's sounds")
        val capability = ClosingCapability(boom)
        val project = loadProject(files(), listOf(capability))
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project, handler)
        val switch: SceneSwitch = game.requireService()

        switch.request(LEVEL)
        game.frames(FRAMES)

        val error = received.single()
        assertEquals(AppErrorSource.SceneSwitch, error.source)
        assertSame(boom, error.cause)
        assertEquals(LEVEL, error.subject)
        assertNotNull(error.retry, "the scene can be asked for again")
        assertEquals("level", project.scene.name, "the scene put in plays; the app is not left without one")
        val beforeSwitchingBack = capability.healthy.calls

        switch.request(MENU)
        game.frames(FRAMES)

        assertEquals("menu", project.scene.name, "the runner was able to switch again")
        assertEquals(1, received.size, "and nothing more was reported")
        assertTrue(capability.healthy.calls > beforeSwitchingBack, "the scene's systems run on: ${capability.healthy.calls}")
        project.close()
    }

    @Test
    fun theRetryOfATeardownFailureAsksForTheSceneAgain() = runTest {
        val capability = ClosingCapability(IllegalStateException("could not close"))
        val project = loadProject(files(), listOf(capability))
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project, handler)
        game.requireService<SceneSwitch>().request(LEVEL)
        game.frames(FRAMES)
        val retry = assertNotNull(received.single().retry)

        retry()
        game.frames(FRAMES)

        assertEquals("level", project.scene.name)
        assertEquals(1, received.size, "the scene is put in again, and its own teardown is clean")
        project.close()
    }

    @Test
    fun withoutAHandlerATeardownFailureIsThrownOutOfTheFrameAsBeforeAndTheNewSceneStillPlays() = runTest {
        val boom = IllegalStateException("could not close the menu's sounds")
        val project = loadProject(files(), listOf(ClosingCapability(boom)))
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project, handler = null)
        game.requireService<SceneSwitch>().request(LEVEL)

        assertSame(boom, assertFailsWith<IllegalStateException> { game.frames(FRAMES) })

        assertEquals("level", project.scene.name)
        game.frames(FRAMES)
        project.close()
    }

    private suspend fun play(project: LoadedProject, handler: AppErrorHandler?): AwakeAppLifecycle {
        val game = app {
            handler?.let { service(AppErrorHandler::class, it) }
            scene("play") { runProject(project) }
        }
        game.ready(TestRenderer())
        game.frames(1)
        return game
    }

    private fun AwakeAppLifecycle.frames(count: Int) = repeat(count) { update(DELTA, WIDTH, HEIGHT) }

    private fun files(): AssetSource {
        val scenes = mapOf(MENU to MENU_SCENE, LEVEL to LEVEL_SCENE)
        return AssetSource { path ->
            runCatching { (if (path.value == MANIFEST_PATH) MANIFEST else scenes.getValue(path.value)).encodeToByteArray() }
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
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/menu.scene.json"}"""

        const val MENU_SCENE = """
{ "version": 1, "name": "menu", "nodes": [
  { "name": "MenuCamera", "components": [ { "component": "camera" } ] },
  { "name": "Title", "components": [ { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" } ] }
] }
"""

        const val LEVEL_SCENE = """
{ "version": 1, "name": "level", "nodes": [
  { "name": "LevelCamera", "components": [ { "component": "camera" } ] },
  { "name": "Door", "components": [ { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" } ] }
] }
"""
    }
}

/**
 * A capability whose scenes always run a healthy frame system, and whose first scene, the menu, also runs
 * a system that throws [failure] when the scene stops.
 */
private class ClosingCapability(private val failure: Exception) : SceneCapability {
    override val id = "com.example.harbor-town.closing"
    val healthy = StepCount()

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        plan.frame("healthy") { HealthyStepSystem(healthy) }
        if (scene.name == "menu") {
            plan.frame("closing") { FailsToClose(failure) }
        }
    }
}

private class StepCount {
    var calls = 0
}

private class HealthyStepSystem(private val counting: StepCount) : System {
    override fun update(world: World, delta: Float) {
        counting.calls++
    }
}

private class FailsToClose(private val failure: Exception) :
    System,
    AutoCloseable {
    override fun update(world: World, delta: Float) = Unit

    override fun close(): Unit = throw failure
}
