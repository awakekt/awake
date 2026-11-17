/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp

/**
 * [count] children under a parent that places each at the origin itself, with no `Box` policy or
 * alignment (#1512). With [leaves], each child is a bare layout node with a fixed size and no
 * modifiers; otherwise a `Box` with only a size. Read against [PlainOffsetBoxes], a skipped frame of
 * each separates `Box`'s placement from what core `LayoutNode` placement costs per node.
 */
internal class PlainLayouts(private val count: Int, private val leaves: Boolean) {
    fun frame(host: ComposeHost) {
        host.frame(INPUT) {
            Layout(PlainParent, measurePolicy = parent) {
                for (index in 0 until count) {
                    if (leaves) Layout(PlainLeaf, measurePolicy = leaf) else Box(Modifier.size(70.dp, 20.dp))
                }
            }
        }
    }

    private object PlainParent

    private object PlainLeaf

    private companion object {
        val INPUT = FrameInput(1280, 720)

        val parent = MeasurePolicy { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                for (i in placeables.indices) placeables[i].placeAbsoluteAt(0, 0)
            }
        }

        val leaf = MeasurePolicy { _, _ -> layout(70, 20) {} }
    }
}
