/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.IntOffset
import com.awakekt.awake.compose.ui.unit.dp

/**
 * [count] boxes placed at fixed offsets read while placing, with no canvas: the plain `compose:ui`
 * and `foundation` placement a follower HUD's elements also go through (#1512). A skipped frame of it
 * separates what core placement allocates per node from what the canvas's follower placement does.
 */
internal class PlainOffsetBoxes(private val count: Int) {
    private val offsets = IntArray(count) { (it % COLUMNS) * SPACING }

    fun frame(host: ComposeHost) {
        host.frame(INPUT) {
            Box(Modifier.size(1280.dp, 720.dp)) {
                for (index in 0 until count) {
                    Box(Modifier.offset { IntOffset(offsets[index], (index / COLUMNS) * SPACING) }.size(70.dp, 20.dp))
                }
            }
        }
    }

    private companion object {
        const val COLUMNS = 20
        const val SPACING = 60
        val INPUT = FrameInput(1280, 720)
    }
}
