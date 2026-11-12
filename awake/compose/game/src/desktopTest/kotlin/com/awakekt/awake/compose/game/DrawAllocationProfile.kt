/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import java.nio.file.Files
import jdk.jfr.Recording
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordingFile
import kotlin.test.Test

/**
 * Names what `Painter.paint` allocates (#1512): a laid-out tree of 200 nameplates is painted over and
 * over under Java Flight Recorder's allocation sampling, and the sampled bytes are grouped by class and
 * by the first engine frame that allocated them. It prints and asserts nothing. Run with escape
 * analysis off (the module's test task sets it), so short-lived objects are counted as on Android.
 */
class DrawAllocationProfile {
    private val painter = Painter()
    private val fill = Color(0.1f, 0.1f, 0.1f)

    @Test
    fun whatPaintAllocates() {
        for ((name, bar, text) in listOf(Triple("one background box per nameplate", false, false), Triple("frame, bar and text", true, true))) {
            profile(name, bar, text)
        }
    }

    private fun profile(name: String, bar: Boolean, text: Boolean) {
        val root = LayoutNode(MeasurePolicy { measurables, c -> val placed = measurables.map { it.measure(c) }; layout(c.maxWidth, c.maxHeight) { placed.forEach { it.placeAt(0, 0) } } })
        composeInto(root) { scene(bar, text) }
        root.layoutTree(Constraints.fixed(1280, 720))
        repeat(WARMUP) { painter.paint(root) }
        val file = Files.createTempFile("draw-alloc", ".jfr")
        Recording().use { recording ->
            recording.enable("jdk.ObjectAllocationSample").withStackTrace().with("throttle", "100000/s")
            recording.start()
            repeat(FRAMES) { painter.paint(root) }
            recording.stop()
            recording.dump(file)
        }
        val byClass = HashMap<String, Double>()
        val bySite = HashMap<String, Double>()
        var total = 0.0
        for (event in RecordingFile.readAllEvents(file)) {
            val weight = event.getLong("weight").toDouble()
            total += weight
            byClass.merge(event.getClass("objectClass").name, weight, Double::plus)
            bySite.merge(site(event), weight, Double::plus)
        }
        Files.delete(file)
        println("DrawAllocationProfile [$name]: sampled ${(total / FRAMES).toLong()} B/frame over $FRAMES frames, $NAMEPLATES nameplates")
        byClass.entries.sortedByDescending { it.value }.take(TOP).forEach { println("DrawAllocationProfile [$name] class | ${(it.value / FRAMES / NAMEPLATES).toLong()} B/nameplate | ${it.key}") }
        bySite.entries.sortedByDescending { it.value }.take(TOP).forEach { println("DrawAllocationProfile [$name] site | ${(it.value / FRAMES / NAMEPLATES).toLong()} B/nameplate | ${it.key}") }
    }

    /** The first frame inside the engine's own packages, which is the code that asked for the allocation, plus the class it allocated. */
    private fun site(event: RecordedEvent): String {
        val frames = event.stackTrace?.frames.orEmpty()
        val own = frames.firstOrNull { it.method.type.name.startsWith("com.awakekt") }
        val where = own?.let { "${it.method.type.name.substringAfterLast('.')}.${it.method.name}:${it.lineNumber}" } ?: "(no engine frame)"
        return "$where allocates ${event.getClass("objectClass").name.substringAfterLast('.')}"
    }

    context(_: Composer)
    private fun scene(bar: Boolean, text: Boolean) {
        for (i in 0 until NAMEPLATES) {
            val frameMod = remember { Modifier.size(70.dp, 20.dp).background(fill) }
            val barMod = remember { Modifier.size(36.dp, 4.dp).background(fill) }
            Box(frameMod) {
                if (bar) Box(barMod)
                if (text) Text("Player $i")
            }
        }
    }

    private companion object {
        const val NAMEPLATES = 200
        const val WARMUP = 500
        const val FRAMES = 4000
        const val TOP = 12
    }
}
