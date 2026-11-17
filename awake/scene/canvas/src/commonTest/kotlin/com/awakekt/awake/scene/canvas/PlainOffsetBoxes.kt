/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.IntOffset
import com.awakekt.awake.compose.ui.unit.dp

/**
 * [count] boxes with no canvas: the plain `compose:ui` and `foundation` placement a follower HUD's
 * elements also go through (#1512). Each box carries [links], so a skipped frame of each kind names
 * what one layout link costs to place, per node.
 */
internal class PlainOffsetBoxes(private val count: Int, private val links: Links = Links.OffsetLambda) {
    /** The layout links in front of each box's size. */
    enum class Links { None, OffsetDp, OffsetLambda, Padding, ThreePaddings }

    private val offsets = IntArray(count) { (it % COLUMNS) * SPACING }

    fun frame(host: ComposeHost) {
        host.frame(INPUT) {
            Box(Modifier.size(1280.dp, 720.dp)) {
                for (index in 0 until count) {
                    Box(links(index).size(70.dp, 20.dp))
                }
            }
        }
    }

    private fun links(index: Int): Modifier = when (links) {
        Links.None -> Modifier
        Links.OffsetDp -> Modifier.offset(offsets[index].dp, ((index / COLUMNS) * SPACING).dp)
        Links.OffsetLambda -> Modifier.offset { IntOffset(offsets[index], (index / COLUMNS) * SPACING) }
        Links.Padding -> Modifier.padding(2.dp)
        Links.ThreePaddings -> Modifier.padding(2.dp).padding(2.dp).padding(2.dp)
    }

    private companion object {
        const val COLUMNS = 20
        const val SPACING = 60
        val INPUT = FrameInput(1280, 720)
    }
}
