/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Followers in a host that skips unchanged frames (#1512): planned while placing, a follower whose
 * node moves is moved without composing or measuring, and one that leaves the canvas, comes back or
 * is thinned by a limit composes the frame it changes in. No skipped frame would have drawn anything
 * other than a composed one ([ComposeHost.verifySkippedCompositions]).
 */
class CanvasFollowerSkipGateTest {
    private var shift = 0f
    private var edgeSpeed = EDGE_SPEED

    /** Node `i` sits on a row, moved right by [shift]; one in every [EDGE_EVERY] runs past the canvas's right edge and back. */
    private val resolver = object : CanvasResolver {
        override fun project(target: String, offset: Vec3f): CanvasProjection {
            val index = target.removePrefix("node").toInt()
            val reach = if (index % EDGE_EVERY == 0) shift * edgeSpeed else shift
            return CanvasProjection(START + (index % COLUMNS) * SPACING + reach, START + (index / COLUMNS) * SPACING, distance = 1f + index)
        }
    }

    private val items = List(FOLLOWERS) { index ->
        CanvasItem(
            index + 1,
            CanvasElement().apply {
                kind = CanvasElementKind.Bar
                follow = "node$index"
                width = 40f
                height = 6f
                value = 0.5f
            },
        )
    }

    private fun host(verify: Boolean) = ComposeHost().apply {
        skipUnchangedComposition = true
        skipUnchangedMeasure = true
        retainUnchangedDraw = true
        verifySkippedCompositions = verify
    }

    private fun ComposeHost.frame(limit: Int = 0) = frame(FrameInput(WIDTH, HEIGHT)) {
        CanvasOverlay(items, resolver = resolver, followLimits = if (limit > 0) listOf(CanvasFollowLimit(max = limit)) else emptyList())
    }

    @Test
    fun movingFollowersComposeNothing() {
        // Every node moves, none far enough to leave the canvas: nothing drawn changes but positions.
        edgeSpeed = 1f
        val host = host(verify = false)
        repeat(SETTLE) { host.frame() }
        var composed = 0
        repeat(FRAMES) {
            shift = (it % SWAY).toFloat()
            host.frame()
            if (host.frameCounts.composed || host.frameCounts.measured) composed++
        }
        println("CanvasFollowerSkipGate: $FOLLOWERS followers moving, composed or measured $composed of $FRAMES frames")
        assertEquals(0, composed, "composed or measured $composed of $FRAMES frames")
    }

    @Test
    fun noSkippedFrameIsStaleAsFollowersMoveLeaveAndAreThinned() {
        val host = host(verify = true)
        repeat(SETTLE) { host.frame() }
        repeat(FRAMES) {
            shift = (it % SWAY).toFloat()
            // The limit is a plain argument, which no host observes: a change to it is reported, as
            // the scene's own limits are by their store.
            if (it % (LIMIT_EVERY / 2) == 0) host.invalidate()
            host.frame(limit = if (it % LIMIT_EVERY < LIMIT_EVERY / 2) 0 else FOLLOWERS / 2)
        }
        println("CanvasFollowerSkipGate: ${host.staleSkips} stale skips over $FRAMES frames")
        assertEquals(0, host.staleSkips, "a skipped frame would have drawn stale followers")
    }

    private companion object {
        const val FOLLOWERS = 60
        const val COLUMNS = 10
        const val START = 20f
        const val SPACING = 50f
        const val EDGE_EVERY = 7
        const val EDGE_SPEED = 30f
        const val WIDTH = 640
        const val HEIGHT = 360
        const val SETTLE = 30
        const val FRAMES = 200
        const val SWAY = 40
        const val LIMIT_EVERY = 50
    }
}
