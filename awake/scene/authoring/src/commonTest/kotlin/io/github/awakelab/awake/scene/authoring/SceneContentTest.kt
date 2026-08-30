/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.authoring

import io.github.awakelab.awake.compose.foundation.text.Text
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.ui.platform.LocalViewportSize
import io.github.awakelab.awake.compose.ui.platform.ViewportSize
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.engine.bootstrap.dsl.app
import io.github.awakelab.awake.engine.platform.dsl.requireService
import io.github.awakelab.awake.scene.authoring.blueprints.cameraEntity
import io.github.awakelab.awake.scene.runtime.LocalRenderer
import io.github.awakelab.awake.scene.runtime.LocalWorld
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What `content { }` guarantees, which nothing checked when it was added.
 *
 * It shipped with no coverage at all: it compiled, and a scene declaring one would have failed at
 * runtime with nothing pointing at why. That is the same silent-when-unarmed shape this repo keeps
 * finding, so the path gets its own test rather than being exercised incidentally.
 */
class SceneContentTest {

    @Test
    fun contentDrawsThroughTheComposeHost() = runTest {
        val renderer = RecordingRenderer()
        val game = app {
            scene("compose-scene") {
                cameraEntity("camera")
                content { Text("composed") }
            }
        }

        game.ready(renderer)
        game.update(0.016f, 320f, 240f)

        assertTrue(
            renderer.lastUiPrimitives.any { it is UiDrawPrimitive.Glyph },
            "content { } produced no glyphs -- the compose path did not run",
        )
    }

    @Test
    fun contentSeesTheViewportAndTheSceneServices() = runTest {
        val renderer = RecordingRenderer()
        var seenViewport: ViewportSize? = null
        var seenRenderer: Any? = null
        var worldWasProvided = false

        val game = app {
            scene("locals-scene") {
                cameraEntity("camera")
                content {
                    seenViewport = LocalViewportSize.current
                    seenRenderer = LocalRenderer.current
                    // Reading it at all is the assertion: the local throws when unprovided, so a
                    // runtime that forgot to provide one fails here rather than composing an empty
                    // world and drawing a blank screen.
                    LocalWorld.current
                    worldWasProvided = true
                    Text("locals")
                }
            }
        }

        game.ready(renderer)
        game.update(0.016f, 320f, 240f)

        assertEquals(ViewportSize(320, 240), seenViewport, "content did not see the host viewport")
        assertSame(renderer, seenRenderer)
        assertTrue(worldWasProvided)
    }

    @Test
    fun theSceneRuntimeIsStillReachableForServices() = runTest {
        val renderer = RecordingRenderer()
        val game = app {
            scene("named-scene") {
                cameraEntity("camera")
                content { Text("x") }
            }
        }

        game.ready(renderer)
        game.update(0.016f, 320f, 240f)

        assertEquals("named-scene", game.requireService<SceneAppLifecycleRuntime>().sceneName)
    }
}
