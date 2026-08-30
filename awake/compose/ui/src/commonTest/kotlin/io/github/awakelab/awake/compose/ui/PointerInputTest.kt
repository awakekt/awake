/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.combinedClickable
import io.github.awakelab.awake.compose.foundation.gestures.draggable
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventPass
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventType
import io.github.awakelab.awake.compose.ui.input.pointer.PointerInputDispatcher
import io.github.awakelab.awake.compose.ui.layout.Layer
import io.github.awakelab.awake.compose.ui.layout.LayerKind
import io.github.awakelab.awake.compose.ui.layout.LayerPosition
import io.github.awakelab.awake.compose.ui.layout.LayerPositionProvider
import io.github.awakelab.awake.compose.ui.layout.Layout
import io.github.awakelab.awake.compose.ui.layout.MeasurePolicy
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.node.PointerInputNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object HitType

private val stackPolicy = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val w = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
    val h = placeables.maxOfOrNull { it.height } ?: constraints.minHeight
    layout(constraints.constrainWidth(w), constraints.constrainHeight(h)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

context(_: Composer)
private fun hit(
    modifier: Modifier,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) =
    Layout(HitType, modifier = modifier, measurePolicy = stackPolicy, content = content)

private fun tree(content: context(Composer) () -> Unit): LayoutNode {
    val root = LayoutNode(stackPolicy)
    composeInto(root, content)
    root.layoutTree(Constraints.of(0, 200, 0, 200))
    return root
}

private class TestPointerInputElement(
    private val onEvent: (PointerEvent, PointerEventPass) -> Unit,
) : ModifierNodeElement<TestPointerInputNode>() {
    override fun create(): TestPointerInputNode = TestPointerInputNode()

    override fun update(node: TestPointerInputNode) {
        node.onEvent = onEvent
    }
}

private class TestPointerInputNode : Modifier.Node(), PointerInputNode {
    lateinit var onEvent: (PointerEvent, PointerEventPass) -> Unit

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) = onEvent(event, pass)
}

private fun Modifier.testPointerInput(
    onEvent: (PointerEvent, PointerEventPass) -> Unit,
): Modifier = this then TestPointerInputElement(onEvent)

private fun Modifier.record(log: MutableList<String>, name: String): Modifier = testPointerInput { event, pass ->
    // Type included because hover arrives as ordinary Enter/Exit events, as in Compose -- a log
    // of passes alone cannot tell the gesture apart from the pointer showing up.
    log += "$name:${event.type}:$pass"
}

class PointerInputTest {

    @Test
    fun aHeldCombinedClickDispatchesLongPressInsteadOfClick() {
        var clicks = 0
        var longClicks = 0
        val root = tree { hit(Modifier.size(50.dp).combinedClickable({ clicks++ }, { longClicks++ })) }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 10, 10)
        dispatcher.advanceTime(root, 10, 10, 0.5f)
        dispatcher.advanceTime(root, 10, 10, 1f)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 10, 10)

        assertEquals(0, clicks)
        assertEquals(1, longClicks)
    }

    @Test
    fun combinedClickableWithoutALongClickRemainsAnOrdinaryClick() {
        var clicks = 0
        val root = tree { hit(Modifier.size(50.dp).combinedClickable(onClick = { clicks++ })) }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 10, 10)
        dispatcher.advanceTime(root, 10, 10, 0.5f)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 10, 10)

        assertEquals(1, clicks)
    }

    @Test
    fun aClickInsideTheNodeFires() {
        var clicks = 0
        val root = tree { hit(Modifier.size(50.dp).clickable { clicks++ }) }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 10, 10)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 10, 10)

        assertEquals(1, clicks)
    }

    @Test
    fun aClickOutsideTheNodeDoesNot() {
        var clicks = 0
        val root = tree { hit(Modifier.size(20.dp).clickable { clicks++ }) }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 150, 150)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 150, 150)

        assertEquals(0, clicks)
    }

    @Test
    fun coordinatesArriveNodeLocal() {
        // A handler that had to subtract its own origin would break the moment it moved.
        var seenX = -1
        var seenY = -1
        val root = tree {
            hit(Modifier.padding(10.dp)) {
                hit(Modifier.size(30.dp).testPointerInput { event, pass ->
                    if (pass == PointerEventPass.Main) {
                        seenX = event.x
                        seenY = event.y
                    }
                })
            }
        }
        PointerInputDispatcher().dispatch(root, PointerEvent(PointerEventType.Press), 25, 25)

        assertEquals(15, seenX, "25 minus the node's own 10px origin")
        assertEquals(15, seenY)
    }

    @Test
    fun passesRunRootToLeafThenLeafToRootThenRootToLeaf() {
        val log = mutableListOf<String>()
        val root = tree {
            hit(Modifier.size(60.dp).record(log, "outer")) {
                hit(Modifier.size(40.dp).record(log, "inner"))
            }
        }
        PointerInputDispatcher().dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)

        assertEquals(
            listOf(
                // The pointer arriving is delivered first, then the gesture runs its three passes.
                "outer:Enter:Main",
                "inner:Enter:Main",
                "outer:Press:Initial",
                "inner:Press:Initial",
                "inner:Press:Main",
                "outer:Press:Main",
                "outer:Press:Final",
                "inner:Press:Final",
            ),
            log,
        )
    }

    @Test
    fun anAncestorCanTakeAGestureBeforeTheChildSeesIt() {
        // The reason passes exist. A single leaf-first walk cannot express this, and a scroll
        // container stealing a drag from a button inside it is exactly this shape.
        var childClicks = 0
        val root = tree {
            hit(Modifier.size(60.dp).testPointerInput { event, pass ->
                if (pass == PointerEventPass.Initial) event.consume()
            }) {
                hit(Modifier.size(40.dp).clickable { childClicks++ })
            }
        }
        val dispatcher = PointerInputDispatcher()
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 5, 5)

        assertEquals(0, childClicks, "the ancestor claimed it on Initial")
    }

    @Test
    fun theTopmostSiblingIsHitFirst() {
        // Reverse of paint order: whatever draws last is on top.
        val log = mutableListOf<String>()
        val root = tree {
            hit(Modifier.size(50.dp).record(log, "under"))
            hit(Modifier.size(50.dp).record(log, "over"))
        }
        PointerInputDispatcher().dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)

        assertTrue(log.none { it.startsWith("under") }, "the covered sibling never saw it")
        assertTrue(log.any { it.startsWith("over") })
    }

    @Test
    fun aLayerIsHitBeforeTheContentBehindIt() {
        val log = mutableListOf<String>()
        val root = tree {
            hit(Modifier.size(50.dp).record(log, "content"))
            Layer(
                LayerKind.Dialog,
                modifier = Modifier.size(50.dp).record(log, "dialog"),
                measurePolicy = stackPolicy,
            )
        }
        PointerInputDispatcher().dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)

        assertTrue(log.any { it.startsWith("dialog") })
        assertTrue(log.none { it.startsWith("content") }, "the dialog is in front")
    }

    @Test
    fun aDeepLayerIsHitBeforeALaterBaseSibling() {
        var popupClicks = 0
        var siblingClicks = 0
        val root = tree {
            hit(Modifier.size(20.dp)) {
                Layer(
                    LayerKind.Popup,
                    modifier = Modifier.size(50.dp).clickable { popupClicks++ },
                    positionProvider = LayerPositionProvider { _, _, _, _, _, _ -> LayerPosition(0, 50) },
                    measurePolicy = stackPolicy,
                )
            }
            hit(Modifier.size(200.dp).clickable { siblingClicks++ })
        }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 5, 55)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 5, 55)

        assertEquals(1, popupClicks)
        assertEquals(0, siblingClicks, "the later sibling took the popup's click")
    }

    @Test
    fun aDragKeepsArrivingAfterThePointerLeavesTheNode() {
        // Capture. Without it a slider stops tracking the instant the pointer slips off the thumb.
        val deltas = mutableListOf<Int>()
        val root = tree { hit(Modifier.size(20.dp).draggable { dx, _ -> deltas += dx }) }
        val dispatcher = PointerInputDispatcher()

        // The delta is supplied by the caller, because only the frame loop knows where the pointer
        // was last frame -- a modifier cannot hold that, which is what `draggable` got wrong.
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 15, 5, dx = 10, dy = 0)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 500, 5, dx = 485, dy = 0)

        assertEquals(listOf(10, 485), deltas, "still delivered at x=500, far outside 20px")
    }

    @Test
    fun captureEndsOnRelease() {
        val deltas = mutableListOf<Int>()
        val root = tree { hit(Modifier.size(20.dp).draggable { dx, _ -> deltas += dx }) }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 5, 5)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 500, 5)

        assertEquals(emptyList(), deltas, "the move after release went nowhere")
    }

    @Test
    fun concurrentPointersKeepIndependentCaptures() {
        val capturedMoves = mutableListOf<Long>()
        val root = tree {
            hit(Modifier.size(20.dp).testPointerInput { event, pass ->
                if (pass != PointerEventPass.Main) return@testPointerInput
                when (event.type) {
                    PointerEventType.Press -> event.consume()
                    PointerEventType.Move -> if (event.isCaptureHolder) capturedMoves += event.pointerId
                    else -> Unit
                }
            })
        }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press, pointerId = 1), 5, 5)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press, pointerId = 2), 6, 6)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move, pointerId = 1), 200, 5)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move, pointerId = 2), 200, 6)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release, pointerId = 1), 200, 5)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move, pointerId = 2), 201, 6)

        assertEquals(listOf(1L, 2L, 2L), capturedMoves)
    }

    @Test
    fun captureEndsIfTheHoldingNodeLeavesTheTree() {
        // A reconcile that removes the node mid-drag would otherwise strand the pointer, and every
        // later event would go to something no longer laid out.
        var present = true
        val root = LayoutNode(stackPolicy)
        val build: context(Composer)
        () -> Unit = {
            if (present) hit(Modifier.size(20.dp).draggable { _, _ -> })
        }
        composeInto(root, build)
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val dispatcher = PointerInputDispatcher()
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)

        present = false
        composeInto(root, build)
        val consumed = dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 5, 5)

        assertEquals(false, consumed)
        assertEquals(0, root.children.size)
    }

    @Test
    fun anEventOverNothingIsNotConsumed() {
        val root = tree { }
        val consumed = PointerInputDispatcher()
            .dispatch(root, PointerEvent(PointerEventType.Press), 500, 500)

        assertEquals(false, consumed)
    }
}

/**
 * Gestures driven through whole frames, reconciling between each one.
 *
 * The suite above drives the dispatcher directly, so a modifier instance survives every event in a
 * test and none in a real app. `draggable` held its own `dragging` flag and produced no drag at all
 * in a live frame loop while passing every direct-dispatch test -- this class is where that class
 * of bug is visible.
 */
class GestureAcrossFramesTest {

    private fun host() = io.github.awakelab.awake.compose.ui.platform.ComposeHost()

    @Test
    fun aDragReportsDeltasAcrossFrames() {
        val host = host()
        val deltas = mutableListOf<Int>()
        fun frame(x: Int, down: Boolean) {
            host.frame(
                io.github.awakelab.awake.compose.ui.platform.FrameInput(
                    viewportWidth = 200,
                    viewportHeight = 200,
                    pointerX = x,
                    pointerY = 5,
                    pointerDown = down,
                ),
            ) {
                hit(Modifier.size(50.dp).draggable { dx, _ -> deltas += dx })
            }
        }

        frame(5, false)
        frame(5, true)
        frame(15, true)
        frame(25, true)

        assertEquals(listOf(10, 10), deltas, "the drag stopped once the chain was rebuilt")
    }

    @Test
    fun aClickSpanningTwoFramesFires() {
        // The bug `isCaptureHolder` was added for, pinned here too so the two live together.
        val host = host()
        var clicks = 0
        fun frame(down: Boolean) {
            host.frame(
                io.github.awakelab.awake.compose.ui.platform.FrameInput(
                    viewportWidth = 200,
                    viewportHeight = 200,
                    pointerX = 5,
                    pointerY = 5,
                    pointerDown = down,
                ),
            ) {
                hit(Modifier.size(50.dp).clickable { clicks++ })
            }
        }

        frame(false)
        frame(true)
        frame(false)

        assertEquals(1, clicks)
    }

    @Test
    fun hostDeliversConcurrentTouchContacts() {
        val host = host()
        val seen = mutableListOf<Long>()
        fun frame(vararg touches: io.github.awakelab.awake.compose.ui.platform.PointerFrame) {
            host.frame(
                io.github.awakelab.awake.compose.ui.platform.FrameInput(
                    viewportWidth = 200,
                    viewportHeight = 200,
                    pointers = touches.toList(),
                ),
            ) {
                hit(Modifier.size(50.dp).testPointerInput { event, pass ->
                    if (pass == PointerEventPass.Main && event.type == PointerEventType.Move && event.isCaptureHolder) {
                        seen += event.pointerId
                    }
                    if (pass == PointerEventPass.Main && event.type == PointerEventType.Press) event.consume()
                })
            }
        }

        frame()
        frame(
            io.github.awakelab.awake.compose.ui.platform.PointerFrame(1, 5, 5, down = true),
            io.github.awakelab.awake.compose.ui.platform.PointerFrame(2, 6, 6, down = true),
        )
        frame(
            io.github.awakelab.awake.compose.ui.platform.PointerFrame(1, 15, 5, down = true),
            io.github.awakelab.awake.compose.ui.platform.PointerFrame(2, 16, 6, down = true),
        )

        assertEquals(listOf(1L, 2L), seen)
    }
}
