/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.interaction.Interaction
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.sheet.SliceInsets
import com.awakekt.awake.compose.foundation.skin.SheetRect
import com.awakekt.awake.compose.foundation.skin.SkinnedKeys
import com.awakekt.awake.compose.foundation.skin.skin
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a skinned component does on every draw allocates nothing in steady state: the part is cut
 * once per sheet and answered from a cache, and the state frame is a map read. The draw lambdas
 * of SkinnedButton, SkinnedFrame and SkinnedBar do exactly these calls and a draw call.
 */
class SkinnedAllocationTest {
    private val art = skin(ImageBitmap(24, 4, ByteArray(24 * 4 * 4) { -1 })) {
        states(SkinnedKeys.ButtonFace, SheetRect(0, 0, 16, 4))
        frame(SkinnedKeys.Frame, SheetRect(16, 0, 4, 4), SliceInsets(1))
        bar(SkinnedKeys.BarFill, SheetRect(20, 0, 4, 4), SliceInsets(1))
    }

    @Test
    fun resolvingPartsAndStateFramesAllocatesNothingAfterTheFirstUse() {
        val source = InteractionSource()
        val enter = Interaction.Hover.Enter
        val exit = Interaction.Hover.Exit(enter)
        var checksum = 0
        fun draw() {
            val face = art.resolve(SkinnedKeys.ButtonFace)
            checksum += face!!.fill(source, enabled = true, selected = false).srcX
            checksum += art.resolve(SkinnedKeys.Frame)!!.srcX
            checksum += art.resolve(SkinnedKeys.BarFill)!!.srcX
            checksum += art.sliceInsets(SkinnedKeys.Frame)!!.left
        }
        repeat(WARMUP) {
            source.tryEmit(enter)
            draw()
            source.tryEmit(exit)
            draw()
        }
        val before = allocatedBytes()
        repeat(COUNT) {
            source.tryEmit(enter)
            draw()
            source.tryEmit(exit)
            draw()
        }
        val bytes = allocatedBytes() - before
        assertNotNull(art.resolve(SkinnedKeys.ButtonFace))
        assertTrue(checksum != 0)
        assertTrue(bytes <= SLACK_BYTES, "$COUNT draws allocated $bytes B")
        println("skinned draw path: ${bytes / COUNT} B per two draws")
        assertEquals(1, art.keys.count { it === SkinnedKeys.Frame })
    }

    private fun allocatedBytes(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val COUNT = 20_000
        const val WARMUP = 5_000
        const val SLACK_BYTES = 2_000
    }
}
