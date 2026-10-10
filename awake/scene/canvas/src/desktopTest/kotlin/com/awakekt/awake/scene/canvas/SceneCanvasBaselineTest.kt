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
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test

class SceneCanvasBaselineTest {
    @Test
    fun aStatusWindowWithItsGaugesAtTwiceItsSize() {
        val world = World()
        val window = world.create().also {
            world.add(
                it,
                CanvasElement().apply {
                    kind = CanvasElementKind.Panel; offsetX = 8f; offsetY = 8f; width = 120f; height = 52f
                    style = CanvasStyle(image = CanvasImage("frame.png", sliceLeft = 4, sliceTop = 4, sliceRight = 4, sliceBottom = 4, repeatEdges = true, pixelated = true))
                },
            )
        }
        for ((row, value) in listOf(0.8f, 0.45f, 0.2f).withIndex()) {
            world.create().also {
                world.add(it, Transform(parent = window))
                world.add(
                    it,
                    CanvasElement().apply {
                        kind = CanvasElementKind.Bar; offsetX = 10f; offsetY = 10f + row * 12f; width = 100f; height = 8f; this.value = value
                        background = "#00000080"
                        style = CanvasStyle(fillImage = CanvasImage("gauge.png", sliceLeft = 2, sliceRight = 2, tint = TINTS[row], pixelated = true))
                    },
                )
            }
        }

        ComposeHost().frame(FrameInput(272, 136)) {
            SceneCanvas(world, images = mapOf("frame.png" to FRAME, "gauge.png" to GAUGE), scale = 2f)
        }.primitives.assertMatchesBaseline("scene-canvas-status-window", 272, 136)
    }

    @Test
    fun corneredBorderedGradientAndShadowedElements() {
        val world = World()
        fun element(configure: CanvasElement.() -> Unit) = world.create().also { world.add(it, CanvasElement().apply(configure)) }
        element {
            kind = CanvasElementKind.Button; offsetX = 16f; offsetY = 16f; width = 120f; height = 40f
            style = CanvasStyle(
                gradient = CanvasGradient("#4A6FD8", "#2A3F8A"),
                cornerRadius = 10f,
                borderWidth = 1f,
                borderColor = "#FFFFFF60",
                shadow = CanvasShadow(color = "#000000A0", offsetY = 4f, blur = 8f),
            )
        }
        element {
            kind = CanvasElementKind.Panel; offsetX = 152f; offsetY = 16f; width = 80f; height = 40f; background = "#2E2A24"
            style = CanvasStyle(cornerRadius = 6f, borderWidth = 2f, borderColor = "#C8A060")
        }
        element {
            kind = CanvasElementKind.Bar; offsetX = 16f; offsetY = 80f; width = 216f; height = 12f; value = 0.65f
            color = "#30A46C"; background = "#00000080"
            style = CanvasStyle(cornerRadius = 6f)
        }
        element {
            kind = CanvasElementKind.Panel; offsetX = 16f; offsetY = 108f; width = 216f; height = 20f
            style = CanvasStyle(gradient = CanvasGradient("#E5484D", "#FFB224", horizontal = true), alpha = 0.6f)
        }

        ComposeHost().frame(FrameInput(248, 144)) { SceneCanvas(world) }
            .primitives.assertMatchesBaseline("scene-canvas-styles", 248, 144)
    }

    private companion object {
        val TINTS = listOf("#E5484D", "#3E63DD", "#30A46C")

        /** 12 x 12: a dark frame with a light bevel, 4-pixel corners, its edges dashed. */
        val FRAME = image(12, 12) { x, y ->
            val edge = minOf(x, y, 11 - x, 11 - y)
            when {
                edge == 0 -> 0x1A1410
                edge == 1 -> if ((x + y) % 4 < 2) 0xC8A060 else 0x8A6A3A
                edge < 4 -> 0x3A2E24
                else -> 0x221C18
            }
        }

        /** 8 x 4, white so a tint colours it: rounded caps, a highlight along its top. */
        val GAUGE = image(8, 4) { x, y ->
            val cap = x == 0 || x == 7
            when {
                cap && (y == 0 || y == 3) -> -1
                y == 0 -> 0xFFFFFF
                else -> 0xB0B0B0
            }
        }

        /** [width] x [height] from 0xRRGGBB per pixel; -1 is transparent. */
        fun image(width: Int, height: Int, rgb: (Int, Int) -> Int) = ImageBitmap(
            width,
            height,
            ByteArray(width * height * 4) { i ->
                val pixel = rgb(i / 4 % width, i / 4 / width)
                when {
                    pixel == -1 -> 0
                    i % 4 == 3 -> -1
                    else -> (pixel shr (8 * (2 - i % 4))).toByte()
                }
            },
        )
    }
}
