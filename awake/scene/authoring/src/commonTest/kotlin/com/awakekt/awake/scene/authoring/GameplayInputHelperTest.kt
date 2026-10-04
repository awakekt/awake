/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.authoring.infrastructure.gameplayInput
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** [gameplayInput] is the one place a system reads the input a game may act on. */
class GameplayInputHelperTest {

    private class Probe(private val onUpdate: () -> Unit) : System {
        override fun update(world: World, delta: Float) = onUpdate()
    }

    @Test
    fun theHelperReadsTheRunningInputAndWhatTheUiOwns() = runTest {
        var seen: GameplayInput? = null
        val game = app {
            scene("probe") {
                frameSystem("probe") { Probe { seen = gameplayInput() } }
            }
        }
        game.ready(RecordingRenderer())
        val input = game.requireService<Input>()
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        input.setKeyDown(Key.W, true)
        input.updateSnapshot()
        game.update(1f / 60f, 320f, 240f)
        assertTrue(assertNotNull(seen).isDown(Key.W), "the key held this frame reaches gameplay")

        runtime.uiOwnership = InputOwnership(isTextInputFocused = true)
        game.update(1f / 60f, 320f, 240f)
        assertFalse(assertNotNull(seen).isDown(Key.W), "a key the UI owns is not gameplay's")
        game.dispose()
    }
}
