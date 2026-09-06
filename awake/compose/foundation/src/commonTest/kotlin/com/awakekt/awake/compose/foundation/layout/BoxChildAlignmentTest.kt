/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `BoxScope.align` puts each child where it asks, not where the box would.
 *
 * The case it exists for is a viewport's floating chrome: tools in one corner, display toggles
 * centred on the top edge, a gizmo in the other corner, all over a scene that keeps its whole
 * area. Before it, a Box could only stack every child at one anchor, so a panel wanting several
 * spent layout rows on them -- and a row above the scene is a band of the scene that is gone.
 */
class BoxChildAlignmentTest {

    private fun bounds(tag: String, nodes: List<SemanticsNode>): SemanticsNode? =
        nodes.firstNotNullOfOrNull { node ->
            if (node.testTag == tag) node else bounds(tag, node.children)
        }

    @Test
    fun eachChildLandsAtItsOwnAnchor() {
        val frame = ComposeHost().frame(FrameInput(SIDE, SIDE)) {
            Box(Modifier.width(SIDE.dp).height(SIDE.dp)) {
                Box(Modifier.align(Alignment.TopStart).size(CHILD.dp).testTag("top-start"))
                Box(Modifier.align(Alignment.TopCenter).size(CHILD.dp).testTag("top-centre"))
                Box(Modifier.align(Alignment.BottomEnd).size(CHILD.dp).testTag("bottom-end"))
            }
        }

        val topStart = requireNotNull(bounds("top-start", frame.semantics))
        val topCentre = requireNotNull(bounds("top-centre", frame.semantics))
        val bottomEnd = requireNotNull(bounds("bottom-end", frame.semantics))

        assertEquals(0 to 0, topStart.x to topStart.y, "top-start moved")
        assertEquals((SIDE - CHILD) / 2 to 0, topCentre.x to topCentre.y, "top-centre is not centred")
        assertEquals(
            (SIDE - CHILD) to (SIDE - CHILD),
            bottomEnd.x to bottomEnd.y,
            "bottom-end is not in the far corner",
        )
    }

    /**
     * A child with no alignment of its own still follows the box.
     *
     * The pair matters: `align` is opt-in per child, so adding one anchored child must not move
     * the siblings that never asked for anything.
     */
    @Test
    fun anUnalignedChildStillFollowsTheBox() {
        val frame = ComposeHost().frame(FrameInput(SIDE, SIDE)) {
            Box(Modifier.width(SIDE.dp).height(SIDE.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(CHILD.dp).testTag("follows"))
                Box(Modifier.align(Alignment.TopStart).size(CHILD.dp).testTag("anchored"))
            }
        }

        val follows = requireNotNull(bounds("follows", frame.semantics))
        val anchored = requireNotNull(bounds("anchored", frame.semantics))

        assertEquals((SIDE - CHILD) / 2 to (SIDE - CHILD) / 2, follows.x to follows.y, "the box's own child moved")
        assertEquals(0 to 0, anchored.x to anchored.y, "the anchored child did not")
    }

    private companion object {
        const val SIDE = 100
        const val CHILD = 20
    }

    /**
     * `BoxWithConstraints` is a `BoxScope`, so `align` has to mean the same thing there.
     *
     * It measured and placed its children itself, ignoring their alignment, which made a
     * responsive layout that anchors per child silently stack everything in one corner.
     */
    @Test
    fun aConstraintsBoxAnchorsItsChildrenToo() {
        val frame = ComposeHost().frame(FrameInput(SIDE, SIDE)) {
            BoxWithConstraints(Modifier.width(SIDE.dp).height(SIDE.dp)) {
                Box(Modifier.align(Alignment.BottomEnd).size(CHILD.dp).testTag("anchored"))
            }
        }

        val anchored = requireNotNull(bounds("anchored", frame.semantics))

        assertEquals(
            (SIDE - CHILD) to (SIDE - CHILD),
            anchored.x to anchored.y,
            "BoxWithConstraints ignored its child's alignment",
        )
    }
}
