/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasBindingProjectTest {
    @Test
    fun aPlayedProjectsBarFollowsAComponentField() = runTest {
        val project = loadProject(PROJECT)
        val game = app { scene("play") { runProject(project) } }
        game.ready(DocsRenderer())
        game.update(FRAME, WIDTH, HEIGHT)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        val fill = runtime.uiPrimitives.filterIsInstance<UiDrawPrimitive.Quad>().single { it.color == Color.fromHex("#FF0000") }

        assertEquals(60f, fill.w, 1f, "speed 6 of 10 fills 60 of the bar's 100")
    }

    private companion object {
        val PROJECT = AssetSource { path ->
            runCatching {
                when (path.value) {
                    "awake.project.json" ->
                        """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
                    "scenes/main.scene.json" -> """
                        { "version": 1, "name": "hud", "nodes": [
                          { "name": "Player", "components": [ { "component": "movement_control", "speed": 6.0 } ] },
                          { "name": "Speed", "components": [ { "component": "canvas_element", "kind": "Bar",
                            "offsetX": 0, "offsetY": 0, "width": 100, "height": 10, "color": "#FF0000",
                            "bind": { "node": "Player", "value": "movement_control.speed", "max": "10" } } ] }
                        ] }
                    """.trimIndent()
                    else -> error("no ${path.value}")
                }.encodeToByteArray()
            }
        }
    }
}
