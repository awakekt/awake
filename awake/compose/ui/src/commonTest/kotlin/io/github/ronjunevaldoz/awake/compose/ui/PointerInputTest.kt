// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui

import io.github.ronjunevaldoz.awake.compose.foundation.clickable
import io.github.ronjunevaldoz.awake.compose.foundation.gestures.draggable
import io.github.ronjunevaldoz.awake.compose.foundation.layout.padding
import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEvent
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventPass
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventType
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerInputDispatcher
import io.github.ronjunevaldoz.awake.compose.ui.layout.Layer
import io.github.ronjunevaldoz.awake.compose.ui.layout.LayerKind
import io.github.ronjunevaldoz.awake.compose.ui.layout.Layout
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.layout.composeInto
import io.github.ronjunevaldoz.awake.compose.ui.layout.layoutTree
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.node.PointerInputNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
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

/** Records which pass reached it, so ordering is observable rather than inferred. */
private class Recorder(private val log: MutableList<String>, private val name: String) : PointerInputNode {
    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        // Type included because hover arrives as ordinary Enter/Exit events, as in Compose -- a log
        // of passes alone cannot tell the gesture apart from the pointer showing up.
        log += "$name:${event.type}:$pass"
    }
}

private fun Modifier.record(log: MutableList<String>, name: String): Modifier =
    this then Recorder(log, name)

class PointerInputTest {

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
        val probe = object : PointerInputNode {
            override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
                if (pass == PointerEventPass.Main) {
                    seenX = event.x
                    seenY = event.y
                }
            }
        }
        val root = tree {
            hit(Modifier.padding(10.dp)) {
                hit(Modifier.size(30.dp) then probe)
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
        val stealer = object : PointerInputNode {
            override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
                if (pass == PointerEventPass.Initial) event.consume()
            }
        }
        val root = tree {
            hit(Modifier.size(60.dp) then stealer) {
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
    fun aDragKeepsArrivingAfterThePointerLeavesTheNode() {
        // Capture. Without it a slider stops tracking the instant the pointer slips off the thumb.
        val deltas = mutableListOf<Int>()
        val root = tree { hit(Modifier.size(20.dp).draggable { dx, _ -> deltas += dx }) }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 15, 5)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), 500, 5)

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
