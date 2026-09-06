/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.BoxWithConstraints
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class BoxWithConstraintsTest {

    @Test
    fun boxWithConstraintsExposesMeasuredConstraints() {
        val host = ComposeHost()
        var reportedMaxWidth = 0.dp
        var reportedMaxHeight = 0.dp

        val content: context(Composer)
        () -> Unit = {
            BoxWithConstraints {
                reportedMaxWidth = maxWidth
                reportedMaxHeight = maxHeight
                Spacer(Modifier.size(maxWidth / 2, maxHeight / 2))
            }
        }

        host.frame(FrameInput(viewportWidth = 300, viewportHeight = 200), content)

        assertEquals(300.dp, reportedMaxWidth)
        assertEquals(200.dp, reportedMaxHeight)

        val boxNode = host.root.children[0]
        assertEquals(150, boxNode.width)
        assertEquals(100, boxNode.height)
    }

    @Test
    fun boxWithConstraintsBranchesDynamically() {
        val host = ComposeHost()

        val content: context(Composer)
        () -> Unit = {
            BoxWithConstraints {
                if (maxWidth >= 200.dp) {
                    Spacer(Modifier.size(180.dp, 50.dp))
                } else {
                    Spacer(Modifier.size(80.dp, 30.dp))
                }
            }
        }

        host.frame(FrameInput(viewportWidth = 300, viewportHeight = 100), content)
        val wideChild = host.root.children[0].children[0]
        assertEquals(180, wideChild.width)
        assertEquals(50, wideChild.height)

        host.frame(FrameInput(viewportWidth = 120, viewportHeight = 100), content)
        val narrowChild = host.root.children[0].children[0]
        assertEquals(80, narrowChild.width)
        assertEquals(30, narrowChild.height)
    }

    @Test
    fun boxWithConstraintsPositionsWithAlignment() {
        val host = ComposeHost()

        val content: context(Composer)
        () -> Unit = {
            BoxWithConstraints(
                modifier = Modifier.size(200.dp, 100.dp),
                contentAlignment = Alignment.Center,
            ) {
                Spacer(Modifier.size(60.dp, 40.dp))
            }
        }

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 100), content)

        val boxNode = host.root.children[0]
        val spacerNode = boxNode.children[0]

        assertEquals(200, boxNode.width)
        assertEquals(100, boxNode.height)
        assertEquals(70, spacerNode.absoluteX, "(200 - 60) / 2")
        assertEquals(30, spacerNode.absoluteY, "(100 - 40) / 2")
    }
}
