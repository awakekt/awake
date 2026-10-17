/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** [CanvasOverlay] keeps anchored elements inside the safe area it is given, unless one opts out. */
class CanvasSafeAreaTest {

    private val notch = CanvasInsets(left = 40f, top = 24f, right = 10f, bottom = 30f)

    private fun frame(
        items: List<CanvasItem>,
        safeArea: CanvasInsets = notch,
        scale: Float = 1f,
        resolver: CanvasResolver = CanvasResolver.None,
    ): FrameOutput = ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) {
        CanvasOverlay(items, scale = scale, resolver = resolver, safeArea = safeArea)
    }

    private fun FrameOutput.box(id: Int): List<Int> =
        assertNotNull(semantics.findTag("canvas-element-$id")).let { listOf(it.x, it.y, it.width, it.height) }

    private fun element(anchor: CanvasAnchor, configure: CanvasElement.() -> Unit = {}) = CanvasElement().apply {
        this.anchor = anchor
        offsetX = 5f
        offsetY = 5f
        width = 20f
        height = 10f
        configure()
    }

    @Test
    fun cornerElementsAreMeasuredFromTheSafeAreasEdges() {
        val out = frame(listOf(CanvasItem(1, element(CanvasAnchor.TopLeft)), CanvasItem(2, element(CanvasAnchor.BottomRight))))

        assertEquals(listOf(45, 29, 20, 10), out.box(1))
        assertEquals(listOf(WIDTH - 10 - 5 - 20, HEIGHT - 30 - 5 - 10, 20, 10), out.box(2))
    }

    @Test
    fun aCentredElementIsCentredInTheSafeArea() {
        val out = frame(listOf(CanvasItem(1, element(CanvasAnchor.Center) { offsetX = 0f; offsetY = 0f })))

        // The safe area runs from 40 to 390 across and 24 to 270 down: its centre is (215, 147).
        assertEquals(listOf(205, 142, 20, 10), out.box(1))
    }

    @Test
    fun anElementOptedOutIsMeasuredFromTheCanvasEdges() {
        val out = frame(listOf(CanvasItem(1, element(CanvasAnchor.TopLeft) { safeArea = false })))

        assertEquals(listOf(5, 5, 20, 10), out.box(1))
    }

    @Test
    fun aChildIsPlacedInsideItsParentAsBefore() {
        val parent = element(CanvasAnchor.TopLeft) { kind = CanvasElementKind.Panel; width = 100f; height = 80f }
        val child = element(CanvasAnchor.TopLeft)
        val out = frame(listOf(CanvasItem(1, parent), CanvasItem(2, child, parent)))

        assertEquals(listOf(45, 29, 100, 80), out.box(1))
        assertEquals(listOf(50, 34, 20, 10), out.box(2), "the parent's own box, not the safe area, is the child's")
    }

    @Test
    fun aFollowerStaysOnWhatItFollows() {
        val follower = element(CanvasAnchor.TopLeft) { follow = "Hero"; offsetX = 0f; offsetY = 0f }
        val resolver = object : CanvasResolver {
            override fun point(target: String, offset: Vec3f): Vec2 = Vec2(12f, 8f)
        }

        assertEquals(listOf(12, 8, 20, 10), frame(listOf(CanvasItem(1, follower)), resolver = resolver).box(1))
    }

    @Test
    fun theInsetsAreTheHostsDpWhateverTheCanvasScale() {
        val out = frame(listOf(CanvasItem(1, element(CanvasAnchor.TopLeft))), scale = 2f)

        // The 40 and 24 dp insets stay 40 and 24 pixels; the element's own offset and size double.
        assertEquals(listOf(50, 34, 40, 20), out.box(1))
    }

    @Test
    fun noSafeAreaPlacesAsBefore() {
        val out = frame(listOf(CanvasItem(1, element(CanvasAnchor.BottomRight))), safeArea = CanvasInsets.None)

        assertEquals(listOf(WIDTH - 25, HEIGHT - 15, 20, 10), out.box(1))
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
    }
}

private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
