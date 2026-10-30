/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldtext

import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.worldtext.WorldTextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A `world_text` becomes the live text it describes, and saves back unchanged. */
class WorldTextBindingTest {
    private val sign = SceneWorldText(
        text = "Harbor Town\nMarket",
        size = 0.5f,
        color = SceneColor(1f, 0.8f, 0.2f, 0.9f),
        weight = 700,
        align = WorldTextAlign.Start,
        lineSpacing = 1.2f,
        billboard = true,
    )

    @Test
    fun everyFieldReachesTheLiveText() {
        val text = sign.toWorldText()

        assertEquals("Harbor Town\nMarket", text.text)
        assertEquals(0.5f, text.style.size)
        assertEquals(FontWeight(700), text.style.weight)
        assertEquals(listOf(1f, 0.8f, 0.2f, 0.9f), text.style.color.let { listOf(it.r, it.g, it.b, it.a) })
        assertEquals(WorldTextAlign.Start, text.style.align)
        assertEquals(1.2f, text.style.lineSpacing)
        assertTrue(text.billboard)
    }

    @Test
    fun itSavesBackUnchanged() {
        assertEquals(sign, sign.toWorldText().toScene())
    }

    @Test
    fun badValuesAreReported() {
        val issues = SceneWorldText(size = 0f, weight = 50, lineSpacing = -1f).validate("nodes[0]")

        assertEquals(3, issues.size, issues.toString())
    }
}
