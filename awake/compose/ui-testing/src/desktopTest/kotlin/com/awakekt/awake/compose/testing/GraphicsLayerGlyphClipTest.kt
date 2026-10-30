/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A `graphicsLayer` does not clip its node's drawing, as Compose's does not: its target grows to what
 * the content paints, so text whose ink passes its box keeps it once a fade or a press scale wraps it
 * in a layer (#1053).
 */
class GraphicsLayerGlyphClipTest {
    @Test
    fun aLayerAroundTextKeepsTheInkPastTheTextBox() {
        val failures = GlyphInk.cases().mapNotNull { case ->
            val text = "ÅHgjpqy|Wolf"
            val lost = lostToALayer(text, case)
            if (lost > 0) "$case '$text': the layer cut $lost pixels of ink" else null
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun aLayerWhoseContentFitsItsNodeKeepsATargetTheNodesSize() {
        val layers = GlyphInk.frame(1f, 200, 100) {
            Box(Modifier.padding(20.dp).graphicsLayer(alpha = 0.5f)) {
                Box(Modifier.padding(4.dp)) { Text("ab", style = GlyphInk.cases().first().style) }
            }
        }.graphicsLayers
        val layer = layers.single()
        assertEquals(0, layer.effectInsetX, "ink inside the node needs no room past it")
        assertEquals(0, layer.effectInsetY, "ink inside the node needs no room past it")
    }

    /** Pixels of ink wrapping [text] in a barely translucent layer takes away, against the same text unwrapped. */
    private fun lostToALayer(text: String, case: GlyphInk.Case): Int {
        val width = ((case.size * text.length + 80) * case.density).roundToInt()
        val height = ((case.size * 3 + 80) * case.density).roundToInt()
        val plain = GlyphInk.frame(case.density, width, height) {
            Box(Modifier.padding(40.dp)) { Box { Text(text, style = case.style) } }
        }
        val layered = GlyphInk.frame(case.density, width, height) {
            Box(Modifier.padding(40.dp)) { Box(Modifier.graphicsLayer(alpha = 0.999f)) { Text(text, style = case.style) } }
        }
        val expected = GlyphInk.paint(plain.primitives, width, height)
        assertTrue(GlyphInk.inkedPixels(expected) > 0, "'$text' drew nothing")
        return GlyphInk.lostPixels(expected, GlyphInk.paint(layered.primitives, width, height, layered.graphicsLayers))
    }
}
