/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.module
import com.awakekt.awake.engine.compose.composeAppModule
import com.awakekt.awake.scene.authoring.dsl.cameraEntity
import com.awakekt.awake.scene.runtime.ui.sceneComposeAppModule
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The "UI with AwakeKt Compose" guide shows the three ways UI reaches the screen. Each is included
 * from here and runs one frame against a recording renderer, which must receive text.
 */
class UiHostingDocsSampleTest {

    @Test
    fun aUiOnlyAppDrawsItsContent() = runTest {
        // --8<-- [start:ui-only-app]
        val game = app {
            module(composeAppModule(content = { Text("Hello from AwakeKt") }))
        }
        // --8<-- [end:ui-only-app]

        assertDrawsText { game.ready(it); game.update(1f / 60f, 320f, 240f) }
    }

    @Test
    fun aSceneDrawsItsUiOverTheGame() = runTest {
        // --8<-- [start:scene-ui]
        val game = app {
            scene("level") {
                cameraEntity("camera")
                ui { Text("Score 0") }
            }
        }
        // --8<-- [end:scene-ui]

        assertDrawsText { game.ready(it); game.update(1f / 60f, 320f, 240f) }
    }

    @Test
    fun anAppLevelHostDrawsOverAScene() = runTest {
        // --8<-- [start:app-ui-over-scene]
        val game = app {
            module(sceneComposeAppModule(content = { Text("Paused") }))
            scene("level") {
                cameraEntity("camera")
            }
        }
        // --8<-- [end:app-ui-over-scene]

        assertDrawsText { game.ready(it); game.update(1f / 60f, 320f, 240f) }
    }

    private suspend fun assertDrawsText(run: suspend (RecordingRenderer) -> Unit) {
        val renderer = RecordingRenderer()
        run(renderer)
        assertTrue(renderer.lastUiPrimitives.any { it is UiDrawPrimitive.Glyph }, "no text reached the renderer")
    }
}
