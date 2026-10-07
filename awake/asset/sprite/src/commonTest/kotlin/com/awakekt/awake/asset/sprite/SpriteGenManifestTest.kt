/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.sprite

import com.awakekt.awake.core.animation.FrameClip
import com.awakekt.awake.core.animation.FrameClipPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpriteGenManifestTest {
    @Test
    fun importsNamedRunsInOrderWithTopLeftIndicesAndTiming() {
        val sheet = SpriteGenManifest.decode(MANIFEST)
        assertEquals("sheet.png", sheet.image)
        assertEquals(4, sheet.columns)
        assertEquals(2, sheet.rows)
        assertEquals(listOf("idle", "flash"), sheet.clips.keys.toList())
        assertEquals(FrameClip(5, 2, 5f, true), sheet.clips["idle"])
        assertEquals(FrameClip(2, 1, 10f, false), sheet.clips["flash"])
        val player = FrameClipPlayer(sheet.clips)
        assertEquals(5, player.frame)
        player.advance(0.2f)
        assertEquals(6, player.frame)
        player.play("flash")
        player.advance(1f)
        assertTrue(player.isFinished)
        assertEquals(2, player.frame)
    }

    @Test
    fun uniformDurationsTakePrecedenceOverExporterFps() {
        val sheet = SpriteGenManifest.decode(MANIFEST.replace("\"fps\":5", "\"fps\":99"))
        assertEquals(5f, sheet.clips.getValue("idle").framesPerSecond)
        val withoutFps = SpriteGenManifest.decode(MANIFEST.replace("\"fps\":5,", ""))
        assertEquals(5f, withoutFps.clips.getValue("idle").framesPerSecond)
    }

    @Test
    fun absentDurationsUseFpsAndZeroFpsHoldsAFrame() {
        val text = MANIFEST.replace(",\"durations_ms\":[200,200]", "").replace("\"fps\":5", "\"fps\":0")
        val player = FrameClipPlayer(SpriteGenManifest.decode(text).clips)
        player.advance(100f)
        assertEquals(5, player.frame)
    }

    @Test
    fun rejectsInvalidDimensionsAndConflictingLayoutsBeforeIndexing() {
        val cases = listOf(
            MANIFEST.replace("\"sheetWidth\":64", "\"sheetWidth\":63"),
            MANIFEST.replace("\"cellWidth\":16", "\"cellWidth\":0"),
            MANIFEST.replace("\"sheetHeight\":32", "\"sheetHeight\":0"),
            MANIFEST.replace("\"columns\":4", "\"columns\":5"),
            MANIFEST.replace("\"columns\":4", "\"columns\":3"),
            MANIFEST.replace("\"sheetWidth\":64", "\"sheetWidth\":2147483647")
                .replace("\"sheetHeight\":32", "\"sheetHeight\":2147483647")
                .replace("\"cellWidth\":16", "\"cellWidth\":1").replace("\"cellHeight\":16", "\"cellHeight\":1"),
        )
        cases.forEach { assertFailsWith<IllegalArgumentException> { SpriteGenManifest.decode(it) } }
    }

    @Test
    fun rejectsTrimmedOffGridOutOfBoundsAndReorderedFrames() {
        val cases = listOf(
            MANIFEST.replace("\"x\":16", "\"x\":17"),
            MANIFEST.replace("\"x\":16", "\"x\":48"),
            MANIFEST.replace("\"x\":16", "\"x\":-16"),
            MANIFEST.replace("\"x\":16", "\"x\":64"),
            MANIFEST.replace("\"x\":32,\"y\":16", "\"x\":16,\"y\":16"),
            MANIFEST.replace("\"x\":32,\"y\":16", "\"x\":48,\"y\":16"),
            MANIFEST.replace("\"y\":16", "\"y\":0"),
            MANIFEST.replace("\"w\":16", "\"w\":8"),
            MANIFEST.replace("\"h\":16", "\"h\":8"),
        )
        cases.forEach { text ->
            val error = assertFailsWith<IllegalArgumentException> { SpriteGenManifest.decode(text) }
            assertTrue("idle" in error.message.orEmpty(), error.message)
        }
    }

    @Test
    fun rejectsMissingNamesCountsRowsAndUnsupportedTiming() {
        val cases = listOf(
            MANIFEST.replace("\"row\":1", "\"row\":2"),
            MANIFEST.replace("\"frames\":2", "\"frames\":0"),
            MANIFEST.replace("\"frames\":2", "\"frames\":3"),
            MANIFEST.replace("\"idle\":{", "\"missing\":{"),
            MANIFEST.replace("\"idle\"", "\" \""),
            MANIFEST.replace("\"fps\":5", "\"fps\":-1"),
            MANIFEST.replace("\"fps\":5,", "").replace(",\"durations_ms\":[200,200]", ""),
            MANIFEST.replace("[200,200]", "[200]"),
            MANIFEST.replace("[200,200]", "[]"),
            MANIFEST.replace("[200,200]", "[0,0]"),
            MANIFEST.replace("[200,200]", "[200,300]"),
        )
        cases.forEach { assertFailsWith<IllegalArgumentException> { SpriteGenManifest.decode(it) } }
    }

    @Test
    fun rejectsMalformedJsonMissingImageAndWrongImageDimensions() {
        assertFailsWith<IllegalArgumentException> { SpriteGenManifest.decode("{") }
        assertFailsWith<IllegalArgumentException> { SpriteGenManifest.decode(MANIFEST.replace("sheet.png", " ")) }
        assertFailsWith<IllegalArgumentException> { SpriteGenManifest.decode(MANIFEST.replace("\"game_input\":\"sheet.png\",", "")) }
        val sheet = SpriteGenManifest.decode(MANIFEST)
        sheet.requireImageSize(64, 32)
        val error = assertFailsWith<IllegalArgumentException> { sheet.requireImageSize(32, 64) }
        assertTrue("sheet.png" in error.message.orEmpty())
    }

    @Test
    fun portableMetadataCopiesItsRunsAndRejectsInvalidRanges() {
        val clips = mutableMapOf("idle" to FrameClip(0, 1, 5f, true))
        val sheet = SpriteSheet("sheet.png", 16, 16, 16, 16, clips)
        clips.clear()
        assertEquals(listOf("idle"), sheet.clips.keys.toList())
        assertFailsWith<IllegalArgumentException> { SpriteSheet("sheet.png", 16, 16, 16, 16, mapOf("bad" to FrameClip(1, 1, 5f, true))) }
    }

    private companion object {
        const val MANIFEST = """
{
  "game_input":"sheet.png", "characterId":"test-sprite", "future_field":{"ignored":true},
  "frame_layout": {"sheetWidth":64,"sheetHeight":32,"cellWidth":16,"cellHeight":16,"rows":{
    "idle":[{"x":16,"y":16,"w":16,"h":16},{"x":32,"y":16,"w":16,"h":16}],
    "flash":[{"x":32,"y":0,"w":16,"h":16}]
  }},
  "animation":{"cellWidth":16,"cellHeight":16,"columns":4,"rows":{
    "idle":{"row":1,"frames":2,"fps":5,"durations_ms":[200,200],"loop":true},
    "flash":{"row":0,"frames":1,"fps":10,"loop":false}
  }}
}
"""
    }
}
