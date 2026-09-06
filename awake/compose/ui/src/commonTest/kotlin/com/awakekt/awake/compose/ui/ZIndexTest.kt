/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.draw.zIndex
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ZIndexTest {

    private val red = Color(1f, 0f, 0f, 1f)
    private val blue = Color(0f, 0f, 1f, 1f)

    private val stackPolicy = MeasurePolicy { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(100, 100) {
            placeables.forEach { it.placeAt(0, 0) }
        }
    }

    @Test
    fun higherZIndexPaintsAfterLowerZIndex() {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            Layout(
                nodeType = "root",
                measurePolicy = stackPolicy,
                content = {
                    // Declared first with higher zIndex (2f)
                    Layout(
                        nodeType = "first_high",
                        modifier = Modifier.zIndex(2f).drawBehind { drawRect(color = red) },
                        measurePolicy = stackPolicy,
                    )
                    // Declared second with lower zIndex (1f)
                    Layout(
                        nodeType = "second_low",
                        modifier = Modifier.zIndex(1f).drawBehind { drawRect(color = blue) },
                        measurePolicy = stackPolicy,
                    )
                },
            )
        }

        val output = host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)
        val rects = output.primitives.filterIsInstance<UiDrawPrimitive.Quad>()
        assertEquals(2, rects.size)
        // Blue (zIndex 1f) paints first, Red (zIndex 2f) paints second on top
        assertEquals(blue, rects[0].color)
        assertEquals(red, rects[1].color)
    }

    @Test
    fun higherZIndexReceivesPointerClickFirst() {
        var clicked = ""
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            Layout(
                nodeType = "root",
                measurePolicy = stackPolicy,
                content = {
                    // Declared first with zIndex = 5f
                    Layout(
                        nodeType = "first_high",
                        modifier = Modifier.zIndex(5f).zIndexPointerInput { event, pass ->
                            if (event.type == PointerEventType.Press) {
                                clicked = "first_high"
                                event.consume()
                            }
                        },
                        measurePolicy = stackPolicy,
                    )
                    // Declared second with zIndex = 0f
                    Layout(
                        nodeType = "second_low",
                        modifier = Modifier.zIndex(0f).zIndexPointerInput { event, pass ->
                            if (event.type == PointerEventType.Press) {
                                clicked = "second_low"
                                event.consume()
                            }
                        },
                        measurePolicy = stackPolicy,
                    )
                },
            )
        }

        // Initial layout frame
        host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)

        // Press at (50, 50)
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

        assertEquals("first_high", clicked)
    }
}

private class ZIndexPointerInputNode :
    Modifier.Node(),
    PointerInputNode {
    lateinit var onEvent: (PointerEvent, PointerEventPass) -> Unit

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) = onEvent(event, pass)
}

private class ZIndexPointerInputElement(
    private val onEvent: (PointerEvent, PointerEventPass) -> Unit,
) : ModifierNodeElement<ZIndexPointerInputNode>() {
    override fun create(): ZIndexPointerInputNode = ZIndexPointerInputNode()
    override fun update(node: ZIndexPointerInputNode) {
        node.onEvent = onEvent
    }
}

private fun Modifier.zIndexPointerInput(
    onEvent: (PointerEvent, PointerEventPass) -> Unit,
): Modifier = this then ZIndexPointerInputElement(onEvent)
