/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test

/**
 * Finds the bytes `NameplateCostProbe` does not reproduce (#1512). The earlier probe behind the
 * 684 KB figure ran through `ComposeHost.frame`, built its modifier chains and `Color`s inside the
 * composition every frame, and built each label as `"Player $i"` every frame. Each of those is
 * switched on or off here, one at a time, on 200 anchors of the same content, so the gap can be
 * assigned to them by difference. It prints and asserts nothing.
 */
class NameplateGapProbe {
    private class Variant(
        val name: String,
        val freshModifiers: Boolean = false,
        val freshColors: Boolean = false,
        val dynamicText: Boolean = false,
        val content: Boolean = true,
        val anchors: Boolean = true,
    )

    private val variants = listOf(
        Variant("original: fresh modifiers, colors, label", true, true, true),
        Variant("label built once", true, true, false),
        Variant("colors built once", true, false, true),
        Variant("modifiers remembered", false, true, true),
        Variant("all built once (clean content)"),
        Variant("empty anchored boxes", content = false),
        Variant("host, no children", content = false, anchors = false),
    )

    private class Spot(val index: Int) : AnchorProvider {
        var tick = 0

        override fun anchor(into: AnchorPoint) {
            into.x = 40f + (index * 37 + tick * 3) % 1200
            into.y = 40f + (index * 53 + tick * 2) % 640
        }
    }

    private val spots = List(COUNT) { Spot(it) }
    private val labels = List(COUNT) { "Player $it" }
    private val frameColor = Color(0.1f, 0.1f, 0.1f)
    private val barColor = Color(0.2f, 0.8f, 0.2f)

    @Test
    fun nameplateGap() {
        println("NameplateGapProbe: $COUNT anchors, $RUNS runs of $MEASURED frames after $WARMUP, ${Runtime.getRuntime().availableProcessors()} cores")
        val results = variants.associateWith { ArrayList<Pair<Double, Long>>() }
        repeat(RUNS) { for (v in variants) results.getValue(v) += run(v) }
        for ((v, runs) in results) {
            val ms = runs.map { it.first }
            val bytes = runs.map { it.second }
            println("NameplateGapProbe | ${v.name} | ${"%.3f".format(ms.average())} ms (${"%.3f".format(ms.min())} to ${"%.3f".format(ms.max())}) | ${bytes.average().toLong()} B (${bytes.min()} to ${bytes.max()})")
        }
    }

    private fun run(v: Variant): Pair<Double, Long> {
        val host = ComposeHost()
        var tick = 0
        fun frame() {
            tick++
            spots.forEach { it.tick = tick }
            host.frame(FrameInput(WIDTH, HEIGHT)) { scene(v) }
        }
        repeat(WARMUP) { frame() }
        val before = bytes()
        val start = System.nanoTime()
        repeat(MEASURED) { frame() }
        return ((System.nanoTime() - start) / 1e6 / MEASURED) to ((bytes() - before) / MEASURED)
    }

    context(_: Composer)
    private fun scene(v: Variant) {
        WorldAnchorLayout(Modifier.fillMaxSize()) {
            if (!v.anchors) return@WorldAnchorLayout
            for (i in 0 until COUNT) {
                val spot = spots[i]
                val outer = if (v.freshModifiers) Modifier.worldAnchor(spot).size(70.dp, 20.dp) else remember { Modifier.worldAnchor(spot).size(70.dp, 20.dp) }
                if (!v.content) {
                    Box(outer)
                    continue
                }
                val frameFill = if (v.freshColors) Color(0.1f, 0.1f, 0.1f) else frameColor
                val barFill = if (v.freshColors) Color(0.2f, 0.8f, 0.2f) else barColor
                val barMod = if (v.freshModifiers) Modifier.size(36.dp, 4.dp).background(barFill) else remember { Modifier.size(36.dp, 4.dp).background(barFill) }
                Box(if (v.freshModifiers) outer.background(frameFill) else remember { outer.background(frameFill) }) {
                    Box(barMod)
                    Text(if (v.dynamicText) "Player $i" else labels[i])
                }
            }
        }
    }

    private fun bytes(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val COUNT = 200
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val RUNS = 5
        const val WARMUP = 300
        const val MEASURED = 400
    }
}
