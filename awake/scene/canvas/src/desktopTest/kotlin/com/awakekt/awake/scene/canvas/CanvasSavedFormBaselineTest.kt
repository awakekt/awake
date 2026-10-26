/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.testing.assertMatchesBaseline
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasBar
import com.awakekt.awake.ui.canvas.CanvasBarDirection
import com.awakekt.awake.ui.canvas.CanvasButton
import com.awakekt.awake.ui.canvas.CanvasDirection
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasImage
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasMask
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasSweep
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A HUD saved before the split, as one `canvas_element` a node, draws pixel for pixel as it did,
 * and as the same HUD saved in the current form and as what a world that loaded it saves back. One
 * baseline holds all three: it was recorded from the older form, and matches what the code drew
 * before the split.
 */
class CanvasSavedFormBaselineTest {
    @Test
    fun aHudSavedInTheOlderFormDrawsAsItDidAndAsItsCurrentForm() {
        val older = listOf(hud())
        val current = older.map { it.withCanvasComponents() }
        val resaved = loadCanvas(older).saveCanvas()

        assertEquals(current, resaved, "a world that loaded the older form saves the current form")
        for (nodes in listOf(older, current, resaved)) {
            val world = loadCanvas(nodes)
            ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) { SceneCanvas(world, images = IMAGES, bindings = canvasRegistry.bindings) }
                .primitives.assertMatchesBaseline("scene-canvas-saved-forms", WIDTH, HEIGHT)
        }
    }

    /** One of each kind that draws without a camera or a followed node, nested and laid out. */
    private fun hud(): SceneNode = node(
        "Window",
        SceneCanvasElement(
            kind = CanvasElementKind.Panel, offsetX = 8f, offsetY = 8f, width = 304f, height = 176f, background = "#1C1F26",
            style = CanvasStyle(cornerRadius = 8f, borderWidth = 1f, borderColor = "#FFFFFF40"),
            text = "never drawn on a panel", camera = "never drawn either",
        ),
        node("Title", SceneCanvasElement(kind = CanvasElementKind.Text, offsetX = 12f, offsetY = 8f, width = 200f, height = 20f, text = "Status", fontSize = 16f, color = "#F0E6D2")),
        node(
            "Gauges",
            SceneCanvasElement(
                kind = CanvasElementKind.Panel,
                offsetX = 12f,
                offsetY = 36f,
                width = 180f,
                height = 40f,
                layout = CanvasLayout(direction = CanvasDirection.Column, gap = 4f),
            ),
            node("Health", SceneCanvasElement(kind = CanvasElementKind.Bar, width = 180f, height = 10f, value = 0.7f, color = "#E5484D", background = "#00000080")),
            node(
                "Energy",
                SceneCanvasElement(
                    kind = CanvasElementKind.Bar,
                    width = 180f,
                    height = 10f,
                    value = 0.4f,
                    color = "#3E63DD",
                    background = "#00000080",
                    bar = CanvasBar(segments = 5, segmentGap = 2f),
                ),
            ),
            node("Charge", SceneCanvasElement(kind = CanvasElementKind.Bar, width = 180f, height = 10f, value = 0.55f, bar = CanvasBar(direction = CanvasBarDirection.RightToLeft))),
        ),
        node("Cooldown", SceneCanvasElement(kind = CanvasElementKind.Sweep, offsetX = 212f, offsetY = 36f, width = 40f, height = 40f, value = 0.35f, color = "#FFB224", sweep = CanvasSweep(mask = CanvasMask.Circle, track = "#00000060"))),
        node("Portrait", SceneCanvasElement(kind = CanvasElementKind.Image, offsetX = 260f, offsetY = 36f, width = 32f, height = 32f, style = CanvasStyle(image = CanvasImage("portrait.png", pixelated = true)))),
        node(
            "Use",
            SceneCanvasElement(
                kind = CanvasElementKind.Button, offsetX = 12f, offsetY = 88f, width = 96f, height = 32f, text = "Use", fontSize = 14f,
                background = "#30A46C", action = "use", button = CanvasButton(cooldownSeconds = 1f), style = CanvasStyle(cornerRadius = 6f),
            ),
        ),
        node("Count", SceneCanvasElement(kind = CanvasElementKind.Stepper, offsetX = 120f, offsetY = 88f, width = 120f, height = 32f, stepper = CanvasStepper(value = 3f, max = 9f), background = "#2A2A2A")),
        node("Name", SceneCanvasElement(kind = CanvasElementKind.TextField, offsetX = 12f, offsetY = 132f, width = 200f, height = 28f, text = "Ada", background = "#0E1015", maxLength = 12)),
        node("Pin", SceneCanvasElement(kind = CanvasElementKind.TextField, anchor = CanvasAnchor.TopRight, offsetX = 12f, offsetY = 132f, width = 72f, height = 28f, text = "1234", mask = "*", background = "#0E1015")),
    )

    private fun node(name: String, element: SceneCanvasElement, vararg children: SceneNode) =
        SceneNode(name = name, components = listOf(element), children = children.toList())

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 192

        /** A 4 by 4 checker of two colours, drawn pixelated. */
        val IMAGES = mapOf(
            "portrait.png" to ImageBitmap(
                4,
                4,
                ByteArray(4 * 4 * 4) { index ->
                    val pixel = index / 4
                    val light = (pixel % 4 + pixel / 4) % 2 == 0
                    when (index % 4) {
                        3 -> 0xFF.toByte()
                        else -> (if (light) 0xD0 else 0x40).toByte()
                    }
                },
            ),
        )
    }
}
