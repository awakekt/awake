/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.project.ContentCache
import com.awakekt.awake.project.ContentResponse
import com.awakekt.awake.project.ContentTransport
import com.awakekt.awake.project.IndexedAssetSource
import com.awakekt.awake.project.ProjectIndex
import com.awakekt.awake.project.ProjectIndexEntry
import com.awakekt.awake.project.ReadToken
import com.awakekt.awake.project.TokenAuthorizedFetch
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * The "Protected content" guide's route to a project loaded after sign-in includes these samples, so they
 * keep compiling and doing what it says. They load on the route's real dispatcher, so the test plays
 * frames until the project arrives, as a game does.
 */
class ProtectedProjectRouteDocsSampleTest {
    private val files = mapOf(
        "awake.project.json" to MANIFEST.encodeToByteArray(),
        "scenes/main.scene.json" to SCENE.encodeToByteArray(),
    )
    private val index = ProjectIndex(
        files = files.map { (path, bytes) -> ProjectIndexEntry(path, bytes.size.toLong(), Sha256.digestHex(bytes), "files/$path") },
    )

    /** Stands in for the game's own HTTP client, which Core doesn't carry. */
    private inner class GameHttpClient(private val acceptedToken: String) {
        val sent = mutableListOf<Map<String, String>>()

        suspend fun get(url: String, headers: Map<String, String>): Pair<Int, ByteArray> {
            sent += headers
            val path = url.removePrefix("https://content.example.com/release/")
            val body = when (path) {
                "project.index.json" -> Json.encodeToString(ProjectIndex.serializer(), index).encodeToByteArray()
                else -> files[path.removePrefix("files/")]
            }
            return when {
                headers["Authorization"] != "Bearer $acceptedToken" -> 401 to ByteArray(0)
                body == null -> 404 to ByteArray(0)
                else -> 200 to body
            }
        }
    }

    /** Stands in for what the game's own server answers once its player has signed in. */
    private class PlayerSession(private val token: String) {
        suspend fun readToken(): ReadToken = ReadToken(token, expiresIn = 3600.seconds)
    }

    @Test
    fun aProtectedProjectPlaysOnceThePlayerHasSignedIn() = runTest {
        val httpClient = GameHttpClient(acceptedToken = "player-token")
        val (game, router) = sampleApp(httpClient)
        assertEquals("sign-in", router.activeSceneId)

        val session = PlayerSession("player-token")
        // --8<-- [start:signed-in]
        // The sign-in scene, once the game's server has signed the player in:
        signedIn.complete(session)
        router.switchTo("harbor-town")
        // --8<-- [end:signed-in]

        game.frameUntil { router.activeRouteStatus == SceneRouteStatus.Loaded }
        assertEquals("harbor-town", router.activeSceneId)
        assertTrue(router.sceneRuntime.world.hasNamed("Crate"), "the protected project plays")
        assertTrue(httpClient.sent.all { it["Authorization"] == "Bearer player-token" }, "every read carried the token")
        game.dispose()
    }

    @Test
    fun aRefusedTokenFailsTheLoadAndTheLoadingSceneSendsThePlayerBack() = runTest {
        val httpClient = GameHttpClient(acceptedToken = "another-token")
        val (game, router) = sampleApp(httpClient)

        signedIn.complete(PlayerSession("player-token"))
        router.switchTo("harbor-town")

        game.frameUntil { router.activeSceneId == "sign-in" }
        assertIs<SceneRouteStatus.Failed>(router.routeStatus("harbor-town"))
        game.dispose()
    }

    private lateinit var signedIn: CompletableDeferred<PlayerSession>

    private suspend fun sampleApp(httpClient: GameHttpClient): Pair<AwakeAppLifecycle, SceneRouterRuntime> {
        val contentCache = MemoryContentCache()
        val transport = ContentTransport { request ->
            val (status, body) = httpClient.get("https://content.example.com/release/" + request.url, request.headers)
            ContentResponse(status, body)
        }

        // --8<-- [start:route]
        // Completed by the sign-in scene once the player has signed in.
        val signedIn = CompletableDeferred<PlayerSession>()

        val game = app {
            scenes {
                route("sign-in", label = "Sign in") {
                    cameraEntity("camera") // and the game's sign-in screen
                }
                projectRoute(
                    "harbor-town",
                    label = "Harbor Town",
                    loading = {
                        ui { Text("Loading Harbor Town...") }
                        update { _, _ ->
                            // Back to sign-in when the content can't be read; entering again loads again.
                            val router = requireService(SceneRouterRuntime::class)
                            if (router.activeRouteStatus is SceneRouteStatus.Failed) router.switchTo("sign-in")
                        }
                    },
                ) {
                    // Runs off the frame thread the first time the route is entered, and waits for sign-in.
                    val session = signedIn.await()
                    val contentFetch = TokenAuthorizedFetch(transport) { session.readToken() }
                    loadProject(IndexedAssetSource.open(fetch = contentFetch, cache = contentCache))
                }
            }
        }
        val router = game.requireService<SceneRouterRuntime>()
        // --8<-- [end:route]

        this.signedIn = signedIn
        game.ready(TestRenderer())
        game.frame()
        return game to router
    }

    /** Plays frames, as a game does, until [condition] holds; the route loads on its own dispatcher meanwhile. */
    private suspend fun AwakeAppLifecycle.frameUntil(condition: () -> Boolean) {
        repeat(MAX_FRAMES) {
            frame()
            if (condition()) return
            withContext(Dispatchers.Default) { delay(FRAME_MILLIS) }
        }
        fail("Nothing happened in $MAX_FRAMES frames")
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

    private class MemoryContentCache : ContentCache {
        private val stored = mutableMapOf<String, ByteArray>()
        override suspend fun get(sha256: String): ByteArray? = stored[sha256]
        override suspend fun put(sha256: String, bytes: ByteArray) {
            stored[sha256] = bytes
        }
    }

    private companion object {
        const val MAX_FRAMES = 1000
        const val FRAME_MILLIS = 5L
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
