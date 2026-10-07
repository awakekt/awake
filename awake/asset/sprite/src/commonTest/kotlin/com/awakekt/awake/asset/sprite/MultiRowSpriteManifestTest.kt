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

class MultiRowSpriteManifestTest {
    @Test
    fun clipStartingPartwayAlongARowWrapsAcrossTwoMoreRowsAndPlaysInOrder() {
        val sheet = SpriteGenManifest.decode(manifest())
        assertEquals(FrameClip(2, 8, 10f, true), sheet.clips.getValue("walk"))
        val player = FrameClipPlayer(sheet.clips)
        for (expected in 2..9) {
            assertEquals(expected, player.frame)
            player.advance(0.101f)
        }
        assertEquals(2, player.frame)
    }

    @Test
    fun wrappedNonLoopingClipStopsAtItsLastCellAndUniformDurationsStillWin() {
        val text = manifest().replace("\"fps\":10", "\"fps\":99,\"durations_ms\":[100,100,100,100,100,100,100,100]")
            .replace("\"loop\":true", "\"loop\":false")
        val sheet = SpriteGenManifest.decode(text)
        assertEquals(10f, sheet.clips.getValue("walk").framesPerSecond)
        val player = FrameClipPlayer(sheet.clips)
        player.advance(1f)
        assertEquals(9, player.frame)
        assertTrue(player.isFinished)
    }

    @Test
    fun wrappingCannotRelaxDeclaredStartGridBoundsOrPlaybackOrder() {
        val valid = manifest()
        val cases = listOf(
            valid.replace("\"row\":0", "\"row\":1"),
            valid.replace("\"x\":48,\"y\":0", "\"x\":47,\"y\":0"),
            valid.replace("\"x\":0,\"y\":16", "\"x\":16,\"y\":16"),
            valid.replace("\"x\":0,\"y\":16", "\"x\":48,\"y\":0"),
            valid.replace("\"x\":0,\"y\":16", "\"x\":0,\"y\":32"),
            valid.replace("\"x\":0,\"y\":32", "\"x\":0,\"y\":48"),
            valid.replace("\"w\":16", "\"w\":8"),
            valid.replace("\"h\":16", "\"h\":8"),
        )
        cases.forEach { text ->
            val error = assertFailsWith<IllegalArgumentException> { SpriteGenManifest.decode(text) }
            assertTrue("walk" in error.message.orEmpty())
        }
    }

    private fun manifest(): String {
        val frames = (2..9).joinToString(",") { cell ->
            """{"x":${cell % 4 * 16},"y":${cell / 4 * 16},"w":16,"h":16}"""
        }
        return """{
            "game_input":"walk.png",
            "frame_layout":{"sheetWidth":64,"sheetHeight":48,"cellWidth":16,"cellHeight":16,"rows":{"walk":[$frames]}},
            "animation":{"cellWidth":16,"cellHeight":16,"columns":4,"rows":{"walk":{"row":0,"frames":8,"fps":10,"loop":true}}}
        }"""
    }
}
