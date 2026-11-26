/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.draw.scale
import com.awakekt.awake.compose.ui.draw.zIndex
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.unit.IntOffset
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.TranslatePush
import com.awakekt.awake.core.graphics2d.flattened
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Retained draw (#1512 stage e): a host that replays unchanged subtrees draws exactly what a host
 * that paints every node every frame draws, on the same frame, including after a parent moves, and
 * it does replay, or the comparison proves nothing.
 */
class RetainedDrawTest {
    private val retaining = ComposeHost().also {
        it.skipUnchangedComposition = true
        it.skipUnchangedMeasure = true
        it.retainUnchangedDraw = true
    }

    // Off explicitly, so the reference composes, measures and draws every frame whatever the defaults.
    private val reference = ComposeHost().also {
        it.skipUnchangedComposition = false
        it.skipUnchangedMeasure = false
        it.retainUnchangedDraw = false
    }

    /** Runs [content] on both hosts and checks they drew the same thing. */
    private fun frame(label: String, content: context(Composer) () -> Unit): FrameOutput {
        val input = FrameInput(1600, 900, deltaSeconds = 0f)
        val expected = reference.frame(input, content)
        val actual = retaining.frame(input, content)
        // A moved subtree replays inside a translate range; drawn, it is what a fresh paint draws.
        assertEquals(expected.primitives, actual.primitives.flattened(), "the retaining host drew something else at $label")
        assertEquals(expected.graphicsLayers, actual.graphicsLayers, "the retaining host layered something else at $label")
        return actual
    }

    context(_: Composer)
    private fun plate(index: Int, x: () -> Int, y: () -> Int) {
        Box(Modifier.offset { IntOffset(x(), y()) }.size(70.dp, 20.dp).background(Color(0.1f, 0.1f, 0.1f))) {
            Box(Modifier.size(36.dp, 4.dp).background(Color(0.2f, 0.8f, 0.2f)))
            Text("Player $index", Modifier.padding(top = 5.dp))
        }
    }

    @Test
    fun aStillFrameReplaysEverySubtree() {
        val content: context(Composer)
        () -> Unit = { Box { for (i in 0 until 5) plate(i, { i * 80 }, { 10 }) } }
        frame("first", content)
        frame("second", content)
        val before = retaining.retainedDraws
        frame("still", content)
        assertTrue(retaining.retainedDraws > before, "a still frame replayed nothing")
    }

    @Test
    fun aMovedParentReplaysItsSubtreeTranslated() {
        var x = 100
        val content: context(Composer)
        () -> Unit = { Box { plate(0, { x }, { 40 }) } }
        frame("first", content)
        frame("second", content)
        var replayed = 0
        var ranged = 0
        // Across whole-pixel steps and the 128, 256 and 512 exponent boundaries, both ways.
        val steps = listOf(3, 3, 3, 7, 40, 90, 150, 300, -2, -260, -300, 1, 1)
        for ((i, step) in steps.withIndex()) {
            x += step
            val before = retaining.retainedDraws
            val output = frame("move $i to $x", content)
            if (retaining.retainedDraws > before) replayed++
            if (output.primitives.any { it is TranslatePush }) ranged++
        }
        assertTrue(replayed >= steps.size / 2, "only $replayed of ${steps.size} moves replayed")
        assertEquals(replayed, ranged, "each moved replay is a translate range, not a copy")
    }

    @Test
    fun aChildMovingInsideAStillParentRepaintsTheParentAndReplaysItsOtherChildren() {
        var dx = 0
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.size(300.dp, 100.dp).background(Color(0f, 0f, 0.3f))) {
                Box(Modifier.offset { IntOffset(dx, 0) }.size(20.dp).background(Color(1f, 0f, 0f)))
                Text("still label", Modifier.padding(top = 40.dp))
            }
        }
        frame("first", content)
        frame("second", content)
        for (i in 1..6) {
            dx = i * 11
            val before = retaining.retainedDraws
            frame("child moved to $dx", content)
            assertTrue(retaining.retainedDraws > before, "the moved child and the still label should replay")
        }
    }

    @Test
    fun aStateReadWhileDrawingRepaintsThatNodeOnly() {
        val shade = mutableStateOf(0.1f)
        val content: context(Composer)
        () -> Unit = {
            Box {
                Box(Modifier.size(20.dp).drawBehind { drawRect(0f, 0f, 20f, 20f, Color(shade.value, 0f, 0f)) })
                plate(1, { 40 }, { 0 })
            }
        }
        frame("first", content)
        frame("second", content)
        for (i in 1..4) {
            shade.value = 0.1f + i * 0.2f
            val before = retaining.retainedDraws
            val output = frame("shade $i", content)
            assertEquals(shade.value, (output.primitives.first() as com.awakekt.awake.core.graphics2d.UiDrawPrimitive.Quad).color.r)
            assertTrue(retaining.retainedDraws > before, "the plate beside it should still replay")
        }
    }

    @Test
    fun textAtFractionalPositionsMovesExactlyOrRepaints() {
        var x = 0
        val content: context(Composer)
        () -> Unit = {
            // Large enough for the distance field, whose advances inside a line stay fractional; spaced
            // by a fraction too. Pixel glyphs would land on whole pixels and prove nothing here.
            Box(Modifier.offset { IntOffset(x, x / 3) }) {
                Text("fractional advances: iiiWWWmmm", style = TextStyle(size = 41.3f.sp, letterSpacing = 0.37f.sp))
            }
        }
        frame("first", content)
        val glyphs = retaining.frame(FrameInput(1600, 900, deltaSeconds = 0f), content).primitives.flattened()
        assertTrue(glyphs.any { it is com.awakekt.awake.core.graphics2d.UiDrawPrimitive.Glyph && it.x != kotlin.math.floor(it.x) }, "no glyph sits between pixels")
        reference.frame(FrameInput(1600, 900, deltaSeconds = 0f), content)
        for (i in 0 until 120) {
            x = (i * 97) % 1500 - 20
            frame("x=$x", content)
        }
    }

    /**
     * Text holds the distance field for a few passes after a scale or a size change ends, counting the
     * passes in plain fields. A replayed draw or a skipped measure would freeze it mid-settle.
     */
    @Test
    fun textSettlingAfterAScaleOrSizeChangeKeepsMatchingAFreshPaint() {
        val scale = mutableStateOf(1.25f)
        val size = mutableStateOf(14f)
        val content: context(Composer)
        () -> Unit = {
            Box {
                Box(Modifier.scale(scale.value)) { Text("settling after a scale") }
                Text("settling after a size", Modifier.padding(top = 40.dp), style = TextStyle(size = size.value.sp))
            }
        }
        frame("scaled", content)
        frame("scaled again", content)
        scale.value = 1f
        size.value = 18f
        for (i in 0 until 14) frame("settling $i", content)
        val before = retaining.retainedDraws
        frame("settled", content)
        assertTrue(retaining.retainedDraws > before, "settled text should replay again")
    }

    @Test
    fun randomTreesDrawWhatAFreshPaintDraws() {
        for (seed in 0 until SEEDS) {
            val tree = RandomTree(Random(seed))
            // A new tree in plain fields: tell the skipping host, as the switch's contract asks.
            retaining.invalidate()
            frame("seed $seed first") { tree.ui() }
            for (f in 0 until FRAMES) {
                tree.step()
                frame("seed $seed frame $f") { tree.ui() }
            }
        }
        assertTrue(retaining.retainedDraws > SEEDS * FRAMES, "the random trees replayed ${retaining.retainedDraws} subtrees")
    }

    @Test
    fun nothingIsReplayedUnlessMeasureIsSkippedToo() {
        retaining.skipUnchangedMeasure = false
        val content: context(Composer)
        () -> Unit = { plate(0, { 10 }, { 10 }) }
        repeat(4) { frame("frame $it", content) }
        assertEquals(0, retaining.retainedDraws)
    }

    @Test
    fun retainUnchangedSubtreesReplaysStaticSiblingsAcrossComposedFrames() {
        val host = ComposeHost().also {
            it.skipUnchangedComposition = false
            it.skipUnchangedMeasure = false
            it.retainUnchangedDraw = true
            it.retainUnchangedSubtrees = true
        }
        val ref = ComposeHost()
        var counter = 0
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.size(600.dp, 200.dp)) {
                Box(Modifier.size(120.dp, 24.dp).background(Color(0.2f + (counter % 5) * 0.1f, 0.1f, 0.1f))) {
                    Text("HP ${100 + counter}")
                }
                for (i in 0 until 4) {
                    Box(Modifier.padding(start = (140 + i * 100).dp).size(90.dp, 40.dp).background(Color(0.1f, 0.2f, 0.3f))) {
                        Text("Panel $i")
                    }
                }
            }
        }
        val input = FrameInput(1600, 900, deltaSeconds = 0f)
        repeat(2) {
            counter++
            assertEquals(ref.frame(input, content).primitives, host.frame(input, content).primitives.flattened())
        }
        val before = host.retainedDraws
        repeat(5) {
            counter++
            val expected = ref.frame(input, content)
            val actual = host.frame(input, content)
            assertEquals(expected.primitives, actual.primitives.flattened(), "composed frame $counter diverged")
            assertEquals(expected.graphicsLayers, actual.graphicsLayers)
        }
        assertTrue(host.retainedDraws >= before + 20, "expected static sibling subtrees to replay across composed frames, got ${host.retainedDraws - before}")
    }

    @Test
    fun retainUnchangedSubtreesRepaintsChangedModifiersOrderZIndexAndDensity() {
        val host = ComposeHost().also {
            it.skipUnchangedComposition = false
            it.skipUnchangedMeasure = false
            it.retainUnchangedDraw = true
            it.retainUnchangedSubtrees = true
        }
        val ref = ComposeHost()
        var bg = Color(1f, 0f, 0f)
        var boxAlpha = 1f
        var boxScale = 1f
        var digits = "100"
        var swap = false
        var topZ = 0f
        var drawColor = Color(0f, 1f, 0f)
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.size(300.dp, 200.dp)) {
                val first = if (swap) 1 else 0
                val second = if (swap) 0 else 1
                for (id in listOf(first, second)) {
                    key(id) {
                        val z = if (id == 0) topZ else 0.5f
                        val mod = if (id == 0) {
                            Modifier.size(80.dp, 40.dp).scale(boxScale).alpha(boxAlpha)
                                .zIndex(z)
                                .background(bg)
                                .drawBehind { drawRect(0f, 0f, 12f, 12f, drawColor) }
                        } else {
                            Modifier.padding(start = 10.dp).size(80.dp, 40.dp)
                                .zIndex(z)
                                .background(Color(0.2f, 0.2f, 0.8f))
                        }
                        Box(mod) { Text(if (id == 0) digits else "Fixed") }
                    }
                }
            }
        }
        val input = FrameInput(800, 600, deltaSeconds = 0f)
        fun step(label: String) {
            val expected = ref.frame(input, content)
            val actual = host.frame(input, content)
            assertEquals(expected.primitives, actual.primitives.flattened(), "diverged at $label")
        }
        step("initial")
        step("warm")
        bg = Color(0f, 0f, 1f); step("bg color")
        boxAlpha = 0.4f; step("alpha")
        boxScale = 1.5f; step("scale")
        digits = "200"; step("same-width text")
        swap = true; step("child reorder")
        topZ = 2f; step("zIndex")
        drawColor = Color(1f, 1f, 0f); step("drawBehind lambda")
        host.density = 2f; ref.density = 2f; step("density change")
    }

    @Test
    fun retainUnchangedSubtreesRandomComposedTreesMatchFreshPaint() {
        val host = ComposeHost().also {
            it.skipUnchangedComposition = false
            it.skipUnchangedMeasure = false
            it.retainUnchangedDraw = true
            it.retainUnchangedSubtrees = true
        }
        val ref = ComposeHost()
        val input = FrameInput(1600, 900, deltaSeconds = 0f)
        for (seed in 0 until SEEDS) {
            val tree = RandomComposedTree(Random(seed + 1000))
            assertEquals(ref.frame(input) { tree.ui() }.primitives, host.frame(input) { tree.ui() }.primitives.flattened())
            for (f in 0 until FRAMES) {
                tree.step()
                val expected = ref.frame(input) { tree.ui() }
                val actual = host.frame(input) { tree.ui() }
                assertEquals(expected.primitives, actual.primitives.flattened(), "composed random seed=$seed frame=$f diverged")
                assertEquals(expected.graphicsLayers, actual.graphicsLayers, "composed random layers seed=$seed frame=$f diverged")
            }
        }
        assertTrue(host.retainedDraws > SEEDS * FRAMES, "expected subtree replays during composed frames, got ${host.retainedDraws}")
    }

    private class RandomComposedTree(private val random: Random) {
        private val count = 1 + random.nextInt(MAX_NODES)
        private val parents = IntArray(count) { if (it == 0) -1 else random.nextInt(it) }
        private val kinds = IntArray(count) { random.nextInt(KINDS) }
        private val colors = Array(count) { Color(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
        private val alphas = FloatArray(count) { 0.4f + random.nextFloat() * 0.6f }
        private val labels = Array(count) { "n$it" }
        private val zOrders = FloatArray(count) { 0f }

        fun step() {
            val i = random.nextInt(count)
            when (random.nextInt(4)) {
                0 -> colors[i] = Color(random.nextFloat(), random.nextFloat(), random.nextFloat())
                1 -> alphas[i] = if (random.nextBoolean()) 1f else 0.5f
                2 -> labels[i] = "n$i:${random.nextInt(100)}"
                else -> zOrders[i] = random.nextInt(3).toFloat()
            }
        }

        context(_: Composer)
        fun ui() = node(0)

        context(_: Composer)
        private fun node(i: Int) {
            val base = Modifier.size((24 + i * 7 % 80).dp, (16 + i * 5 % 50).dp)
                .zIndex(zOrders[i])
            val modifier = when (kinds[i]) {
                0 -> base.background(colors[i])
                1 -> base.alpha(alphas[i]).background(colors[i])
                2 -> base.clipToBounds().background(colors[i])
                3 -> base.drawBehind { drawRect(0f, 0f, 8f, 8f, colors[i]) }
                4 -> base.graphicsLayer(alpha = alphas[i])
                else -> base.padding(3.dp)
            }
            Box(modifier) {
                if (kinds[i] % 2 == 0) Text(labels[i])
                for (c in 0 until count) if (parents[c] == i) node(c)
            }
        }
    }

    /**
     * A random tree of boxes: some move by a placement offset, some read a colour while drawing, some
     * clip, dim, layer or carry text. [step] moves a few, rewrites a few colours, or does nothing.
     */
    private class RandomTree(private val random: Random) {
        private val count = 1 + random.nextInt(MAX_NODES)
        private val parents = IntArray(count) { if (it == 0) -1 else random.nextInt(it) }
        private val kinds = IntArray(count) { random.nextInt(KINDS) }
        private val xs = IntArray(count) { random.nextInt(400) }
        private val ys = IntArray(count) { random.nextInt(300) }
        private val shades = List(count) { mutableStateOf(random.nextFloat()) }

        fun step() {
            when (random.nextInt(4)) {
                0 -> Unit
                1 -> repeat(1 + random.nextInt(3)) { val i = random.nextInt(count); xs[i] += random.nextInt(-300, 300) }
                2 -> repeat(1 + random.nextInt(3)) { val i = random.nextInt(count); ys[i] += random.nextInt(-5, 6) }
                else -> shades[random.nextInt(count)].value = random.nextFloat()
            }
        }

        context(_: Composer)
        fun ui() = node(0)

        context(_: Composer)
        private fun node(i: Int) {
            val shade = shades[i]
            val base = Modifier.offset { IntOffset(xs[i], ys[i]) }.size((20 + i * 7 % 90).dp, (14 + i * 5 % 60).dp)
            val modifier = when (kinds[i]) {
                0 -> base.background(Color(0.2f, 0.3f, 0.4f))
                1 -> base.drawBehind { drawRect(0f, 0f, 9f, 9f, Color(shade.value, 0.5f, 0.5f)) }
                2 -> base.clipToBounds().background(Color(0.5f, 0.1f, 0.1f))
                3 -> base.alpha(0.5f).background(Color(0.1f, 0.5f, 0.1f))
                4 -> base.graphicsLayer { translationX = shade.value * 3f }
                else -> base.padding(3.dp)
            }
            Box(modifier) {
                if (kinds[i] % 2 == 0) Text("n$i ${i * 0.37f}")
                for (c in 0 until count) if (parents[c] == i) node(c)
            }
        }
    }

    private companion object {
        const val SEEDS = 40
        const val FRAMES = 25
        const val MAX_NODES = 14
        const val KINDS = 6
    }
}
