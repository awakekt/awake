/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [WorldAnchorLayout] places each child where its [AnchorProvider] says, read as the frame is laid out. */
class WorldAnchorLayoutTest {
    private class Spot(var x: Float, var y: Float, var visible: Boolean = true) : AnchorProvider {
        var asked = 0
        var surface = 0 to 0

        override fun anchor(into: AnchorPoint) {
            asked++
            surface = into.surfaceWidth to into.surfaceHeight
            into.x = x
            into.y = y
            into.visible = visible
        }
    }

    private val root = LayoutNode(
        MeasurePolicy { measurables, c ->
            val placed = measurables.map { it.measure(c) }
            layout(c.maxWidth, c.maxHeight) { placed.forEach { it.placeAt(0, 0) } }
        },
    )

    /** Composes [spots] once, each a 20 by 10 child, then lays out a 200 by 100 surface. */
    private fun mount(vararg spots: Spot?): List<LayoutNode> {
        composeInto(root) {
            WorldAnchorLayout {
                for (spot in spots) Spacer(if (spot == null) Modifier.size(20.dp, 10.dp) else Modifier.size(20.dp, 10.dp).worldAnchor(spot))
            }
        }
        layout()
        return root.children[0].children.let { kids -> List(kids.size) { kids[it] } }
    }

    private fun layout() = root.layoutTree(Constraints.of(200, 200, 100, 100))

    @Test
    fun aChildSitsBottomCentreOnItsPoint() {
        val child = mount(Spot(100f, 50f))[0]

        assertEquals(90 to 40, child.x to child.y, "centre 10 left of the point, bottom 10 above it")
    }

    @Test
    fun placementFollowsTheProviderWithoutComposingAgain() {
        val spot = Spot(100f, 50f)
        val child = mount(spot)[0]

        spot.x = 130f
        spot.y = 60f
        layout()

        assertEquals(120 to 50, child.x to child.y)
    }

    @Test
    fun aHiddenChildHasNoSizeAndIsNotPlacedOnTheSurface() {
        val spot = Spot(100f, 50f, visible = false)
        val child = mount(spot)[0]

        assertEquals(0 to 0, child.width to child.height)
        assertTrue(child.x < 0 && child.y < 0, "off any surface")

        spot.visible = true
        layout()
        assertEquals(20 to 10, child.width to child.height)
        assertEquals(90 to 40, child.x to child.y)
    }

    @Test
    fun theProviderSeesTheSurfaceItIsPlacedOn() {
        val spot = Spot(0f, 0f)
        mount(spot)

        assertEquals(200 to 100, spot.surface)
    }

    @Test
    fun aChildWithNoAnchorIsStackedAtTheTopLeft() {
        val child = mount(null)[0]

        assertEquals(0 to 0, child.x to child.y)
    }

    @Test
    fun manyChildrenEachFollowTheirOwnProvider() {
        val spots = Array(40) { Spot(it * 4f, 20f) }
        val children = mount(*spots)

        spots.forEach { it.x += 1f }
        layout()

        assertEquals(spots.indices.map { it * 4 + 1 - 10 }, children.map { it.x })
    }
}
