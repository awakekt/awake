/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.dsl.cameraEntity
import com.awakekt.awake.scene.runtime.DeferredSceneRoute
import com.awakekt.awake.scene.runtime.LoadedSceneRoute
import com.awakekt.awake.scene.runtime.SceneAppSpec
import com.awakekt.awake.scene.runtime.SceneRouteStatus
import com.awakekt.awake.scene.runtime.SceneRouterRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A route whose scene is supplied after the app starts. Loads run on [Dispatchers.Unconfined], so a load
 * runs up to its first suspension as it starts, and on as soon as what it waits for completes: each test
 * decides the order exactly.
 */
class DeferredSceneRouteTest {
    private val renderer = DeferredRouteRenderer()

    @Test
    fun enteringBeforeTheSceneIsSuppliedShowsTheLoadingSceneUntilItArrives() = runTest {
        val signedIn = CompletableDeferred<String>()
        var loads = 0
        val game = routedApp {
            loads++
            worldScene(signedIn.await())
        }
        val router = game.requireService<SceneRouterRuntime>()
        assertEquals(SceneRouteStatus.NotLoaded, router.routeStatus("world"), "nothing loads before the route is entered")

        router.switchTo("world")
        game.frame()
        game.frame()

        assertEquals("world", router.activeSceneId, "the route is entered while it loads")
        assertEquals("world-loading", router.sceneRuntime.sceneName)
        assertEquals(SceneRouteStatus.Loading, router.activeRouteStatus)

        signedIn.complete("player-1")
        game.frame()

        assertEquals("world:player-1", router.sceneRuntime.sceneName, "the supplied scene replaces the loading one")
        assertEquals(SceneRouteStatus.Loaded, router.activeRouteStatus)
        assertEquals(1, loads)
    }

    @Test
    fun enteringAfterTheSceneIsSuppliedPlaysItAtOnce() = runTest {
        val signedIn = CompletableDeferred<String>()
        val game = routedApp { worldScene(signedIn.await()) }
        val router = game.requireService<SceneRouterRuntime>()

        signedIn.complete("player-1")
        router.switchTo("world")
        game.frame()

        assertEquals("world:player-1", router.sceneRuntime.sceneName, "no loading frame for a source that is ready")
        assertEquals(SceneRouteStatus.Loaded, router.activeRouteStatus)
    }

    @Test
    fun aPreloadedRouteLoadsBeforeItIsEnteredAndThenPlaysAtOnce() = runTest {
        val signedIn = CompletableDeferred<String>()
        var loads = 0
        val game = routedApp {
            loads++
            worldScene(signedIn.await())
        }
        val router = game.requireService<SceneRouterRuntime>()

        router.preload("world")
        game.frame()
        assertEquals("menu", router.activeSceneId, "preloading enters nothing")
        assertEquals(SceneRouteStatus.Loading, router.routeStatus("world"))

        signedIn.complete("player-1")
        game.frame()
        assertEquals(SceneRouteStatus.Loaded, router.routeStatus("world"))
        assertEquals("menu", router.activeSceneId)

        router.switchTo("world")
        game.frame()
        assertEquals("world:player-1", router.sceneRuntime.sceneName)
        assertEquals(1, loads)
    }

    @Test
    fun aFailedLoadStaysOnTheLoadingSceneUntilTheRouteIsEnteredAgain() = runTest {
        val attempts = mutableListOf<CompletableDeferred<String>>()
        val game = routedApp {
            val attempt = CompletableDeferred<String>().also(attempts::add)
            worldScene(attempt.await())
        }
        val router = game.requireService<SceneRouterRuntime>()

        router.switchTo("world")
        game.frame()
        attempts.single().completeExceptionally(IllegalStateException("token refused"))
        game.frame()

        val failed = assertIs<SceneRouteStatus.Failed>(router.activeRouteStatus)
        assertEquals("token refused", failed.cause.message)
        assertEquals("world", router.activeSceneId)
        assertEquals("world-loading", router.sceneRuntime.sceneName, "a failure leaves the loading scene up")
        game.frame()
        assertEquals(1, attempts.size, "a failure is not retried on its own")

        router.switchTo("world")
        game.frame()
        assertEquals(2, attempts.size, "entering the route again loads it again")
        assertEquals(SceneRouteStatus.Loading, router.activeRouteStatus)

        attempts.last().complete("player-1")
        game.frame()
        assertEquals("world:player-1", router.sceneRuntime.sceneName)
    }

    @Test
    fun leavingWhileItLoadsNeverSwitchesBackAndKeepsWhatLoadedForTheNextEntry() = runTest {
        val signedIn = CompletableDeferred<String>()
        var loads = 0
        val game = routedApp {
            loads++
            worldScene(signedIn.await())
        }
        val router = game.requireService<SceneRouterRuntime>()

        router.switchTo("world")
        game.frame()
        router.switchTo("menu")
        game.frame()
        signedIn.complete("player-1")
        repeat(3) { game.frame() }

        assertEquals("menu", router.activeSceneId, "a load that finishes after the player left must not switch to it")
        assertEquals("menu", router.sceneRuntime.sceneName)
        assertEquals(SceneRouteStatus.Loaded, router.routeStatus("world"), "the load went on and is kept")

        router.switchTo("world")
        game.frame()
        assertEquals("world:player-1", router.sceneRuntime.sceneName)
        assertEquals(1, loads)
    }

    @Test
    fun aKeptRouteLoadsOnceAndOneThatKeepsNothingLoadsOnEveryEntryAndReleasesWhatItLeaves() = runTest {
        var keptLoads = 0
        var freshLoads = 0
        var released = 0
        val game = app {
            scenes {
                route("menu") { cameraEntity("camera") }
                deferredRoute("kept", loadContext = Dispatchers.Unconfined) {
                    keptLoads++
                    worldScene("kept")
                }
                route(
                    DeferredSceneRoute(
                        id = "fresh",
                        loading = sceneApp { name("fresh-loading") },
                        keepLoaded = false,
                        loadContext = Dispatchers.Unconfined,
                    ) {
                        freshLoads++
                        LoadedSceneRoute(worldScene("fresh-$freshLoads")) { released++ }
                    },
                )
            }
        }
        game.ready(renderer)
        game.frame()
        val router = game.requireService<SceneRouterRuntime>()

        repeat(2) {
            router.switchTo("kept")
            game.frame()
            router.switchTo("fresh")
            game.frame()
            router.switchTo("menu")
            game.frame()
        }

        assertEquals(1, keptLoads, "a kept route plays what it loaded again")
        assertEquals(2, freshLoads, "a route that keeps nothing loads on every entry")
        assertEquals(2, released, "each fresh load is released as its route is left")
        assertEquals(SceneRouteStatus.Loaded, router.routeStatus("kept"))
        assertEquals(SceneRouteStatus.NotLoaded, router.routeStatus("fresh"))
    }

    @Test
    fun aDeferredInitialRouteStartsOnItsLoadingScene() = runTest {
        val signedIn = CompletableDeferred<String>()
        val game = app {
            scenes {
                deferredRoute("world", loading = { cameraEntity("camera") }, loadContext = Dispatchers.Unconfined) {
                    worldScene(signedIn.await())
                }
                route("menu") { cameraEntity("camera") }
            }
        }
        game.ready(renderer)
        game.frame()
        val router = game.requireService<SceneRouterRuntime>()
        assertEquals("world-loading", router.sceneRuntime.sceneName)

        signedIn.complete("player-1")
        game.frame()
        assertEquals("world:player-1", router.sceneRuntime.sceneName)
    }

    @Test
    fun stoppingTheAppCancelsALoadAndReleasesWhatLoadedButNeverPlayed() = runTest {
        val signedIn = CompletableDeferred<String>()
        var cancelled = false
        var released = 0
        val game = app {
            scenes {
                route("menu") { cameraEntity("camera") }
                route(
                    DeferredSceneRoute("ready", loading = sceneApp {}, loadContext = Dispatchers.Unconfined) {
                        LoadedSceneRoute(worldScene("ready")) { released++ }
                    },
                )
                deferredRoute("waiting", loadContext = Dispatchers.Unconfined) {
                    try {
                        worldScene(signedIn.await())
                    } finally {
                        cancelled = !signedIn.isCompleted
                    }
                }
            }
        }
        game.ready(renderer)
        game.frame()
        val router = game.requireService<SceneRouterRuntime>()
        router.preload("ready")
        router.preload("waiting")
        game.frame()

        game.dispose()

        assertTrue(cancelled, "the load still waiting is cancelled")
        assertEquals(1, released, "what loaded and never played is released")
    }

    @Test
    fun eagerRoutesAreAlwaysLoaded() = runTest {
        val game = routedApp { worldScene("player-1") }
        val router = game.requireService<SceneRouterRuntime>()

        assertEquals(SceneRouteStatus.Loaded, router.routeStatus("menu"))
        assertEquals(SceneRouteStatus.Loaded, router.activeRouteStatus)
    }

    private suspend fun routedApp(load: suspend () -> SceneAppSpec): AwakeAppLifecycle {
        val game = app {
            scenes {
                route("menu") { cameraEntity("camera") }
                deferredRoute("world", label = "World", loadContext = Dispatchers.Unconfined, load = load)
            }
        }
        game.ready(renderer)
        game.frame()
        return game
    }

    private fun worldScene(player: String): SceneAppSpec = sceneApp {
        name("world:$player")
        cameraEntity("camera")
    }

    private fun AwakeAppLifecycle.frame() = update(1f / 60f, 320f, 240f)
}

private class DeferredRouteRenderer :
    Renderer,
    GpuDrawPreparationSource {
    override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    override val clipSpace: ClipSpace = ClipSpace.WebGpu
    override var clearColor: Color = Color.Black
    override var wireframe: Boolean = false

    override fun createMesh(geometry: MeshGeometry): Mesh = object : Mesh {
        override val format = geometry.format
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    override fun createMaterial(
        texture: TextureAsset?,
        renderTarget: RenderTarget?,
        uniformFloatCount: Int,
        pbrTextures: PbrTextureSet?,
    ): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    override fun createRenderTarget(width: Int, height: Int): RenderTarget = object : RenderTarget {
        override val width: Int = width
        override val height: Int = height
        override fun destroy() = Unit
    }

    override suspend fun readPixels(target: RenderTarget): TextureAsset =
        TextureAsset(ByteArray(target.width * target.height * 4), target.width, target.height)

    override fun draw(input: GpuPassInput) = Unit

    override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit

    override fun drawDebugLines(lines: List<LineSegment>) = Unit

    override fun destroy() = Unit
}
