/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.dsl.cameraEntity
import com.awakekt.awake.scene.authoring.scenes
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.runtime.SceneRouteStatus
import com.awakekt.awake.scene.runtime.SceneRouterRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A routed app whose project is supplied after it starts. Loads run on [Dispatchers.Unconfined], so each
 * test decides exactly when the project's files become readable.
 */
class ProjectRouteTest {
    private val signedIn = CompletableDeferred<AssetSource>()
    private val loaded = mutableListOf<LoadedProject>()

    @Test
    fun enteringBeforeTheProjectIsSuppliedShowsTheLoadingSceneThenPlaysIt() = runTest {
        val (game, router) = routedApp()

        router.switchTo("harbor-town")
        game.frame()
        assertEquals("harbor-town-loading", router.sceneRuntime.sceneName)
        assertEquals(SceneRouteStatus.Loading, router.activeRouteStatus)

        signedIn.complete(files())
        game.frame()

        assertEquals("harbor", router.sceneRuntime.sceneName, "the project's scene replaces the loading one")
        assertTrue(router.sceneRuntime.world.hasNamed("Crate"), "the project's nodes are in the world")
        assertEquals(SceneRouteStatus.Loaded, router.activeRouteStatus)
    }

    @Test
    fun enteringAfterTheProjectIsSuppliedPlaysItAtOnce() = runTest {
        val (game, router) = routedApp()

        signedIn.complete(files())
        router.switchTo("harbor-town")
        game.frame()

        assertEquals("harbor", router.sceneRuntime.sceneName)
        assertTrue(router.sceneRuntime.world.hasNamed("Crate"))
    }

    @Test
    fun leavingTheRouteClosesTheProjectAndEnteringAgainLoadsAnother() = runTest {
        val (game, router) = routedApp()
        signedIn.complete(files())

        router.switchTo("harbor-town")
        game.frame()
        router.switchTo("sign-in")
        game.frame()

        assertTrue(loaded.single().closed, "a project plays once, so leaving its route closes it")
        assertEquals(SceneRouteStatus.NotLoaded, router.routeStatus("harbor-town"))

        router.switchTo("harbor-town")
        game.frame()
        assertEquals(2, loaded.size, "the next entry loads the project again")
        assertFalse(loaded.last().closed)
        assertTrue(router.sceneRuntime.world.hasNamed("Crate"))
    }

    @Test
    fun aProjectThatFailsToLoadLeavesTheLoadingSceneUntilTheRouteIsEnteredAgain() = runTest {
        var source: AssetSource = AssetSource { path -> Result.failure(NoSuchElementException(path.value)) }
        val (game, router) = routedApp { loadProject(source).also(loaded::add) }

        router.switchTo("harbor-town")
        game.frame()

        assertIs<SceneRouteStatus.Failed>(router.activeRouteStatus)
        assertEquals("harbor-town-loading", router.sceneRuntime.sceneName)

        source = files()
        router.switchTo("harbor-town")
        game.frame()
        assertEquals("harbor", router.sceneRuntime.sceneName)
    }

    @Test
    fun aProjectLoadedAfterThePlayerLeftWaitsForTheNextEntryAndClosesWithTheApp() = runTest {
        val (game, router) = routedApp()

        router.switchTo("harbor-town")
        game.frame()
        router.switchTo("sign-in")
        game.frame()
        signedIn.complete(files())
        repeat(3) { game.frame() }

        assertEquals("sign-in", router.activeSceneId, "a load that finishes after the player left must not switch to it")
        assertEquals(SceneRouteStatus.Loaded, router.routeStatus("harbor-town"))
        assertFalse(loaded.single().closed, "kept for the next entry")

        game.dispose()
        assertTrue(loaded.single().closed, "a project that never played is closed with the app")
    }

    private suspend fun routedApp(
        project: suspend () -> LoadedProject = { loadProject(signedIn.await()).also(loaded::add) },
    ): Pair<AwakeAppLifecycle, SceneRouterRuntime> {
        val game = app {
            scenes {
                route("sign-in") { cameraEntity("camera") }
                projectRoute("harbor-town", label = "Harbor Town", loadContext = Dispatchers.Unconfined, project = project)
            }
        }
        game.ready(TestRenderer())
        game.frame()
        return game to game.requireService<SceneRouterRuntime>()
    }

    private fun AwakeAppLifecycle.frame() = update(1f / 60f, 800f, 600f)

    private fun World.hasNamed(name: String): Boolean {
        var found = false
        queryEach(Name::class) { _, value -> if (value.value == name) found = true }
        return found
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private fun files() = AssetSource { path ->
        runCatching { mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to SCENE).getValue(path.value).encodeToByteArray() }
    }

    private companion object {
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Camera", "transform": { "position": { "x": 0.0, "y": 2.0, "z": 6.0 } }, "components": [
    { "component": "camera", "primary": true }
  ] },
  { "name": "Crate", "transform": { "position": { "x": 0.0, "y": 0.5, "z": 0.0 } }, "components": [
    { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" }
  ] }
] }
"""
    }
}
