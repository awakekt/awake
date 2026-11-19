/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.platform.blocksGameplayKey
import com.awakekt.awake.compose.ui.platform.blocksGameplayKeys
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultitouchOwnershipTest {
    @Test
    fun reportsCapturedTouchIdsSeparatelyFromUncapturedTouchesAndLeavesKeysFree() {
        val host = ComposeHost(density = 1f)
        val content: context(Composer)
        () -> Unit = {
            Box(modifier = Modifier.size(100.dp, 100.dp).clickable { })
        }
        // First frame places the tree before hit-testing.
        host.frame(FrameInput(viewportWidth = 400, viewportHeight = 300), content)

        val held = host.frame(
            FrameInput(
                viewportWidth = 400,
                viewportHeight = 300,
                pointers = listOf(
                    PointerFrame(pointerId = 1L, x = 20, y = 20, down = true, pressed = true),
                    PointerFrame(pointerId = 2L, x = 250, y = 150, down = true, pressed = true),
                ),
            ),
            content,
        )

        assertTrue(held.ownership.isCaptured)
        assertEquals(setOf(1L), held.ownership.capturedPointerIds)
        assertFalse(held.ownership.blocksGameplayKeys)
        assertFalse(held.ownership.blocksGameplayKey(Key.W))
    }
}
