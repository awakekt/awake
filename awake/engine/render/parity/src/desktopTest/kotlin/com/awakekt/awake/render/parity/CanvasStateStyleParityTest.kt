/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.canvas.CanvasPrefabs
import com.awakekt.awake.scene.canvas.CanvasSceneComponents
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.document.SceneDocument
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pressed and hovered state feedback (`shadow`, `scale` and `alpha`) authored purely in scene JSON
 * renders on both headless Vulkan and headless WebGPU.
 *
 * Negative control: bypassing `CanvasStateStyle.shadow` and `scale` keeps the pressed button
 * unscaled over its idle blue shadow (`[140, 0, 63, 203]` at `(30, 64)` instead of unpainted `<= 10`).
 */
class CanvasStateStyleParityTest {

    @Test
    fun pressedButtonChangesShadowScaleAndAlphaOnVulkanAndWebGpu() {
        val world = loadSceneWorld()
        val host = ComposeHost()
        val idlePrimitives = host.frame(FrameInput(SIZE, SIZE)) { SceneCanvas(world) }.primitives.toList()
        val pressedPrimitives = host.frame(
            FrameInput(SIZE, SIZE, pointerX = 64, pointerY = 64, pointerDown = true, pointerPressed = true),
        ) { SceneCanvas(world) }.primitives.toList()

        listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu).forEach { backend ->
            val (idle, pressed) = withHeadlessUi(backend, SIZE) { renderer ->
                fun renderFrame(primitives: List<UiDrawPrimitive>): ByteArray {
                    val target = renderer.createRenderTarget(SIZE, SIZE)
                    return try {
                        renderer.drawUiToTexture(target, primitives, font = null)
                        runBlocking { renderer.readPixels(target) }.data
                    } finally {
                        target.destroy()
                    }
                }
                renderFrame(idlePrimitives) to renderFrame(pressedPrimitives)
            }

            fun ByteArray.rgba(x: Int, y: Int): List<Int> = (0..3).map { this[(y * SIZE + x) * 4 + it].toInt() and 0xFF }

            val idleCentre = idle.rgba(64, 64)
            val pressedCentre = pressed.rgba(64, 64)
            assertTrue(idleCentre[0] >= 240 && idleCentre[3] >= 250, "$backend idle centre opaque red: $idleCentre")
            assertTrue(
                pressedCentre[0] in 120..165 && pressedCentre[3] < idleCentre[3],
                "$backend pressed centre dimmed by alpha=0.55: $pressedCentre vs $idleCentre",
            )

            val idleEdge = idle.rgba(30, 64)
            val pressedEdge = pressed.rgba(30, 64)
            assertTrue(idleEdge[0] >= 240, "$backend idle unscaled x=30 inside [24..104] is red: $idleEdge")
            assertTrue(
                pressedEdge[0] <= 10 && pressedEdge[3] <= 10,
                "$backend pressed 0.5x scale [44..84] leaves x=30 unpainted: $pressedEdge",
            )

            val idleBelow = idle.rgba(64, 112)
            val pressedBelow = pressed.rgba(64, 112)
            assertTrue(idleBelow[2] >= 80 && idleBelow[0] == 0, "$backend idle blue shadow below button: $idleBelow")
            assertTrue(
                pressedBelow[2] <= 10 && pressedBelow[3] <= 10,
                "$backend pressed replaces idle bottom shadow: $pressedBelow",
            )

            val pressedRightGlow = pressed.rgba(96, 64)
            assertTrue(
                pressedRightGlow[1] >= 40 && pressedRightGlow[2] >= 40 && pressedRightGlow[0] == 0,
                "$backend pressed cyan glow shadow to the right of scaled button: $pressedRightGlow",
            )
        }
    }

    private fun loadSceneWorld(): World {
        val document = CanvasPrefabs.json.decodeFromString(SceneDocument.serializer(), SCENE_JSON)
        val registry = SceneComponentRegistry(bindings = CanvasSceneComponents.bindings)
        val world = World()
        val context = object : SceneResolutionContext {
            override val world: World = world

            override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

            override fun recordRequest(request: Any) = Unit
        }
        for (node in document.nodes) {
            val entity = world.create()
            for (component in node.components) registry.resolve(world, entity, component, context)
        }
        return world
    }

    private companion object {
        const val SIZE = 128
        val SCENE_JSON = """
            {
              "version": 1,
              "name": "StateParity",
              "nodes": [
                {
                  "name": "Button",
                  "components": [
                    {
                      "component": "canvas_rect",
                      "offsetX": 24.0,
                      "offsetY": 24.0,
                      "width": 80.0,
                      "height": 80.0
                    },
                    {
                      "component": "canvas_appearance",
                      "background": "#FF0000",
                      "style": {
                        "cornerRadius": 12.0,
                        "shadow": {
                          "color": "#0000FF",
                          "offsetY": 16.0,
                          "blur": 4.0
                        },
                        "pressed": {
                          "alpha": 0.55,
                          "scale": 0.5,
                          "shadow": {
                            "color": "#00FFFF",
                            "offsetX": 24.0,
                            "offsetY": 0.0,
                            "blur": 6.0,
                            "spread": 6.0
                          }
                        }
                      }
                    },
                    {
                      "component": "canvas_button",
                      "action": "activate"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
