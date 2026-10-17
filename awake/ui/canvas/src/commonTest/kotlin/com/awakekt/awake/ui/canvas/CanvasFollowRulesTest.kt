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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [CanvasFollowRules] and [CanvasFollowLimit]: which followers are drawn, where, and how large. */
class CanvasFollowRulesTest {

    /** Each node named in [at] lands where it says, at its distance; [occluded] names those hidden behind something. */
    private class Points(val at: Map<String, CanvasProjection>, val occluded: Set<String> = emptySet()) : CanvasResolver {
        var asked = 0

        override fun project(target: String, offset: Vec3f): CanvasProjection? {
            asked++
            return at[target]
        }

        override fun occluded(target: String, offset: Vec3f): Boolean = target in occluded
    }

    private fun frame(items: List<CanvasItem>, resolver: CanvasResolver, limits: List<CanvasFollowLimit> = emptyList(), scale: Float = 1f): FrameOutput =
        ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) { CanvasOverlay(items, scale = scale, resolver = resolver, followLimits = limits) }

    private fun follower(node: String, rules: CanvasFollowRules? = null, configure: CanvasElement.() -> Unit = {}) = CanvasElement().apply {
        follow = node
        anchor = CanvasAnchor.BottomCenter
        offsetX = 0f
        offsetY = 0f
        width = 20f
        height = 10f
        followRules = rules
        configure()
    }

    private fun FrameOutput.box(id: Int): List<Int>? = semantics.findTag("canvas-element-$id")?.let { listOf(it.x, it.y, it.width, it.height) }

    private fun FrameOutput.drawn(count: Int): List<Int> = (1..count).filter { box(it) != null }

    @Test
    fun aFollowerWhollyOffTheCanvasIsLeftOut() {
        val element = follower("Far")
        val out = frame(listOf(CanvasItem(1, element)), Points(mapOf("Far" to CanvasProjection(-200f, 100f))))

        assertNull(out.box(1))
        assertNull(element.screenBounds)
    }

    @Test
    fun aFollowerPartlyOnTheCanvasIsDrawn() {
        val out = frame(listOf(CanvasItem(1, follower("Edge"))), Points(mapOf("Edge" to CanvasProjection(5f, 100f))))

        assertEquals(listOf(-5, 90, 20, 10), out.box(1))
    }

    @Test
    fun cullingOffScreenCanBeTurnedOff() {
        val element = follower("Far", CanvasFollowRules(cullOffScreen = false))
        val out = frame(listOf(CanvasItem(1, element)), Points(mapOf("Far" to CanvasProjection(-200f, 100f))))

        assertEquals(listOf(-210, 90, 20, 10), out.box(1))
    }

    @Test
    fun aFollowerBehindTheCameraHides() {
        val out = frame(listOf(CanvasItem(1, follower("Behind"))), Points(mapOf("Behind" to CanvasProjection(200f, 150f, behind = true))))

        assertNull(out.box(1))
    }

    @Test
    fun aFollowerBeyondItsMaxDistanceHides() {
        val rules = CanvasFollowRules(maxDistance = 40f)
        val items = listOf(CanvasItem(1, follower("Near", rules)), CanvasItem(2, follower("Far", rules)))
        val out = frame(items, Points(mapOf("Near" to CanvasProjection(100f, 100f, distance = 30f), "Far" to CanvasProjection(200f, 100f, distance = 50f))))

        assertEquals(listOf(1), out.drawn(2))
    }

    @Test
    fun anOccludedFollowerHidesOnlyWhenItsRulesAsk() {
        val items = listOf(CanvasItem(1, follower("Wall", CanvasFollowRules(occlusion = true))), CanvasItem(2, follower("Wall")))
        val out = frame(items, Points(mapOf("Wall" to CanvasProjection(100f, 100f)), occluded = setOf("Wall")))

        assertEquals(listOf(2), out.drawn(2))
    }

    @Test
    fun aLimitKeepsTheNearest() {
        val items = (1..5).map { CanvasItem(it, follower("N$it")) }
        val points = Points((1..5).associate { "N$it" to CanvasProjection(it * 50f, 100f, distance = (6 - it) * 10f) })

        val out = frame(items, points, listOf(CanvasFollowLimit(max = 2)))

        assertEquals(listOf(4, 5), out.drawn(5), "the two nearest")
        assertEquals(5, points.asked, "each follower projected once")
    }

    @Test
    fun aLimitByPriorityKeepsTheHighestThenTheNearest() {
        val items = (1..4).map { index -> CanvasItem(index, follower("N$index", CanvasFollowRules(priority = if (index <= 2) 5f else 1f))) }
        val points = Points((1..4).associate { "N$it" to CanvasProjection(it * 50f, 100f, distance = it * 10f) })

        val out = frame(items, points, listOf(CanvasFollowLimit(max = 3, rankBy = CanvasFollowRank.Priority)))

        assertEquals(listOf(1, 2, 3), out.drawn(4))
    }

    @Test
    fun aLimitByScoreKeepsWhatGameCodeScoresHighest() {
        val items = (1..3).map { index -> CanvasItem(index, follower("N$index") { followScore = index.toFloat() }) }
        val points = Points((1..3).associate { "N$it" to CanvasProjection(it * 50f, 100f) })

        val out = frame(items, points, listOf(CanvasFollowLimit(max = 1, rankBy = CanvasFollowRank.Score)))

        assertEquals(listOf(3), out.drawn(3))
    }

    @Test
    fun aLimitCountsOnlyItsOwnGroup() {
        val items = listOf(
            CanvasItem(1, follower("A", CanvasFollowRules(group = "plates"))),
            CanvasItem(2, follower("B", CanvasFollowRules(group = "plates"))),
            CanvasItem(3, follower("C", CanvasFollowRules(group = "markers"))),
        )
        val points = Points(mapOf("A" to CanvasProjection(50f, 100f, distance = 5f), "B" to CanvasProjection(100f, 100f, distance = 9f), "C" to CanvasProjection(150f, 100f)))

        val out = frame(items, points, listOf(CanvasFollowLimit(group = "plates", max = 1)))

        assertEquals(listOf(1, 3), out.drawn(3))
    }

    @Test
    fun aCulledFollowerMakesRoomUnderALimit() {
        val items = listOf(CanvasItem(1, follower("Off")), CanvasItem(2, follower("On")))
        val points = Points(mapOf("Off" to CanvasProjection(-500f, 100f, distance = 1f), "On" to CanvasProjection(100f, 100f, distance = 9f)))

        assertEquals(listOf(2), frame(items, points, listOf(CanvasFollowLimit(max = 1))).drawn(2))
    }

    @Test
    fun aScaleCurveSizesTheFollowerAboutItsAnchorPoint() {
        val curve = CanvasCurve(listOf(CanvasCurvePoint(10f, 1f), CanvasCurvePoint(30f, 0.5f)))
        val element = follower("Hero", CanvasFollowRules(scale = curve)) { width = 40f; height = 20f }

        val out = frame(listOf(CanvasItem(1, element)), Points(mapOf("Hero" to CanvasProjection(200f, 150f, distance = 30f))))

        assertEquals(listOf(190, 140, 20, 10), out.box(1), "half size, still standing on the point")
        val bounds = assertNotNull(element.screenBounds)
        assertEquals(listOf(190f, 140f, 20f, 10f), listOf(bounds.x, bounds.y, bounds.width, bounds.height), "reported in the canvas's units")
    }

    @Test
    fun aFollowerFadedToNothingIsLeftOut() {
        val fade = CanvasCurve(listOf(CanvasCurvePoint(0f, 1f), CanvasCurvePoint(100f, 0f)))
        val items = listOf(CanvasItem(1, follower("Near", CanvasFollowRules(fade = fade))), CanvasItem(2, follower("Gone", CanvasFollowRules(fade = fade))))
        val out = frame(items, Points(mapOf("Near" to CanvasProjection(100f, 100f, distance = 50f), "Gone" to CanvasProjection(200f, 100f, distance = 120f))))

        assertEquals(listOf(1), out.drawn(2))
    }

    @Test
    fun aCurveIsStraightBetweenItsPointsAndHeldPastThem() {
        val curve = CanvasCurve(listOf(CanvasCurvePoint(10f, 2f), CanvasCurvePoint(20f, 4f), CanvasCurvePoint(40f, 0f)))

        assertEquals(2f, curve.valueAt(0f))
        assertEquals(3f, curve.valueAt(15f))
        assertEquals(2f, curve.valueAt(30f))
        assertEquals(0f, curve.valueAt(99f))
        assertEquals(7f, CanvasCurve().valueAt(5f, otherwise = 7f))
        assertTrue(CanvasCurve(listOf(CanvasCurvePoint(5f), CanvasCurvePoint(1f))).problems().isNotEmpty())
    }

    @Test
    fun aPinnedFollowerHoldsToTheEdgeTowardItsTarget() {
        val element = follower("East", CanvasFollowRules(pinToEdge = true, edgeMargin = 5f))

        val out = frame(listOf(CanvasItem(1, element)), Points(mapOf("East" to CanvasProjection(1000f, 150f))))

        assertEquals(listOf(WIDTH - 5 - 20, 145, 20, 10), out.box(1))
        assertTrue(element.pinned)
        assertEquals(0f, element.pinAngle, 0.01f)
    }

    @Test
    fun aPinnedFollowerBehindTheCameraGoesTheWayItLies() {
        val west = follower("West", CanvasFollowRules(pinToEdge = true))
        val behind = follower("Behind", CanvasFollowRules(pinToEdge = true))
        val points = Points(mapOf("West" to CanvasProjection(-9000f, 150f, behind = true), "Behind" to CanvasProjection(200f, 150f, behind = true)))

        val out = frame(listOf(CanvasItem(1, west), CanvasItem(2, behind)), points)

        assertEquals(listOf(0, 145, 20, 10), out.box(1))
        assertEquals(180f, kotlin.math.abs(west.pinAngle), 0.01f)
        assertEquals(listOf(190, HEIGHT - 10, 20, 10), out.box(2), "straight behind is taken as below")
        assertEquals(90f, behind.pinAngle, 0.01f)
    }

    @Test
    fun aFollowerOnScreenIsNotPinned() {
        val element = follower("Here", CanvasFollowRules(pinToEdge = true))

        val out = frame(listOf(CanvasItem(1, element)), Points(mapOf("Here" to CanvasProjection(100f, 100f))))

        assertEquals(listOf(90, 90, 20, 10), out.box(1))
        assertFalse(element.pinned)
    }

    @Test
    fun anArrowShowsOnlyWhilePinnedOnTheEdgeTowardTheTarget() {
        val marker = follower("Target", CanvasFollowRules(pinToEdge = true)) { width = 40f; height = 40f }
        val arrow = CanvasElement().apply { followArrow = true; width = 10f; height = 10f }
        val items = listOf(CanvasItem(1, marker), CanvasItem(2, arrow, marker))

        val pinned = frame(items, Points(mapOf("Target" to CanvasProjection(1000f, 150f))))
        val onScreen = frame(items, Points(mapOf("Target" to CanvasProjection(100f, 150f))))

        val markerBox = assertNotNull(pinned.box(1))
        assertEquals(listOf(markerBox[0] + 40 - 5, markerBox[1] + 20 - 5, 10, 10), pinned.box(2), "centred on the marker's right edge")
        assertNull(onScreen.box(2))
    }

    @Test
    fun followersPlaceInTheCanvasUnitsAtAnyScale() {
        val element = follower("Hero", CanvasFollowRules(pinToEdge = true))

        val out = frame(listOf(CanvasItem(1, element)), Points(mapOf("Hero" to CanvasProjection(1000f, 150f))), scale = 2f)

        // A canvas 200 by 150 units at scale 2: held to the right edge, 20 units wide, 40 pixels.
        assertEquals(listOf(WIDTH - 40, 140, 40, 20), out.box(1))
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
    }
}

private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
