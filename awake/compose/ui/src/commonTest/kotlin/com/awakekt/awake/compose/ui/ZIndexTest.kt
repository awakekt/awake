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
import com.awakekt.awake.compose.ui.layout.Layer
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.layout.LayerPosition
import com.awakekt.awake.compose.ui.layout.LayerPositionProvider
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.onPlaced
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

    @Test
    fun nestedZIndexContainersSortStablyWithoutClobberingOuterOrder() {
        val green = Color(0f, 1f, 0f, 1f)
        val yellow = Color(1f, 1f, 0f, 1f)
        val cyan = Color(0f, 1f, 1f, 1f)
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            Layout(nodeType = "root", measurePolicy = stackPolicy) {
                Layout(
                    nodeType = "outer_high",
                    modifier = Modifier.zIndex(2f).drawBehind { drawRect(color = red) },
                    measurePolicy = stackPolicy,
                ) {
                    Layout(
                        nodeType = "inner_high",
                        modifier = Modifier.zIndex(3f).drawBehind { drawRect(color = yellow) },
                        measurePolicy = stackPolicy,
                    )
                    Layout(
                        nodeType = "inner_low_a",
                        modifier = Modifier.zIndex(1f).drawBehind { drawRect(color = green) },
                        measurePolicy = stackPolicy,
                    )
                    Layout(
                        nodeType = "inner_low_b",
                        modifier = Modifier.zIndex(1f).drawBehind { drawRect(color = cyan) },
                        measurePolicy = stackPolicy,
                    )
                }
                Layout(
                    nodeType = "outer_low",
                    modifier = Modifier.zIndex(1f).drawBehind { drawRect(color = blue) },
                    measurePolicy = stackPolicy,
                )
            }
        }

        val output = host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)
        val colors = output.primitives.filterIsInstance<UiDrawPrimitive.Quad>().map { it.color }
        assertEquals(listOf(blue, red, green, cyan, yellow), colors)
    }

    @Test
    fun layoutTreeInvokesOnPlacedOncePerFrameForBaseAndOverlayLayers() {
        var baseCalls = 0
        var layerCalls = 0
        var layerPos = -1 to -1
        val anchorPolicy = MeasurePolicy { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            layout(100, 100) {
                placeables.forEach { it.placeAt(12, 18) }
            }
        }
        val fixedProvider = LayerPositionProvider { parentX, parentY, _, _, _, _ ->
            LayerPosition(5, 7).also {
                assertEquals(12, parentX)
                assertEquals(18, parentY)
            }
        }
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            Layout(nodeType = "root", measurePolicy = anchorPolicy) {
                Layout(
                    nodeType = "anchor",
                    modifier = Modifier.onPlaced { _, _, _, _ -> baseCalls++ },
                    measurePolicy = stackPolicy,
                ) {
                    Layer(kind = LayerKind.Popup, measurePolicy = stackPolicy, positionProvider = fixedProvider) {
                        Layout(
                            nodeType = "popup_body",
                            modifier = Modifier.onPlaced { x, y, _, _ ->
                                layerCalls++
                                layerPos = x to y
                            },
                            measurePolicy = stackPolicy,
                        )
                    }
                }
            }
        }

        host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)
        assertEquals(1, baseCalls, "base node onPlaced must fire once per frame, not twice")
        assertEquals(1, layerCalls, "overlay layer onPlaced must fire once per frame")
        assertEquals(17 to 25, layerPos, "layer absolute position resolves against its placed anchor")
    }

    @Test
    fun nestedOverlayLayersResolveCoordinatesOnFirstFrameAndFireOnPlacedOnce() {
        var anchorCalls = 0
        var outerChildCalls = 0
        var innerLeafCalls = 0
        var innerLeafPos = -1 to -1
        var innerProviderSawParent = -1 to -1
        val offsetPolicy = { dx: Int, dy: Int ->
            MeasurePolicy { measurables, constraints ->
                val placeables = measurables.map { it.measure(constraints) }
                layout(100, 100) { placeables.forEach { it.placeAt(dx, dy) } }
            }
        }
        val outerProvider = LayerPositionProvider { parentX, parentY, _, _, _, _ ->
            assertEquals(12, parentX)
            assertEquals(18, parentY)
            LayerPosition(5, 7)
        }
        val innerProvider = LayerPositionProvider { parentX, parentY, _, _, _, _ ->
            innerProviderSawParent = parentX to parentY
            LayerPosition(10, 20)
        }
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            Layout(nodeType = "root", measurePolicy = offsetPolicy(12, 18)) {
                Layout(
                    nodeType = "anchor",
                    modifier = Modifier.onPlaced { _, _, _, _ -> anchorCalls++ },
                    measurePolicy = stackPolicy,
                ) {
                    Layer(kind = LayerKind.Dialog, measurePolicy = offsetPolicy(3, 4), positionProvider = outerProvider) {
                        Layout(
                            nodeType = "dialog_child",
                            modifier = Modifier.onPlaced { _, _, _, _ -> outerChildCalls++ },
                            measurePolicy = stackPolicy,
                        ) {
                            Layer(kind = LayerKind.Tooltip, measurePolicy = stackPolicy, positionProvider = innerProvider) {
                                Layout(
                                    nodeType = "tooltip_leaf",
                                    modifier = Modifier.onPlaced { x, y, _, _ ->
                                        innerLeafCalls++
                                        innerLeafPos = x to y
                                    },
                                    measurePolicy = stackPolicy,
                                )
                            }
                        }
                    }
                }
            }
        }

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 200), content)
        assertEquals(1, anchorCalls)
        assertEquals(1, outerChildCalls)
        assertEquals(1, innerLeafCalls)
        // Anchor (12, 18) + outer layer (5, 7) = (17, 25); dialog_child (+3, +4) = (20, 29); inner layer (+10, +20) = (30, 49).
        assertEquals(20 to 29, innerProviderSawParent, "nested layer must see outer layer child's fresh frame-1 coordinates")
        assertEquals(30 to 49, innerLeafPos, "nested layer leaf resolves against its placed outer layer child")
    }

    @Test
    fun deeplyNestedZIndexContainersGrowingScratchStackPreserveOrder() {
        // 20 outer + 25 inner = 45 > INITIAL_Z_ORDER_CAPACITY (32), forcing stack resize during recursion.
        val outerCount = 20
        val innerCount = 25
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            Layout(nodeType = "root", measurePolicy = stackPolicy) {
                for (i in 0 until outerCount) {
                    val outerZ = (outerCount - i).toFloat()
                    val outerColor = Color(outerZ / 100f, 0f, 0f, 1f)
                    Layout(
                        nodeType = "outer_$i",
                        modifier = Modifier.zIndex(outerZ).drawBehind { drawRect(color = outerColor) },
                        measurePolicy = stackPolicy,
                    ) {
                        if (i == outerCount - 1) {
                            // Lowest outer zIndex (1f) paints first and grows zIndexOrderStack mid-recursion.
                            for (j in 0 until innerCount) {
                                val innerZ = (innerCount - j).toFloat()
                                val innerColor = Color(0f, innerZ / 100f, 0f, 1f)
                                Layout(
                                    nodeType = "inner_$j",
                                    modifier = Modifier.zIndex(innerZ).drawBehind { drawRect(color = innerColor) },
                                    measurePolicy = stackPolicy,
                                )
                            }
                        }
                    }
                }
            }
        }

        val output = host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)
        val quads = output.primitives.filterIsInstance<UiDrawPrimitive.Quad>()
        assertEquals(outerCount + innerCount, quads.size)
        // First: outer z=1 (red=0.01), then 25 inner sorted z=1..25 (green=0.01..0.25), then outer z=2..20 (red=0.02..0.20).
        assertEquals(0.01f, quads[0].color.r, 1e-5f)
        for (k in 1..innerCount) {
            assertEquals(k / 100f, quads[k].color.g, 1e-5f, "inner child $k out of order after stack growth")
        }
        for (k in 2..outerCount) {
            assertEquals(k / 100f, quads[innerCount + k - 1].color.r, 1e-5f, "outer child $k clobbered after stack growth")
        }
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
