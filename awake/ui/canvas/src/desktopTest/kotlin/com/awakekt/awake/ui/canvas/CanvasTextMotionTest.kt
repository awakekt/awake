/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.render.capture.PixelMap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the shared shimmer-free text motion and scaling rule (#1063) across `:awake:ui:canvas`:
 *
 * - Projected 3D followers (`CanvasElement.follow`), both opaque and faded (`alpha < 1`, which wraps
 *   the follower in a `graphicsLayer`), glide smoothly by `0.1 px/frame` across 20 frames, including
 *   outlined floating labels.
 * - Anchored HUD elements (`TopLeft`, `Center`, `BottomRight`) whose `offsetX` tweens by `0.1 px/frame`
 *   glide smoothly and ease back onto exact static placement when motion stops.
 * - Distance-scaled followers and custom `CanvasSlot`s crossing `1.00x` and the `24 px` pixel-glyph
 *   limit show monotonic ink growth with no single-frame path-switch jump.
 */
class CanvasTextMotionTest {
    private val ground = Color(0.08f, 0.08f, 0.10f, 1f)

    @Test
    fun followerSubpixelMotionGlidesSmoothlyWhenOpaqueAndWhenFaded() {
        for (fadeAlpha in listOf(1f, 0.75f)) {
            var targetX = 90f
            val resolver = object : CanvasResolver {
                override fun project(target: String, offset: Vec3f): CanvasProjection? =
                    if (target == "actor") {
                        CanvasProjection(x = targetX, y = 40f, distance = if (fadeAlpha < 1f) 15f else 5f)
                    } else {
                        null
                    }
            }
            val label = CanvasElement().apply {
                kind = CanvasElementKind.Text
                follow = "actor"
                followRules = if (fadeAlpha < 1f) {
                    CanvasFollowRules(fade = CanvasCurve(listOf(CanvasCurvePoint(5f, 1f), CanvasCurvePoint(45f, 0f))))
                } else {
                    CanvasFollowRules()
                }
                text = "Harbor +12"
                fontSize = 14f
                width = 110f
                height = 24f
                color = "#F5F5F7"
                style = CanvasStyle(textOutline = CanvasTextOutline(color = "#101018", width = 1f))
            }
            val items = listOf(CanvasItem(1, label))
            val host = ComposeHost(density = 1f)
            val metrics = (0 until 20).map { i ->
                targetX = 90f + i * 0.1f
                val out = host.frame(FrameInput(240, 80)) {
                    CanvasOverlay(items, resolver = resolver)
                }
                measureInk(
                    out.primitives.rasterizeToPixelMap(
                        width = 240,
                        height = 80,
                        background = ground,
                        font = UiFonts.default(),
                        layers = out.graphicsLayers,
                    ),
                )
            }
            val baseMass = metrics.first().mass
            assertTrue(baseMass > 8.0, "follower alpha=$fadeAlpha drew no ink")
            for (i in 1 until metrics.size) {
                val step = metrics[i].centroidX - metrics[i - 1].centroidX
                assertTrue(
                    step in 0.03..0.19,
                    "follower(alpha=$fadeAlpha) frame $i step=$step px expected in [0.03, 0.19]",
                )
                val drift = abs(metrics[i].mass - baseMass) / baseMass
                assertTrue(drift < 0.045, "follower(alpha=$fadeAlpha) frame $i ink drifted by ${"%.2f".format(drift * 100)}%")
            }
            val unitPeriodShift = metrics[10].centroidX - metrics[0].centroidX
            assertTrue(abs(unitPeriodShift - 1.0) < 0.01, "follower(alpha=$fadeAlpha) 10-frame shift=$unitPeriodShift px expected 1.0 ± 0.01")
            val totalShift = metrics.last().centroidX - metrics.first().centroidX
            assertTrue(totalShift in 1.80..2.00, "follower(alpha=$fadeAlpha) totalShift=$totalShift px expected in [1.80, 2.00]")
        }
    }

    @Test
    fun anchoredElementsTweenSubpixelOffsetsAndSettleToExactStaticPixels() {
        for (anchor in listOf(CanvasAnchor.TopLeft, CanvasAnchor.Center, CanvasAnchor.BottomRight)) {
            val element = CanvasElement().apply {
                kind = CanvasElementKind.Text
                this.anchor = anchor
                offsetX = 12f
                offsetY = 10f
                width = 120f
                height = 24f
                text = "llll 1111"
                fontSize = 14f
                color = "#F5F5F7"
            }
            val items = listOf(CanvasItem(1, element))
            val host = ComposeHost(density = 1f)
            val centroids = (0 until 20).map { i ->
                element.offsetX = 12f + i * 0.1f
                val out = host.frame(FrameInput(260, 72)) { CanvasOverlay(items) }
                measureInk(out.primitives.rasterizeToPixelMap(260, 72, ground, UiFonts.default(), out.graphicsLayers)).centroidX
            }
            val expectedSign = if (anchor == CanvasAnchor.BottomRight) -1.0 else 1.0
            for (i in 1 until centroids.size) {
                val signedStep = (centroids[i] - centroids[i - 1]) * expectedSign
                assertTrue(
                    signedStep in 0.03..0.19,
                    "anchor=$anchor frame $i signedStep=$signedStep px expected in [0.03, 0.19]",
                )
            }
            val unitPeriodShift = (centroids[10] - centroids[0]) * expectedSign
            assertTrue(abs(unitPeriodShift - 1.0) < 0.01, "anchor=$anchor 10-frame signed shift=$unitPeriodShift px expected 1.0 ± 0.01")
            val totalSignedShift = (centroids.last() - centroids.first()) * expectedSign
            assertTrue(totalSignedShift in 1.80..2.00, "anchor=$anchor totalSignedShift=$totalSignedShift px expected in [1.80, 2.00]")

            // Hold stationary at the final fractional offset (13.9f) for 8 settle frames and confirm exact match against static render.
            var settled: PixelMap? = null
            var prevSettleCx = centroids.last()
            repeat(8) {
                val out = host.frame(FrameInput(260, 72)) { CanvasOverlay(items) }
                val map = out.primitives.rasterizeToPixelMap(260, 72, ground, UiFonts.default(), out.graphicsLayers)
                val cx = measureInk(map).centroidX
                assertTrue(abs(cx - prevSettleCx) <= 0.15, "anchor=$anchor settle frame popped by ${abs(cx - prevSettleCx)} px")
                prevSettleCx = cx
                settled = map
            }
            val staticOut = ComposeHost(density = 1f).frame(FrameInput(260, 72)) { CanvasOverlay(items) }
            val staticMap = staticOut.primitives.rasterizeToPixelMap(260, 72, ground, UiFonts.default(), staticOut.graphicsLayers)
            assertEquals(0, differingPixels(checkNotNull(settled), staticMap), "anchor=$anchor settled frame must match static render")
        }
    }

    @Test
    fun followerAndSlotScaleAcrossUnitAnd24PxWithoutCoverageJump() {
        // 1. Follower scaling 0.90x..1.10x across 1.00x and 24 px via LocalDensity.
        var dist = 20f
        val resolver = object : CanvasResolver {
            override fun project(target: String, offset: Vec3f): CanvasProjection? =
                CanvasProjection(x = 180f, y = 55f, distance = dist)
        }
        val follower = CanvasElement().apply {
            kind = CanvasElementKind.Text
            follow = "actor"
            followRules = CanvasFollowRules(
                scale = CanvasCurve(listOf(CanvasCurvePoint(10f, 1.10f), CanvasCurvePoint(30f, 0.90f))),
            )
            text = "Harbor 24"
            fontSize = 24f
            width = 180f
            height = 40f
            color = "#F5F5F7"
        }
        val followerItems = listOf(CanvasItem(1, follower))
        val followerHost = ComposeHost(density = 1f)
        val scales = (0..20).map { 0.90f + it * 0.01f }
        val followerMasses = scales.mapIndexed { idx, _ ->
            // distance sweeps 30f (0.90x) -> 10f (1.10x), hitting 20f (1.00x) at idx = 10.
            dist = 30f - idx * 1f
            val out = followerHost.frame(FrameInput(360, 110)) { CanvasOverlay(followerItems, resolver = resolver) }
            measureInk(out.primitives.rasterizeToPixelMap(360, 110, ground, UiFonts.default(), out.graphicsLayers)).mass
        }
        assertMonotonicScale("Follower scale", scales, followerMasses)

        // 2. Custom CanvasSlot scaling 0.90x..1.10x across 1.00x (where CanvasSlotView elides graphicsLayer at alpha=1, scale=1).
        val button = CanvasElement().apply {
            kind = CanvasElementKind.Button
            button = CanvasButton()
            anchor = CanvasAnchor.Center
            width = 180f
            height = 48f
        }
        val slotHost = ComposeHost(density = 1f)
        val slotMasses = scales.map { s ->
            button.skin = CanvasControlSkin(
                button = CanvasButtonSkin(
                    background = CanvasSlot(
                        custom = "scaledLabel",
                        states = mapOf(CanvasControlState.Idle to CanvasSlotOverride(scale = s)),
                    ),
                ),
            )
            val out = slotHost.frame(FrameInput(360, 110)) {
                CanvasOverlay(
                    items = listOf(CanvasItem(1, button)),
                    showTouchControls = true,
                    slotContent = mapOf(
                        "scaledLabel" to {
                            Text("Harbor 24", style = TextStyle(color = Color(0.96f, 0.96f, 0.97f, 1f), size = 24.sp))
                        },
                    ),
                )
            }
            measureInk(out.primitives.rasterizeToPixelMap(360, 110, ground, UiFonts.default(), out.graphicsLayers)).mass
        }
        assertMonotonicScale("CanvasSlot scale", scales, slotMasses)
    }

    private fun assertMonotonicScale(name: String, scales: List<Float>, masses: List<Double>) {
        assertTrue(masses.first() > 15.0, "$name drew no ink")
        for (i in 1 until scales.size) {
            assertTrue(masses[i] > masses[i - 1], "$name at ${scales[i]} did not grow: ${masses[i - 1]} -> ${masses[i]}")
            val prevNorm = masses[i - 1] / (scales[i - 1] * scales[i - 1])
            val currNorm = masses[i] / (scales[i] * scales[i])
            val normDelta = abs(currNorm - prevNorm) / prevNorm
            assertTrue(
                normDelta < 0.025,
                "$name ${scales[i - 1]}->${scales[i]} normalized coverage jumped by ${"%.2f".format(normDelta * 100)}% (> 2.5%)",
            )
        }
    }

    private data class InkMetrics(val mass: Double, val centroidX: Double)

    private fun measureInk(map: PixelMap): InkMetrics {
        val bg = map.sample(0, 0)
        val bgLuma = srgbLuma(bg.r, bg.g, bg.b)
        var mass = 0.0
        var weightedX = 0.0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val p = map.sample(x, y)
                val lum = srgbLuma(p.r, p.g, p.b)
                val ink = (lum - bgLuma).coerceAtLeast(0.0)
                if (ink > 1e-4) {
                    mass += ink
                    weightedX += (x + 0.5) * ink
                }
            }
        }
        return InkMetrics(mass = mass, centroidX = if (mass > 0.0) weightedX / mass else 0.0)
    }

    private fun srgbLuma(r: Int, g: Int, b: Int): Double =
        0.2126 * (r / 255.0) + 0.7152 * (g / 255.0) + 0.0722 * (b / 255.0)

    private fun differingPixels(a: PixelMap, b: PixelMap): Int {
        var diff = 0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.sample(x, y) != b.sample(x, y)) diff += 1
            }
        }
        return diff
    }
}
