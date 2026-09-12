/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneColorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesFromObject() {
        val color = json.decodeFromString<SceneColor>("""{"r": 0.5, "g": 0.6, "b": 0.7, "a": 0.8}""")
        assertEquals(0.5f, color.r, 0.001f)
        assertEquals(0.6f, color.g, 0.001f)
        assertEquals(0.7f, color.b, 0.001f)
        assertEquals(0.8f, color.a, 0.001f)
    }

    @Test
    fun decodesFromObjectWithDefaultAlpha() {
        val color = json.decodeFromString<SceneColor>("""{"r": 0.2, "g": 0.3, "b": 0.4}""")
        assertEquals(0.2f, color.r, 0.001f)
        assertEquals(0.3f, color.g, 0.001f)
        assertEquals(0.4f, color.b, 0.001f)
        assertEquals(1.0f, color.a, 0.001f)
    }

    @Test
    fun decodesFromHexString() {
        val color = json.decodeFromString<SceneColor>(""""#FF8000"""")
        assertEquals(1.0f, color.r, 0.005f)
        assertEquals(0.502f, color.g, 0.005f)
        assertEquals(0.0f, color.b, 0.005f)
        assertEquals(1.0f, color.a, 0.001f)
    }

    @Test
    fun decodesFromHexWithAlpha() {
        val color = json.decodeFromString<SceneColor>(""""#FF800080"""")
        assertEquals(1.0f, color.r, 0.005f)
        assertEquals(0.502f, color.g, 0.005f)
        assertEquals(0.0f, color.b, 0.005f)
        assertEquals(0.502f, color.a, 0.005f)
    }

    @Test
    fun decodesFromArray() {
        val color = json.decodeFromString<SceneColor>("""[0.1, 0.2, 0.3, 0.9]""")
        assertEquals(0.1f, color.r, 0.001f)
        assertEquals(0.2f, color.g, 0.001f)
        assertEquals(0.3f, color.b, 0.001f)
        assertEquals(0.9f, color.a, 0.001f)
    }

    @Test
    fun convertsToHex() {
        val color = SceneColor(1f, 0.5f, 0f, 1f)
        assertEquals("#FF7F00", color.toHex())
    }
}
