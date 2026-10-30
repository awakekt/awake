/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontNumerals
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneCanvasNumeralsTest {
    @Test
    fun numeralsRoundTripThroughSceneJsonAndSpaceDigitsOnTheCanvas() {
        val json = SceneSerializers.createJson()
        val defaultEncoded = json.encodeToString(SceneCanvasText.serializer(), SceneCanvasText(text = "123"))
        assertFalse("numerals" in defaultEncoded, "default null numerals adds no JSON field: $defaultEncoded")

        val prefab = canvasPrefab(guid = "00000000-0000-4000-8000-000000001023", name = "Counter") {
            text("Score", id = "score") {
                text = "999"
                numerals = FontNumerals.Tabular
            }
        }
        val prefabJson = CanvasPrefabs.toJson(prefab)
        assertTrue(""""numerals": "Tabular"""" in prefabJson, prefabJson)
        val reloaded = CanvasPrefabs.fromJson(prefabJson)
        assertEquals(prefab, reloaded)
        assertEquals(FontNumerals.Tabular, reloaded.findCanvasNode("score")?.canvasElementView()?.numerals)

        val world = World()
        world.add(
            world.create(),
            CanvasElement().apply {
                offsetX = 0f
                offsetY = 0f
                width = 200f
                height = 40f
                text = "111"
                fontSize = 18f
                numerals = FontNumerals.Tabular
            },
        )
        world.add(
            world.create(),
            CanvasElement().apply {
                offsetX = 0f
                offsetY = 50f
                width = 200f
                height = 40f
                text = "111"
                fontSize = 18f
                numerals = FontNumerals.Proportional
            },
        )
        val glyphs = ComposeHost()
            .frame(FrameInput(800, 600)) { SceneCanvas(world) }
            .primitives
            .filterIsInstance<UiDrawPrimitive.Glyph>()
        val tabRight = glyphs.filter { it.y < 45f }.maxOf { it.x + it.w }
        val propRight = glyphs.filter { it.y >= 45f }.maxOf { it.x + it.w }
        assertTrue(tabRight > propRight, "tabular '111' ($tabRight) spans wider than proportional ($propRight)")
    }
}
