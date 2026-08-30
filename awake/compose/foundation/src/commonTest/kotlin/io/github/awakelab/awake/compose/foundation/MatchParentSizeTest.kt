/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.BoxMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.BoxScopeInstance
import io.github.awakelab.awake.compose.foundation.layout.fillMaxSize
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A matchParentSize child takes the box's size without being part of what decides it.
 *
 * The distinction only shows against `fillMaxSize`, which takes the incoming maximum and so drags a
 * shrink-wrapping Box up to the whole viewport with it -- the difference between an overlay that
 * covers a control and one that redefines how big the control is.
 */
class MatchParentSizeTest {

    @Test
    fun theOverlayTakesTheBoxSizeWithoutDefiningIt() {
        val content = child(CONTENT, CONTENT)
        val overlay = child(1, 1, with(BoxScopeInstance) { Modifier.matchParentSize() })
        val box = box(content, overlay).layoutIn(Constraints.of(0, VIEWPORT, 0, VIEWPORT))

        assertEquals(CONTENT, box.width, "the box still shrink-wraps its sizing child")
        assertEquals(CONTENT, box.height, "the box still shrink-wraps its sizing child")
        assertEquals(CONTENT, overlay.width, "the overlay covers the box")
        assertEquals(CONTENT, overlay.height, "the overlay covers the box")
    }

    /** The behaviour this exists to replace, pinned so the difference does not become folklore. */
    @Test
    fun fillMaxSizeInsteadDragsTheBoxToTheViewport() {
        val content = child(CONTENT, CONTENT)
        val overlay = child(1, 1, Modifier.fillMaxSize())
        val box = box(content, overlay).layoutIn(Constraints.of(0, VIEWPORT, 0, VIEWPORT))

        assertEquals(VIEWPORT, box.width, "fillMaxSize takes the incoming maximum and the box follows")
    }

    @Test
    fun aBoxOfOnlyMatchParentSizeChildrenCollapses() {
        val overlay = child(1, 1, with(BoxScopeInstance) { Modifier.matchParentSize() })
        val box = box(overlay).layoutIn(Constraints.of(0, VIEWPORT, 0, VIEWPORT))

        assertEquals(0, box.width, "nothing declared a size, so there is none to match")
    }

    private fun box(vararg children: LayoutNode): LayoutNode =
        LayoutNode(BoxMeasurePolicy()).also { children.forEach(it.children::add) }

    private companion object {
        const val VIEWPORT = 400
        const val CONTENT = 120
    }
}
