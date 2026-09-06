/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.heroicons.icon.HeroIcons
import com.awakekt.awake.render.passes2d.UiRunCoalescer
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnIcon
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A ratchet on what one frame of ordinary UI costs, so a performance regression fails a test
 * instead of being noticed in an app weeks later.
 *
 * **Counts, not milliseconds.** Wall-clock is noisy on a developer's machine and runs through a
 * software rasteriser in CI, which is why `RendererHeadlessFrameTimingTest` refuses to assert on
 * time at all. These numbers are exact integers for a given frame, and they are what the expensive
 * work is proportional to: the backend writes every vertex into mapped GPU memory, once per run
 * per frame. `UiUploadCostBenchmark` carries the one loose time bound, for the kind of regression
 * a count cannot see.
 *
 * **Its own fixture, deliberately.** An earlier version measured Studio's shell, which made the
 * ceilings hostage to that app's content: adding a panel would fail this, the ceiling would be
 * raised to let it pass, and after a few rounds the ratchet would mean nothing. The scene below is
 * fixed here, so only an engine change moves these numbers. It is built from real shadcn widgets
 * rather than synthetic primitives, because the costs worth guarding live in real geometry --
 * tessellated icons, stroked borders, and text.
 *
 * Every regression this session would have failed here: anti-aliased fringes collapsing to a fixed
 * four segments per arc moved the vertex count 36%; losing the icon or border geometry caches
 * moved it further; and a draw run too small to hold a whole icon re-split every one of them,
 * every frame, for 3.2 ms against 0.2.
 *
 * Raise a ceiling only with the measurement that justifies it, as `ComposeFrameProbe`'s byte
 * ratchet is maintained. Lower it when a change earns the room.
 */
class UiFrameCostRatchetTest {

    @Test
    fun oneFrameOfOrdinaryUiStaysWithinItsGeometryBudget() {
        val primitives = frame()

        val meshVertices = primitives.filterIsInstance<UiDrawPrimitive.Mesh>()
            .sumOf { it.mesh.vertices.size }
        val runs = UiRunCoalescer.coalesce(primitives, MAX_QUADS_PER_RUN).size

        // Printed as well as asserted: a ceiling is maintained by reading the real number, and
        // hunting it down from a failure message is how ratchets get raised carelessly.
        println("PERF ui-frame meshVertices=$meshVertices runs=$runs")

        assertTrue(
            meshVertices <= MAX_MESH_VERTICES,
            "this frame tessellates $meshVertices mesh vertices, over the $MAX_MESH_VERTICES " +
                "ceiling -- every one is written into mapped GPU memory each frame",
        )
        assertTrue(
            runs <= MAX_DRAW_RUNS,
            "this frame coalesces into $runs draw runs, over the $MAX_DRAW_RUNS ceiling -- each " +
                "run is its own buffer upload and draw call",
        )
    }

    /**
     * Bordered cards, tinted icons and text: the three shapes whose geometry actually costs
     * something, in the proportions a real screen has them.
     */
    private fun frame(): List<UiDrawPrimitive> = composeFrame(FRAME_WIDTH, FRAME_HEIGHT) {
        provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
            Column {
                repeat(CARDS) {
                    ShadcnCard(Modifier.size(CARD_WIDTH.dp, CARD_HEIGHT.dp)) {
                        Row {
                            ShadcnIcon(HeroIcons.Outline24.arrowPath)
                            ShadcnText("Ordinary label text")
                            ShadcnButton("Action", variant = ShadcnButtonVariant.Outline)
                        }
                    }
                }
            }
        }
    }.primitives

    private companion object {
        const val FRAME_WIDTH = 900
        const val FRAME_HEIGHT = 700
        const val CARDS = 6
        const val CARD_WIDTH = 420
        const val CARD_HEIGHT = 96
        const val MAX_QUADS_PER_RUN = 1024

        // Measured 2026-08-31 at 56,184 vertices over 42 runs. Headroom is deliberately narrow:
        // this is a ratchet, not a limit, and a change needing more room should say why.
        //
        // Raised from 45,000 (41,016 measured 2026-08-29): `isConvex` used to call a stroke ring
        // convex, and every icon here was centroid-fanned into a solid blob for ~2.5k vertices
        // less each. Correcting it moved this work onto the scanline triangulator, which is what
        // an outline actually costs. The cheaper number was measuring the wrong picture.
        const val MAX_MESH_VERTICES = 58_000
        const val MAX_DRAW_RUNS = 46
    }
}
