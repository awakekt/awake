/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.material3

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ScaffoldTest {

    @Test
    fun scaffoldMeasuresBarsAndSuppliesTheirInsetsToContent() {
        val host = ComposeHost()
        var receivedPadding: ScaffoldPaddingValues? = null
        val content: context(Composer)
        () -> Unit = {
            Scaffold(
                topBar = { Spacer(Modifier.size(200.dp, 20.dp)) },
                bottomBar = { Spacer(Modifier.size(200.dp, 30.dp)) },
                floatingActionButton = { Spacer(Modifier.size(20.dp)) },
            ) { padding ->
                receivedPadding = padding
                Spacer(Modifier.size(200.dp, 150.dp))
            }
        }

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 150), content)

        assertEquals(ScaffoldPaddingValues(top = 20.dp, bottom = 30.dp), receivedPadding)
        val scaffold = host.root.children[0]
        assertEquals(200, scaffold.width)
        assertEquals(150, scaffold.height)

        val children = scaffold.children.asList()
        assertEquals(0, children[0].absoluteY, "top bar")
        assertEquals(120, children[1].absoluteY, "bottom bar")
        assertEquals(0, children[2].absoluteY, "content intentionally draws behind bars")
        assertEquals(164, children[3].absoluteX, "FAB is inset 16dp from the end")
        assertEquals(84, children[3].absoluteY, "FAB sits above the bottom bar with a 16dp inset")
    }
}
