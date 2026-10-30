/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasStateStyleSceneTest {

    @Test
    fun sceneJsonLoadsValidatesRoundTripsAndDrawsPressedFeedback() {
        val document = CanvasPrefabs.json.decodeFromString(SceneDocument.serializer(), SCENE_JSON)
        assertEquals(emptyList(), SceneValidator.validate(document))

        val world = loadCanvas(document.nodes)
        val exported = document.copy(nodes = world.saveCanvas())
        assertEquals(document, exported)

        val host = ComposeHost()
        fun render(px: Int = FrameInput.UNKNOWN_POINTER, py: Int = FrameInput.UNKNOWN_POINTER, down: Boolean = false): ByteArray =
            host.frame(FrameInput(SIZE, SIZE, pointerX = px, pointerY = py, pointerDown = down, pointerPressed = down)) {
                SceneCanvas(world)
            }.primitives.rasterize(SIZE, SIZE, Color.Black)

        val idle = render()
        assertEquals(255, idle[(50 * SIZE + 50) * 4].toInt() and 0xFF)
        assertEquals(255, idle[(84 * SIZE + 50) * 4 + 2].toInt() and 0xFF, "idle blue shadow below")

        val pressed = render(px = 50, py = 50, down = true)
        assertTrue((pressed[(50 * SIZE + 50) * 4].toInt() and 0xFF) in 126..129, "pressed alpha=0.5 dims centre")
        assertEquals(0, pressed[(50 * SIZE + 24) * 4].toInt() and 0xFF, "pressed scale=0.5 shrinks fill inward")
        assertTrue((pressed[(50 * SIZE + 70) * 4 + 1].toInt() and 0xFF) > 100, "pressed green shadow shifts right")
    }

    private companion object {
        const val SIZE = 100
        val SCENE_JSON = """
            {
              "version": 1,
              "name": "ButtonFeedback",
              "nodes": [
                {
                  "name": "Controls",
                  "components": [
                    {
                      "component": "canvas_controls",
                      "theme": {
                        "preset": "None",
                        "transitionSeconds": 0.1
                      }
                    }
                  ]
                },
                {
                  "name": "FireButton",
                  "components": [
                    {
                      "component": "canvas_appearance",
                      "background": "#FF0000",
                      "style": {
                        "transitionSeconds": 0.0,
                        "shadow": {
                          "color": "#0000FF",
                          "offsetY": 10.0,
                          "blur": 0.0
                        },
                        "hovered": {
                          "scale": 1.1
                        },
                        "pressed": {
                          "alpha": 0.5,
                          "shadow": {
                            "color": "#00FF00",
                            "offsetX": 14.0,
                            "offsetY": 0.0,
                            "blur": 0.0,
                            "spread": 4.0
                          },
                          "scale": 0.5
                        }
                      }
                    },
                    {
                      "component": "canvas_button",
                      "action": "fire"
                    },
                    {
                      "component": "canvas_rect",
                      "offsetX": 20.0,
                      "offsetY": 20.0,
                      "width": 60.0,
                      "height": 60.0
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
