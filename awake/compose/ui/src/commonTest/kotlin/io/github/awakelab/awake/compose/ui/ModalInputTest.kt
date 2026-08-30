/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventPass
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventType
import io.github.awakelab.awake.compose.ui.input.pointer.PointerInputDispatcher
import io.github.awakelab.awake.compose.ui.layout.Layer
import io.github.awakelab.awake.compose.ui.layout.LayerKind
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object ModalHitType

private val modalStackPolicy = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val w = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
    val h = placeables.maxOfOrNull { it.height } ?: constraints.minHeight
    layout(constraints.constrainWidth(w), constraints.constrainHeight(h)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

context(_: Composer)
private fun box(modifier: Modifier) =
    Layout(ModalHitType, modifier = modifier, measurePolicy = modalStackPolicy)

private fun scene(content: context(Composer) () -> Unit): LayoutNode {
    val root = LayoutNode(modalStackPolicy)
    composeInto(root, content)
    root.layoutTree(Constraints.of(0, 200, 0, 200))
    return root
}

/**
 * A page-filling button with a smaller dialog opened over its top-left corner.
 *
 * The dialog is 50x50 on a 200x200 page, so 150,150 is outside the dialog and still over the page --
 * which is the whole case: a backdrop click must reach nothing.
 */
private fun sceneWithDialog(
    modal: Boolean,
    onPage: () -> Unit,
    onDialog: () -> Unit,
): LayoutNode = scene {
    box(Modifier.size(200.dp).clickable { onPage() })
    Layer(
        LayerKind.Dialog,
        modal = modal,
        modifier = Modifier.size(50.dp).clickable { onDialog() },
        measurePolicy = modalStackPolicy,
    )
}

private fun PointerInputDispatcher.click(root: LayoutNode, x: Int, y: Int) {
    dispatch(root, PointerEvent(PointerEventType.Press), x, y)
    dispatch(root, PointerEvent(PointerEventType.Release), x, y)
}

/**
 * A modal owns the frame's pointer input, not merely its own bounds.
 *
 * `ui-core` needed a widget to call `registerOverlayOcclusion(bounds, isModal)` and was correct only
 * if it remembered to. Here it is derived from the tree, so a layer cannot forget.
 */
class ModalInputTest {

    @Test
    fun outsidePressDismissesTheTopmostDismissableLayerWithoutClickingThePage() {
        var page = 0
        var dismissals = 0
        val root = scene {
            box(Modifier.size(200.dp).clickable { page++ })
            Layer(
                LayerKind.Popup,
                modifier = Modifier.size(50.dp),
                dismissOnOutsideClick = true,
                onDismissRequest = { dismissals++ },
                measurePolicy = modalStackPolicy,
            )
        }

        val consumed = PointerInputDispatcher()
            .dispatch(root, PointerEvent(PointerEventType.Press), 150, 150)

        assertTrue(consumed, "the dismissing press must not fall through to the page")
        assertEquals(1, dismissals)
        assertEquals(0, page)
    }

    @Test
    fun aClickOutsideAModalReachesNothing() {
        var page = 0
        var dialog = 0
        val root = sceneWithDialog(modal = true, onPage = { page++ }, onDialog = { dialog++ })

        PointerInputDispatcher().click(root, 150, 150)

        assertEquals(0, page, "the click fell through to the page behind the dialog")
        assertEquals(0, dialog)
    }

    @Test
    fun aClickInsideAModalStillReachesIt() {
        // The other half: blocking everything would be easy and useless.
        var page = 0
        var dialog = 0
        val root = sceneWithDialog(modal = true, onPage = { page++ }, onDialog = { dialog++ })

        PointerInputDispatcher().click(root, 10, 10)

        assertEquals(1, dialog)
        assertEquals(0, page)
    }

    @Test
    fun aNonModalLayerDoesNotBlockThePageBehindIt() {
        // A tooltip that swallowed every click on the page would be worse than no tooltip.
        var page = 0
        var dialog = 0
        val root = sceneWithDialog(modal = false, onPage = { page++ }, onDialog = { dialog++ })

        PointerInputDispatcher().click(root, 150, 150)

        assertEquals(1, page)
        assertEquals(0, dialog)
    }

    @Test
    fun hoverBehindAModalDoesNotLightUpWhatItCovers() {
        // Blocking clicks but not hover leaves a button under a dialog glowing as the pointer
        // crosses it, which reads as the dialog being not-quite-modal.
        var hovers = 0
        val root = scene {
            box(
                Modifier.size(200.dp).clickable(
                    onClick = { },
                ).then(HoverCounterElement { hovers++ }),
            )
            Layer(
                LayerKind.Dialog,
                modal = true,
                modifier = Modifier.size(50.dp),
                measurePolicy = modalStackPolicy,
            )
        }

        PointerInputDispatcher().dispatch(root, PointerEvent(PointerEventType.Move), 150, 150)

        assertEquals(0, hovers, "the covered page was hovered through the modal")
    }

    @Test
    fun theTopmostModalOwnsTheFrame() {
        // A dialog opened from a dialog. The first must not keep taking clicks aimed past the second.
        var first = 0
        var second = 0
        val root = scene {
            box(Modifier.size(200.dp))
            Layer(
                LayerKind.Dialog,
                modal = true,
                modifier = Modifier.size(200.dp).clickable { first++ },
                measurePolicy = modalStackPolicy,
            ) {
                Layer(
                    LayerKind.Dialog,
                    modal = true,
                    modifier = Modifier.size(50.dp).clickable { second++ },
                    measurePolicy = modalStackPolicy,
                )
            }
        }

        PointerInputDispatcher().click(root, 150, 150)

        assertEquals(0, first, "the outer dialog took a click outside the inner one")
        assertEquals(0, second)
    }

    @Test
    fun theFrameReportsAnOpenModalSeparatelyFromCapture() {
        // A backdrop click consumes nothing -- there is no node under it to consume -- so a game
        // gating on capture alone would fire a world action straight through an open dialog.
        val root = sceneWithDialog(modal = true, onPage = { }, onDialog = { })
        val dispatcher = PointerInputDispatcher()

        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), 150, 150)

        assertTrue(dispatcher.isModalOpen)
        assertFalse(dispatcher.hasCapture, "nothing was under the pointer to capture it")
    }

    @Test
    fun closingTheModalGivesThePageBack() {
        var page = 0
        val dispatcher = PointerInputDispatcher()
        val open = sceneWithDialog(modal = true, onPage = { page++ }, onDialog = { })
        dispatcher.click(open, 150, 150)
        assertEquals(0, page, "precondition: the modal blocked it")

        val closed = scene { box(Modifier.size(200.dp).clickable { page++ }) }
        dispatcher.click(closed, 150, 150)

        assertEquals(1, page)
        assertFalse(dispatcher.isModalOpen)
    }
}

/** Counts Enter events, so "was this hovered" is observable without a styling layer. */
private class HoverCounterElement(
    private val onEnter: () -> Unit,
) : ModifierNodeElement<HoverCounterNode>() {
    override fun create(): HoverCounterNode = HoverCounterNode()

    override fun update(node: HoverCounterNode) {
        node.onEnter = onEnter
    }
}

private class HoverCounterNode : Modifier.Node(), PointerInputNode {
    lateinit var onEnter: () -> Unit

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (event.type == PointerEventType.Enter) onEnter()
    }
}
