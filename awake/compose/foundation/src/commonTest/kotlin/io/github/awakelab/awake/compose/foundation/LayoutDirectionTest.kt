/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.absoluteOffset
import io.github.awakelab.awake.compose.foundation.layout.offset
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.platform.ComposeHost
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.compose.ui.unit.LayoutDirection
import io.github.awakelab.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutDirectionTest {

    @Test
    fun rowPositionsChildrenFromRightInRtl() {
        val host = ComposeHost(density = 1f, layoutDirection = LayoutDirection.Rtl)
        val content: context(Composer) () -> Unit = {
            Row(Modifier.size(200.dp, 50.dp)) {
                Spacer(Modifier.size(40.dp, 50.dp))
                Spacer(Modifier.size(60.dp, 50.dp))
            }
        }

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 50), content)

        val rowNode = host.root.children[0]
        val firstChild = rowNode.children[0]
        val secondChild = rowNode.children[1]

        // First child (40px) is at 200 - 40 = 160
        assertEquals(160, firstChild.absoluteX)
        // Second child (60px) is at 160 - 60 = 100
        assertEquals(100, secondChild.absoluteX)
    }

    @Test
    fun boxAlignmentMirrorsStartAndEndInRtl() {
        val host = ComposeHost(density = 1f, layoutDirection = LayoutDirection.Rtl)
        val content: context(Composer) () -> Unit = {
            Box(Modifier.size(200.dp, 100.dp), contentAlignment = Alignment.TopStart) {
                Spacer(Modifier.size(50.dp, 40.dp))
            }
        }

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 100), content)
        val boxNode = host.root.children[0]
        val child = boxNode.children[0]

        // TopStart in RTL aligns to the right: 200 - 50 = 150
        assertEquals(150, child.absoluteX)
    }

    @Test
    fun logicalOffsetMirrorsInRtlWhileAbsoluteOffsetDoesNot() {
        val hostLtr = ComposeHost(density = 2f, layoutDirection = LayoutDirection.Ltr)
        val contentLtr: context(Composer) () -> Unit = {
            Box(Modifier.size(100.dp, 100.dp)) {
                Spacer(Modifier.size(30.dp, 30.dp).offset(x = 10.dp, y = 5.dp))
                Spacer(Modifier.size(30.dp, 30.dp).absoluteOffset(x = 10.dp, y = 5.dp))
            }
        }
        hostLtr.frame(FrameInput(viewportWidth = 200, viewportHeight = 200), contentLtr)

        val boxLtr = hostLtr.root.children[0]
        // Density 2: 10dp = 20px, 5dp = 10px
        assertEquals(20, boxLtr.children[0].contentAbsoluteX)
        assertEquals(10, boxLtr.children[0].contentAbsoluteY)
        assertEquals(20, boxLtr.children[1].contentAbsoluteX)
        assertEquals(10, boxLtr.children[1].contentAbsoluteY)

        val hostRtl = ComposeHost(density = 2f, layoutDirection = LayoutDirection.Rtl)
        val contentRtl: context(Composer) () -> Unit = {
            Box(Modifier.size(100.dp, 100.dp)) {
                Spacer(Modifier.size(30.dp, 30.dp).offset(x = 10.dp, y = 5.dp))
                Spacer(Modifier.size(30.dp, 30.dp).absoluteOffset(x = 10.dp, y = 5.dp))
            }
        }
        hostRtl.frame(FrameInput(viewportWidth = 200, viewportHeight = 200), contentRtl)

        val boxRtl = hostRtl.root.children[0]
        // In RTL with Alignment.TopStart (which aligns to right = 200 - 60 = 140):
        // offset(x=10.dp = 20px) shifts left: 140 - 20 = 120
        // absoluteOffset(x=10.dp = 20px) shifts right: 140 + 20 = 160
        assertEquals(120, boxRtl.children[0].contentAbsoluteX)
        assertEquals(10, boxRtl.children[0].contentAbsoluteY)
        assertEquals(160, boxRtl.children[1].contentAbsoluteX)
        assertEquals(10, boxRtl.children[1].contentAbsoluteY)
    }
}
