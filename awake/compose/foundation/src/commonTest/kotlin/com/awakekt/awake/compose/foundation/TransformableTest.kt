/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.gestures.TransformableState
import com.awakekt.awake.compose.foundation.gestures.transformable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransformableTest {

    @Test
    fun singlePointerPanInvokesTransform() {
        var totalPanX = 0f
        var totalPanY = 0f
        var lastZoom = 1f

        val state = TransformableState { panX, panY, zoom, _ ->
            totalPanX += panX
            totalPanY += panY
            lastZoom = zoom
        }

        val host = ComposeHost(density = 1f)
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.size(100.dp, 100.dp).transformable(state))
        }

        // Initial layout pass
        host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)

        // Press pointer at (50, 50)
        host.frame(
            FrameInput(
                viewportWidth = 100,
                viewportHeight = 100,
                pointerX = 50,
                pointerY = 50,
                pointerDown = true,
                pointerPressed = true,
            ),
            content,
        )

        // Move pointer by (+10, +20) to (60, 70)
        host.frame(
            FrameInput(
                viewportWidth = 100,
                viewportHeight = 100,
                pointerX = 60,
                pointerY = 70,
                pointerDown = true,
            ),
            content,
        )

        assertEquals(10f, totalPanX)
        assertEquals(20f, totalPanY)
        assertEquals(1f, lastZoom)
    }

    @Test
    fun multiPointerPinchZoomCalculatesScale() {
        var totalZoom = 1f

        val state = TransformableState { _, _, zoom, _ ->
            totalZoom *= zoom
        }

        val host = ComposeHost(density = 1f)
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.size(200.dp, 200.dp).transformable(state))
        }

        // Initial layout pass
        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 200), content)

        // Press pointer 1 at (50, 100) and pointer 2 at (150, 100) -> initial distance = 100
        val p1Press = PointerFrame(pointerId = 1L, x = 50, y = 100, down = true, pressed = true)
        val p2Press = PointerFrame(pointerId = 2L, x = 150, y = 100, down = true, pressed = true)
        host.frame(
            FrameInput(viewportWidth = 200, viewportHeight = 200, pointers = listOf(p1Press, p2Press)),
            content,
        )

        // Move pointer 1 to (30, 100) and pointer 2 to (170, 100) -> new distance = 140 -> zoom = 1.4
        val p1Move = PointerFrame(pointerId = 1L, x = 30, y = 100, down = true)
        val p2Move = PointerFrame(pointerId = 2L, x = 170, y = 100, down = true)
        host.frame(
            FrameInput(viewportWidth = 200, viewportHeight = 200, pointers = listOf(p1Move, p2Move)),
            content,
        )

        assertTrue(totalZoom > 1.3f && totalZoom < 1.5f)
    }
}
