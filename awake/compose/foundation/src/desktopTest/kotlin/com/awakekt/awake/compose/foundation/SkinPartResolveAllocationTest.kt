/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.interaction.Interaction
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.sheet.SliceInsets
import com.awakekt.awake.compose.foundation.skin.BarKey
import com.awakekt.awake.compose.foundation.skin.FrameKey
import com.awakekt.awake.compose.foundation.skin.SheetRect
import com.awakekt.awake.compose.foundation.skin.StatesKey
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
 * of the shadcn button, card and progress bar do exactly these calls and a draw call.
 */
class SkinPartResolveAllocationTest {
    private val face = StatesKey("face")
    private val panel = FrameKey("panel")
    private val gauge = BarKey("gauge")

    private val art = skin(ImageBitmap(24, 4, ByteArray(24 * 4 * 4) { -1 })) {
        states(face, SheetRect(0, 0, 16, 4))
        frame(panel, SheetRect(16, 0, 4, 4), SliceInsets(1))
        bar(gauge, SheetRect(20, 0, 4, 4), SliceInsets(1))
    }

    @Test
    fun resolvingPartsAndStateFramesAllocatesNothingAfterTheFirstUse() {
        val source = InteractionSource()
        val enter = Interaction.Hover.Enter
        val exit = Interaction.Hover.Exit(enter)
        var checksum = 0
        fun draw() {
            val face = art.resolve(face)
            checksum += face!!.fill(source, enabled = true, selected = false).srcX
            checksum += art.resolve(panel)!!.srcX
            checksum += art.resolve(gauge)!!.srcX
            checksum += art.sliceInsets(panel)!!.left
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
        assertNotNull(art.resolve(face))
        assertTrue(checksum != 0)
        assertTrue(bytes <= SLACK_BYTES, "$COUNT draws allocated $bytes B")
        println("skinned draw path: ${bytes / COUNT} B per two draws")
        assertEquals(1, art.keys.count { it === panel })
    }

    private fun allocatedBytes(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val COUNT = 20_000
        const val WARMUP = 5_000
        const val SLACK_BYTES = 2_000
    }
}
