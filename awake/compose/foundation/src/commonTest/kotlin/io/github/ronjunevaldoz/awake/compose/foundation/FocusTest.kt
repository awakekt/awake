// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.interaction.InteractionSource
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Column
import io.github.ronjunevaldoz.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Spacer
import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.focus.FocusDirection
import io.github.ronjunevaldoz.awake.compose.ui.focus.FocusOwner
import io.github.ronjunevaldoz.awake.compose.ui.focus.FocusRequester
import io.github.ronjunevaldoz.awake.compose.ui.focus.focusRequester
import io.github.ronjunevaldoz.awake.compose.ui.focus.onFocusChanged
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEvent
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventType
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerInputDispatcher
import io.github.ronjunevaldoz.awake.compose.ui.layout.Layer
import io.github.ronjunevaldoz.awake.compose.ui.layout.LayerKind
import io.github.ronjunevaldoz.awake.compose.ui.layout.composeInto
import io.github.ronjunevaldoz.awake.compose.ui.layout.layoutTree
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun laidOut(content: context(Composer) () -> Unit): LayoutNode {
    val root = LayoutNode(ColumnMeasurePolicy())
    composeInto(root, content)
    root.layoutTree(Constraints.of(0, 300, 0, 300))
    return root
}

class FocusOwnerTest {

    @Test
    fun requestingFocusReportsItToTheNode() {
        val source = InteractionSource()
        val root = laidOut { Spacer(Modifier.size(20.dp).focusable(interactionSource = source)) }
        val owner = FocusOwner()

        owner.requestFocus(root, root.children[0])

        assertTrue(source.isFocused)
        assertSame(root.children[0], owner.focused)
    }

    @Test
    fun focusMovesRatherThanAccumulates() {
        // Two nodes reading as focused at once is the bug a single string id could not prevent:
        // whoever wrote `focusedId` last won, and the loser never heard about it.
        val first = InteractionSource()
        val second = InteractionSource()
        val root = laidOut {
            Spacer(Modifier.size(20.dp).focusable(interactionSource = first))
            Spacer(Modifier.size(20.dp).focusable(interactionSource = second))
        }
        val owner = FocusOwner()

        owner.requestFocus(root, root.children[0])
        owner.requestFocus(root, root.children[1])

        assertFalse(first.isFocused, "the previous holder was never told it lost focus")
        assertTrue(second.isFocused)
    }

    @Test
    fun aNodeWithNoFocusableLinkRefuses() {
        val root = laidOut { Spacer(Modifier.size(20.dp)) }
        val owner = FocusOwner()

        assertFalse(owner.requestFocus(root, root.children[0]))
        assertNull(owner.focused)
    }

    @Test
    fun disabledIsOutOfTheRing() {
        val source = InteractionSource()
        val root = laidOut {
            Spacer(Modifier.size(20.dp).focusable(enabled = false, interactionSource = source))
        }
        val owner = FocusOwner()

        assertFalse(owner.requestFocus(root, root.children[0]))
        assertFalse(owner.moveFocus(root, FocusDirection.Next))
        assertFalse(source.isFocused)
    }

    @Test
    fun tabWalksThePlacedOrder() {
        val root = laidOut {
            repeat(3) { Spacer(Modifier.size(20.dp).focusable()) }
        }
        val owner = FocusOwner()

        owner.moveFocus(root, FocusDirection.Next)
        assertSame(root.children[0], owner.focused, "entered somewhere other than the first")
        owner.moveFocus(root, FocusDirection.Next)
        assertSame(root.children[1], owner.focused)
        owner.moveFocus(root, FocusDirection.Next)
        assertSame(root.children[2], owner.focused)
    }

    @Test
    fun tabWrapsAtTheEnd() {
        // A Tab that silently does nothing at the last field reads as a broken keyboard.
        val root = laidOut { repeat(2) { Spacer(Modifier.size(20.dp).focusable()) } }
        val owner = FocusOwner()

        owner.moveFocus(root, FocusDirection.Next)
        owner.moveFocus(root, FocusDirection.Next)
        owner.moveFocus(root, FocusDirection.Next)

        assertSame(root.children[0], owner.focused)
    }

    @Test
    fun shiftTabEntersAtTheLast() {
        val root = laidOut { repeat(3) { Spacer(Modifier.size(20.dp).focusable()) } }
        val owner = FocusOwner()

        owner.moveFocus(root, FocusDirection.Previous)

        assertSame(root.children[2], owner.focused)
    }

    @Test
    fun theRingIsDepthFirstNotSiblingsFirst() {
        // Reading order: a nested field comes before the row after it, not after everything.
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Column(Modifier.size(80.dp)) {
                Spacer(Modifier.size(10.dp).focusable())
            }
            Spacer(Modifier.size(10.dp).focusable())
        }
        root.layoutTree(Constraints.of(0, 300, 0, 300))
        val owner = FocusOwner()

        owner.moveFocus(root, FocusDirection.Next)
        val nested = root.children[0].children[0]
        assertSame(nested, owner.focused)

        owner.moveFocus(root, FocusDirection.Next)
        assertSame(root.children[1], owner.focused)
    }

    @Test
    fun focusDoesNotSurviveTheNodeLeavingTheTree() {
        // Same class as a stranded pointer capture: keys would keep arriving at something no longer
        // laid out, and isFocused would answer for it.
        val source = InteractionSource()
        val root = LayoutNode(ColumnMeasurePolicy())
        var present = true
        val content: context(Composer)
        () -> Unit = {
            if (present) Spacer(Modifier.size(20.dp).focusable(interactionSource = source))
        }
        composeInto(root, content)
        root.layoutTree(Constraints.of(0, 300, 0, 300))
        val owner = FocusOwner()
        owner.requestFocus(root, root.children[0])

        present = false
        composeInto(root, content)
        owner.revalidate(root)

        assertNull(owner.focused)
        assertFalse(source.isFocused)
    }
}

class FocusFromPointerTest {

    @Test
    fun aPressFocusesWhatItLandsOn() {
        val source = InteractionSource()
        val root = laidOut {
            Spacer(Modifier.size(40.dp).focusable(interactionSource = source).clickable {})
        }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)

        assertTrue(source.isFocused)
    }

    @Test
    fun aPressOnNothingFocusableClearsFocus() {
        // Clicking away from a text field is how a caret is dismissed. Focus that only ever moved
        // forward would leave the field editable from the keyboard after the user visibly left it.
        val source = InteractionSource()
        val root = laidOut {
            Spacer(Modifier.size(20.dp).focusable(interactionSource = source))
            Spacer(Modifier.size(40.dp))
        }
        val dispatcher = PointerInputDispatcher()
        dispatcher.focusOwner.requestFocus(root, root.children[0])

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 5, 30)

        assertFalse(source.isFocused)
        assertNull(dispatcher.focusOwner.focused)
    }

    @Test
    fun theInnermostFocusableWins() {
        val outer = InteractionSource()
        val inner = InteractionSource()
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Column(Modifier.size(80.dp).focusable(interactionSource = outer)) {
                Spacer(Modifier.size(40.dp).focusable(interactionSource = inner))
            }
        }
        root.layoutTree(Constraints.of(0, 300, 0, 300))

        PointerInputDispatcher().dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)

        assertTrue(inner.isFocused)
        assertFalse(outer.isFocused, "the press stopped at the container")
    }

    @Test
    fun aPressFallsThroughToAFocusableAncestor() {
        val outer = InteractionSource()
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Column(Modifier.size(80.dp).focusable(interactionSource = outer)) {
                Spacer(Modifier.size(40.dp))
            }
        }
        root.layoutTree(Constraints.of(0, 300, 0, 300))

        PointerInputDispatcher().dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)

        assertTrue(outer.isFocused)
    }

    @Test
    fun clickAndTabAgreeOnWhoIsFocused() {
        // One owner, shared: two sources of truth would let a Tab move focus while the click-side
        // still believed the old node had it.
        val root = laidOut { repeat(3) { Spacer(Modifier.size(20.dp).focusable()) } }
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 5, 25)
        val clicked = dispatcher.focusOwner.focused
        dispatcher.focusOwner.moveFocus(root, FocusDirection.Next)

        assertSame(root.children[1], clicked)
        assertSame(root.children[2], dispatcher.focusOwner.focused)
        assertEquals(3, root.children.size)
    }
}

class FocusObservationTest {

    @Test
    fun onFocusChangedFiresBothWays() {
        val seen = mutableListOf<Boolean>()
        val root = laidOut {
            Spacer(Modifier.size(20.dp).focusable().onFocusChanged { seen += it })
            Spacer(Modifier.size(20.dp).focusable())
        }
        val owner = FocusOwner()

        owner.requestFocus(root, root.children[0])
        owner.requestFocus(root, root.children[1])

        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun observingFocusDoesNotJoinTheTabRing() {
        // Otherwise every container that wants to know about focus becomes a Tab stop.
        val seen = mutableListOf<Boolean>()
        val root = laidOut { Spacer(Modifier.size(20.dp).onFocusChanged { seen += it }) }
        val owner = FocusOwner()

        assertFalse(owner.requestFocus(root, root.children[0]))
        assertFalse(owner.moveFocus(root, FocusDirection.Next))
        assertEquals(emptyList(), seen)
    }

    @Test
    fun aRequesterFocusesANodeItsHolderNeverSees() {
        // The dialog case: opening one focuses its first field, and the opening code has no
        // reference to that field's node.
        val requester = FocusRequester()
        val source = InteractionSource()
        val root = laidOut {
            Spacer(Modifier.size(20.dp))
            Spacer(
                Modifier.size(20.dp)
                    .focusable(interactionSource = source)
                    .focusRequester(requester),
            )
        }
        val owner = FocusOwner()

        assertTrue(requester.requestFocus(owner, root))

        assertTrue(source.isFocused)
        assertSame(root.children[1], owner.focused)
    }

    @Test
    fun anUnattachedRequesterRefusesRatherThanThrowing() {
        // A requester for a node that this pass did not declare is normal, not exceptional -- a
        // dialog's field does not exist until the dialog opens.
        assertFalse(FocusRequester().requestFocus(FocusOwner(), laidOut { }))
    }
}

class ModalFocusTest {

    private fun treeWithDialog(dialogModal: Boolean): LayoutNode {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Spacer(Modifier.size(20.dp).focusable())
            Spacer(Modifier.size(20.dp).focusable())
            Layer(LayerKind.Dialog, modal = dialogModal, measurePolicy = ColumnMeasurePolicy()) {
                Spacer(Modifier.size(20.dp).focusable())
                Spacer(Modifier.size(20.dp).focusable())
            }
        }
        root.layoutTree(Constraints.of(0, 300, 0, 300))
        return root
    }

    private fun dialogOf(root: LayoutNode) = root.layers[0]

    @Test
    fun tabCyclesInsideTheDialogRatherThanWalkingOut() {
        val root = treeWithDialog(dialogModal = true)
        val dialog = dialogOf(root)
        val owner = FocusOwner()

        owner.moveFocus(root, FocusDirection.Next)
        assertSame(dialog.children[0], owner.focused, "entered the page instead of the dialog")
        owner.moveFocus(root, FocusDirection.Next)
        assertSame(dialog.children[1], owner.focused)
        owner.moveFocus(root, FocusDirection.Next)
        assertSame(dialog.children[0], owner.focused, "Tab escaped the dialog")
    }

    @Test
    fun shiftTabIsTrappedToo() {
        val root = treeWithDialog(dialogModal = true)
        val owner = FocusOwner()

        owner.moveFocus(root, FocusDirection.Previous)

        assertSame(dialogOf(root).children[1], owner.focused)
    }

    @Test
    fun aNonModalLayerExtendsTheRingInstead() {
        // The popup case: a menu that does not trap still has to be reachable by Tab.
        val root = treeWithDialog(dialogModal = false)
        val owner = FocusOwner()

        owner.moveFocus(root, FocusDirection.Next)

        assertSame(root.children[0], owner.focused)
    }

    @Test
    fun aClickBehindTheDialogCannotStealFocus() {
        // The hole a Tab-only trap leaves: the mouse would walk straight through it.
        val root = treeWithDialog(dialogModal = true)
        val owner = FocusOwner()

        assertFalse(owner.requestFocus(root, root.children[0]))
        assertNull(owner.focused)
    }

    @Test
    fun aRequesterBehindTheDialogIsRefused() {
        val root = treeWithDialog(dialogModal = true)
        val owner = FocusOwner()

        assertTrue(owner.requestFocus(root, dialogOf(root).children[0]))
        assertFalse(owner.requestFocus(root, root.children[1]), "focus escaped through a requester")
    }

    @Test
    fun openingADialogDropsFocusHeldBehindIt() {
        val plain = laidOut { Spacer(Modifier.size(20.dp).focusable()) }
        val owner = FocusOwner()
        owner.requestFocus(plain, plain.children[0])
        assertTrue(owner.focused != null)

        val withDialog = treeWithDialog(dialogModal = true)
        owner.revalidate(withDialog)

        assertNull(owner.focused, "the field behind the dialog kept focus")
    }

    @Test
    fun theTopmostDialogWins() {
        // A dialog opened over a dialog traps focus rather than handing it back to the one below.
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Layer(LayerKind.Dialog, modal = true, measurePolicy = ColumnMeasurePolicy()) {
                Spacer(Modifier.size(20.dp).focusable())
            }
            Layer(LayerKind.Dialog, modal = true, measurePolicy = ColumnMeasurePolicy()) {
                Spacer(Modifier.size(20.dp).focusable())
            }
        }
        root.layoutTree(Constraints.of(0, 300, 0, 300))
        val owner = FocusOwner()

        owner.moveFocus(root, FocusDirection.Next)

        assertSame(root.layers[1].children[0], owner.focused)
    }
}

/**
 * The layer branch of every tree walk.
 *
 * `isInTree`, `isAttachedTo` and `buildPathTo` each recurse into children *and* layers, and only the
 * children half was ever reached. An overlay is exactly where a stranded capture or a stale focus
 * would show up, because a popup is the thing that opens and closes.
 */
class LayerWalkTest {

    private fun treeWithPopup(): LayoutNode {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Spacer(Modifier.size(20.dp))
            Layer(LayerKind.Popup, measurePolicy = ColumnMeasurePolicy()) {
                Spacer(Modifier.size(30.dp).focusable().clickable {})
            }
        }
        root.layoutTree(Constraints.of(0, 300, 0, 300))
        return root
    }

    @Test
    fun focusInsideALayerSurvivesRevalidation() {
        val root = treeWithPopup()
        val inPopup = root.layers[0].children[0]
        val owner = FocusOwner()
        owner.requestFocus(root, inPopup)

        owner.revalidate(root)

        assertSame(inPopup, owner.focused, "a node inside a layer was treated as detached")
    }

    @Test
    fun aCaptureInsideALayerKeepsReceivingTheDrag() {
        // The path to a captured node has to be rebuilt through the layer slot, not just children.
        var clicks = 0
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Spacer(Modifier.size(20.dp))
            Layer(LayerKind.Popup, measurePolicy = ColumnMeasurePolicy()) {
                Spacer(Modifier.size(30.dp).clickable { clicks++ })
            }
        }
        root.layoutTree(Constraints.of(0, 300, 0, 300))
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 5, 5)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), 5, 5)

        assertEquals(1, clicks)
    }

    @Test
    fun focusDroppedWhenTheLayerCloses() {
        val root = LayoutNode(ColumnMeasurePolicy())
        var open = true
        val content: context(Composer)
        () -> Unit = {
            Spacer(Modifier.size(20.dp))
            if (open) {
                Layer(LayerKind.Popup, measurePolicy = ColumnMeasurePolicy()) {
                    Spacer(Modifier.size(30.dp).focusable())
                }
            }
        }
        composeInto(root, content)
        root.layoutTree(Constraints.of(0, 300, 0, 300))
        val owner = FocusOwner()
        owner.requestFocus(root, root.layers[0].children[0])

        open = false
        composeInto(root, content)
        owner.revalidate(root)

        assertNull(owner.focused, "focus survived the popup closing")
    }
}
