/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test

/**
 * Where the cost of one world-following nameplate goes (#1512): 200 anchors of a 70x20 frame, a bar
 * and one line of text, on a 1280x720 surface, every anchor moving each frame or held still.
 *
 * Each phase of the frame is timed and its allocation read on its own (compose, measure, place,
 * resolve positions, paint), and the content is built up one layer at a time (empty box, frame,
 * frame and bar, frame bar and text) so the draw cost of each can be read as a difference. A clean
 * loop without the per-phase reads reports the whole frame, so the two can be checked against each
 * other. It asserts nothing and runs about a minute; it is here so the numbers can be reproduced:
 * `./gradlew :awake:compose:game:desktopTest --tests '*NameplateCostProbe*' -i`.
 */
class NameplateCostProbe {
    /** [fresh] builds the modifier chains inside the composition each frame instead of remembering them. */
    private enum class Content(val frame: Boolean, val bar: Boolean, val text: Boolean, val fresh: Boolean = false) {
        Empty(false, false, false),
        Frame(true, false, false),
        FrameBar(true, true, false),
        FrameBarText(true, true, true),
        FrameBarTextFreshModifiers(true, true, true, fresh = true),
    }

    private class Spot(val index: Int) : AnchorProvider {
        var moving = true
        var tick = 0

        override fun anchor(into: AnchorPoint) {
            val t = if (moving) tick else 0
            into.x = 40f + (index * 37 + t * 3) % 1200
            into.y = 40f + (index * 53 + t * 2) % 640
        }
    }

    private class Result(val phaseNanos: LongArray, val phaseBytes: LongArray, val cleanNanos: Long, val cleanBytes: Long)

    private val painter = Painter()
    private val spots = List(ANCHORS) { Spot(it) }
    private val frameColor = Color(0.2f, 0.2f, 0.25f, 1f)
    private val barColor = Color(0.8f, 0.2f, 0.2f, 1f)

    @Test
    fun nameplateCost() {
        println("NameplateCostProbe machine: ${Runtime.getRuntime().availableProcessors()} cores, ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
        val results = HashMap<String, MutableList<Result>>()
        repeat(RUNS) {
            for (content in Content.entries) {
                for (moving in listOf(true, false)) {
                    results.getOrPut("${content.name}/${if (moving) "moving" else "still"}") { ArrayList() } += run(content, moving)
                }
            }
        }
        println("NameplateCostProbe: $ANCHORS anchors, $RUNS runs of $MEASURED frames after $WARMUP warm-up")
        println("variant | clean ms | clean B/frame | " + PHASES.joinToString(" | ") { "$it ms, B" })
        for ((name, runs) in results) {
            val clean = stat(runs.map { it.cleanNanos / MEASURED / 1e6 })
            val bytes = stat(runs.map { it.cleanBytes / MEASURED.toDouble() })
            val phases = PHASES.indices.joinToString(" | ") { p ->
                val ms = stat(runs.map { it.phaseNanos[p] / MEASURED / 1e6 })
                val b = stat(runs.map { it.phaseBytes[p] / MEASURED.toDouble() })
                "$ms, ${b.replace("\\.\\d+".toRegex(), "")}"
            }
            println("$name | $clean | ${bytes.replace("\\.\\d+".toRegex(), "")} | $phases")
        }
    }

    private fun run(content: Content, moving: Boolean): Result {
        val root = LayoutNode(
            MeasurePolicy { measurables, c ->
                val placed = measurables.map { it.measure(c) }
                layout(c.maxWidth, c.maxHeight) { placed.forEach { it.placeAt(0, 0) } }
            },
        )
        currentContent = content
        spots.forEach { it.moving = moving }
        val viewport = Constraints.fixed(WIDTH, HEIGHT)
        repeat(WARMUP) { step(root, viewport, it) }
        // Clean loop: the whole frame, no per-phase reads.
        var before = bytes()
        var start = System.nanoTime()
        repeat(MEASURED) { step(root, viewport, WARMUP + it) }
        val cleanNanos = System.nanoTime() - start
        val cleanBytes = bytes() - before
        // Phase loop: each phase read on its own.
        val nanos = LongArray(PHASES.size)
        val allocated = LongArray(PHASES.size)
        repeat(MEASURED) {
            spots.forEach { s -> s.tick = WARMUP + MEASURED + it }
            fun phase(index: Int, block: () -> Unit) {
                before = bytes()
                start = System.nanoTime()
                block()
                nanos[index] += System.nanoTime() - start
                allocated[index] += bytes() - before
            }
            phase(0) { composeInto(root) { scene(content) } }
            phase(1) { root.measure(viewport) }
            phase(2) { root.placeAt(0, 0) }
            phase(3) { root.resolveAbsolutePositions() }
            phase(4) { painter.paint(root) }
        }
        return Result(nanos, allocated, cleanNanos, cleanBytes)
    }

    private var currentContent = Content.FrameBarText

    private fun step(root: LayoutNode, viewport: Constraints, tick: Int) {
        spots.forEach { it.tick = tick }
        composeInto(root) { scene(currentContent) }
        root.measure(viewport)
        root.placeAt(0, 0)
        root.resolveAbsolutePositions()
        painter.paint(root)
    }

    context(_: Composer)
    private fun scene(content: Content) {
        WorldAnchorLayout {
            for (spot in spots) {
                val outer = if (content.fresh) Modifier.size(70.dp, 20.dp).worldAnchor(spot) else remember { Modifier.size(70.dp, 20.dp).worldAnchor(spot) }
                val frameMod = if (content.fresh) Modifier.size(70.dp, 20.dp).background(frameColor) else remember { Modifier.size(70.dp, 20.dp).background(frameColor) }
                val barMod = if (content.fresh) Modifier.width(60.dp).height(4.dp).background(barColor) else remember { Modifier.width(60.dp).height(4.dp).background(barColor) }
                Box(outer) {
                    if (content.frame) Box(frameMod)
                    if (content.bar) Box(barMod)
                    if (content.text) Text("Name")
                }
            }
        }
    }

    private fun stat(values: List<Double>): String =
        "%.3f (%.3f to %.3f)".format(values.average(), values.min(), values.max())

    private fun bytes(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        val PHASES = listOf("compose", "measure", "place", "resolve", "paint")
        const val ANCHORS = 200
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val RUNS = 5
        const val WARMUP = 300
        const val MEASURED = 400
    }
}
