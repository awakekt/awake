/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Steady-state glyph, effect-tile, advance, kerning, and pixel-glyph lookups on [UiFonts.default]
 * (`WeightedUiFont` over `PackedUiFont`) and [UiFonts.fallback] (`FallbackUiFont`) must allocate
 * nothing per character (#1742, #1512).
 */
class FontLookupAllocationTest {
    private var sink: Any? = null
    private var floatSink: Float = 0f

    @Test
    fun defaultWeightedFontLookupsAllocateNothing() {
        val font = UiFonts.default()
        val sample = "Player 12345 — Ü"
        val weights = arrayOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight(450))
        val pixels = font.pixelGlyphs

        // First lookup populates the per-face cache; repeated lookups return the same instance.
        val firstGlyph = assertNotNull(font.glyphFor('A', FontWeight.Normal))
        assertSame(firstGlyph, font.glyphFor('A', FontWeight.Normal))
        val firstEffect = font.effectGlyphFor('A'.code, FontWeight.Bold)
        assertSame(firstEffect, font.effectGlyphFor('A'.code, FontWeight.Bold))

        fun lookupAll() {
            for (weight in weights) {
                var prev = -1
                for (i in 0 until sample.length) {
                    val code = sample[i].code
                    sink = font.glyphFor(code, weight)
                    sink = font.effectGlyphFor(code, weight)
                    floatSink += font.advanceFor(code, 16f, weight)
                    if (prev >= 0) floatSink += font.kerningFor(prev, code, 16f, weight)
                    if (pixels != null) {
                        sink = pixels.glyph(code, weight, 14, i and 3)
                    }
                    prev = code
                }
            }
        }

        // Warm up caches and pixel-glyph blocks before measuring steady-state lookups.
        repeat(WARMUP) { lookupAll() }
        val reading = readAllocation(unitsPerWindow = ITERATIONS) {
            repeat(ITERATIONS) { lookupAll() }
        }
        assertAllocatesNothing(reading, "WeightedUiFont steady glyph lookups")
    }

    @Test
    fun fallbackFontLookupsAllocateNothing() = runTest {
        val primary = UiFonts.default()
        val fallback = UiFonts.load(readResourceBytes("Roboto-Regular.ttf"), "Roboto-Regular.ttf", chars = "Привет")
        val chain = UiFonts.fallback(primary, listOf(fallback))
        val sample = "Player Привет 42"
        val weights = arrayOf(FontWeight.Normal, FontWeight.Bold, FontWeight(450))

        assertSame(chain.glyphFor('A', FontWeight.Normal), chain.glyphFor('A', FontWeight.Normal))
        assertSame(chain.glyphFor('П', FontWeight.Bold), chain.glyphFor('П', FontWeight.Bold))
        assertSame(chain.effectGlyphFor('A'.code, FontWeight.Normal), chain.effectGlyphFor('A'.code, FontWeight.Normal))

        fun lookupAll() {
            for (weight in weights) {
                for (i in 0 until sample.length) {
                    val code = sample[i].code
                    sink = chain.fontFor(code, weight)
                    sink = chain.glyphFor(code, weight)
                    sink = chain.effectGlyphFor(code, weight)
                    floatSink += chain.advanceFor(code, 16f, weight)
                }
            }
        }

        repeat(WARMUP) { lookupAll() }
        val reading = readAllocation(unitsPerWindow = ITERATIONS) {
            repeat(ITERATIONS) { lookupAll() }
        }
        assertAllocatesNothing(reading, "FallbackUiFont steady glyph lookups")
    }

    private companion object {
        const val WARMUP = 200
        const val ITERATIONS = 500
    }
}
