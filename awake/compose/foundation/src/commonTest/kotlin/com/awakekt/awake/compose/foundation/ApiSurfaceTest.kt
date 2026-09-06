/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.gestures.draggable
import com.awakekt.awake.compose.foundation.interaction.Interaction
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.ColumnScopeInstance
import com.awakekt.awake.compose.foundation.layout.IntrinsicSize
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.focus.FocusOwner
import com.awakekt.awake.compose.ui.focus.FocusRequester
import com.awakekt.awake.compose.ui.focus.focusRequester
import com.awakekt.awake.compose.ui.focus.onFocusChanged
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun sized(
    modifier: Modifier,
    viewport: Int = 200,
    maxHeight: Int = viewport,
): LayoutNode {
    val root = LayoutNode(ColumnMeasurePolicy())
    composeInto(root) { Spacer(modifier) }
    root.layoutTree(Constraints.of(0, viewport, 0, maxHeight))
    return root.children[0]
}

/** The overloads and shapes the suite reached around rather than through. */
class PaddingOverloadTest {

    @Test
    fun horizontalAndVerticalPadEachAxisOnce() {
        // Padding outside the size, so the pad shows up in the outer box rather than being
        // swallowed by a fixed size declared outside it.
        val node = sized(Modifier.padding(horizontal = 5.dp, vertical = 3.dp).size(10.dp))

        assertEquals(20, node.width, "10 + 5 + 5")
        assertEquals(16, node.height, "10 + 3 + 3")
    }

    @Test
    fun perSidePaddingIsAsymmetric() {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Spacer(Modifier.padding(start = 1.dp, top = 2.dp, end = 4.dp, bottom = 8.dp).size(10.dp))
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        assertEquals(15, root.children[0].width, "10 + 1 + 4")
        assertEquals(20, root.children[0].height, "10 + 2 + 8")
    }

    @Test
    fun fillMaxSizeTakesBothBoundedAxes() {
        val root = LayoutNode(BoxMeasurePolicy())
        composeInto(root) { Spacer(Modifier.fillMaxSize()) }
        root.layoutTree(Constraints.of(0, 120, 0, 120))

        assertEquals(120, root.children[0].width)
        assertEquals(120, root.children[0].height)
    }

    @Test
    fun fillingAnUnboundedAxisIsANoOp() {
        // Compose's rule, and the `UNBOUNDED_MAIN_AXIS` bug class it prevents: a child that filled
        // an axis with no bound would adopt the no-bound sentinel as a real height. The unbounded
        // height has to come from outside -- a Column passes its own bound down, so nesting one is
        // not enough to produce the case.
        val node = sized(Modifier.fillMaxSize(), viewport = 120, maxHeight = Constraints.Infinity)

        assertEquals(120, node.width, "the bounded axis should still fill")
        assertEquals(0, node.height, "the unbounded axis adopted a sentinel as a size")
    }
}

/**
 * The non-default policy branch of each container.
 *
 * `Column`/`Row`/`Box` cache a shared policy for the default arrangement and allocate one otherwise.
 * Only the cached path had a test, so the allocating path -- the one every real screen with spacing
 * takes -- was never exercised.
 */
class ContainerPolicyTest {

    private fun laidOut(content: context(Composer) () -> Unit): LayoutNode {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root, content)
        root.layoutTree(Constraints.of(0, 300, 0, 300))
        return root
    }

    @Test
    fun aColumnWithSpacingUsesItsOwnPolicy() {
        val root = laidOut {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.size(10.dp))
                Spacer(Modifier.size(10.dp))
            }
        }

        assertEquals(26, root.children[0].height, "10 + 6 + 10")
    }

    @Test
    fun aRowWithSpacingUsesItsOwnPolicy() {
        val root = laidOut {
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(6.dp)) {
                Spacer(Modifier.size(10.dp))
                Spacer(Modifier.size(10.dp))
            }
        }

        assertEquals(26, root.children[0].width, "10 + 6 + 10")
    }

    @Test
    fun aBoxWithAlignmentUsesItsOwnPolicy() {
        val root = laidOut {
            Box(
                Modifier.size(40.dp),
                horizontalAlignment = Alignment.End,
                verticalAlignment = Alignment.Bottom,
            ) {
                Spacer(Modifier.size(10.dp))
            }
        }

        val child = root.children[0].children[0]
        assertEquals(30, child.x, "40 - 10")
        assertEquals(30, child.y)
    }
}

/**
 * `toString` on a modifier link is the diagnostic surface, not decoration.
 *
 * A chain prints as `[padding(...), background(...)]`, and that dump is how a layout bug gets read.
 * A link whose label went blank or wrong would degrade every future investigation silently.
 */
class ModifierLabelTest {

    @Test
    fun everyLinkNamesItselfInTheChainDump() {
        val chain = Modifier
            .padding(4.dp)
            .size(10.dp)
            .fillMaxSize()
            .background(Color(1f, 0f, 0f, 1f))
            .border(1.dp, Color(0f, 1f, 0f, 1f))
            .alpha(0.5f)
            .clipToBounds()
            .onPlaced { _, _, _, _ -> }
            .clickable {}
            .hoverable(InteractionSource())
            .focusable()
            .testTag("tag")
            .toString()

        for (label in listOf(
            "padding", "size", "fillMax", "background", "border",
            "alpha", "clip", "onPlaced", "clickable", "hoverable", "focusable", "semantics",
        )) {
            assertTrue(chain.contains(label), "$label missing from the chain dump: $chain")
        }
    }

    @Test
    fun theDiagnosticLinksNameThemselvesToo() {
        // Not reachable from the chain dump above, because each needs its own construction.
        val chain = Modifier
            .drawBehind { }
            .draggable { _, _ -> }
            .onFocusChanged { }
            .focusRequester(FocusRequester())
            .width(IntrinsicSize.Max)
            .toString()

        for (label in listOf("drawBehind", "draggable", "onFocusChanged", "focusRequester", "IntrinsicSize")) {
            assertTrue(chain.contains(label), "$label missing from: $chain")
        }
        // `weight` only exists inside a scope, so it cannot join the chain above.
        val weighted = with(ColumnScopeInstance) { Modifier.weight(2f) }.toString()
        assertTrue(weighted.contains("weight"), "weight missing from: $weighted")
    }

    @Test
    fun anInteractionSourceReportsItsThreeFlags() {
        val source = InteractionSource()
        source.tryEmit(Interaction.Hover.Enter)

        assertEquals(
            "InteractionSource(hovered=true, pressed=false, focused=false)",
            source.toString(),
        )
    }
}

/** The composables and scopes reached only through other tests' fixtures. */
class ComposableSurfaceTest {

    @Test
    fun aRememberedInteractionSourceIsTheSameObjectNextPass() {
        val root = LayoutNode(ColumnMeasurePolicy())
        val seen = mutableListOf<InteractionSource>()
        val content: context(Composer)
        () -> Unit = {
            Column {
                seen += rememberInteractionSource()
                Spacer(Modifier.size(10.dp))
            }
        }
        composeInto(root, content)
        composeInto(root, content)

        assertEquals(2, seen.size)
        assertTrue(seen[0] === seen[1], "a new source every pass loses hover and press state")
    }

    @Test
    fun aCanvasDrawsWhereItIsPlaced() {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Spacer(Modifier.size(10.dp))
            Canvas(Modifier.size(20.dp)) { drawRect(color = Color(0f, 0f, 1f, 1f)) }
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val quad = Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>().single()

        assertEquals(listOf(0f, 10f, 20f, 20f), listOf(quad.x, quad.y, quad.w, quad.h))
    }

    @Test
    fun weightSplitsTheRemainderInBothScopes() {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Column(Modifier.size(60.dp)) {
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(2f))
            }
            Row(Modifier.size(60.dp)) {
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val column = root.children[0]
        assertEquals(listOf(20, 40), column.children.let { listOf(it[0].height, it[1].height) })
        val row = root.children[1]
        assertEquals(listOf(30, 30), row.children.let { listOf(it[0].width, it[1].width) })
    }
}

/** Small public entry points nothing else happened to call. */
class SmallSurfaceTest {

    @Test
    fun isFocusedAgreesWithTheFocusedNode() {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Spacer(Modifier.size(10.dp).focusable())
            Spacer(Modifier.size(10.dp).focusable())
        }
        root.layoutTree(Constraints.of(0, 100, 0, 100))
        val owner = FocusOwner()

        owner.requestFocus(root, root.children[0])

        assertTrue(owner.isFocused(root.children[0]))
        assertTrue(!owner.isFocused(root.children[1]))
    }

    @Test
    fun eachSideRoundsToWholePixelsOnItsOwn() {
        // `Float.dp` alongside `Int.dp`. Each side rounds independently, so a 2.5 dp pad costs 3 px
        // twice rather than 5 px shared -- worth pinning, because the alternative reading (round the
        // total) silently loses a pixel of inset on one edge.
        val node = sized(Modifier.padding(2.5f.dp).size(10.dp))

        assertEquals(16, node.width, "10 + 3 + 3")
    }

    @Test
    fun paddingDefaultsTheSidesItIsNotGiven() {
        val node = sized(Modifier.padding(start = 6.dp).size(10.dp))

        assertEquals(16, node.width, "only the start side padded")
        assertEquals(10, node.height, "top and bottom defaulted to zero")
    }

    @Test
    fun heightIntrinsicMaxResolvesThroughTheNode() {
        val node = LayoutNode(ColumnMeasurePolicy())
        composeInto(node) {
            Spacer(Modifier.size(10.dp))
            Spacer(Modifier.size(10.dp))
        }
        node.modifier = Modifier.height(IntrinsicSize.Max)

        assertEquals(20, node.minIntrinsicHeight(100))
        assertEquals(20, node.maxIntrinsicHeight(100))
    }
}
