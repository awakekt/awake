/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ui.canvas.CanvasBinding
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
private external fun bindHeapMeasurable(): Boolean

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun bindCollectedHeap(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun bindHeapNow(): Double

@JsFun("() => performance.now()")
private external fun bindNowMillis(): Double

/**
 * The bound-field poll's cost in the browser (#1512): 100 bound elements, two fields each, polled as
 * a skipping host polls a frame, against the JSON export of the same components it replaced.
 */
class SceneCanvasBindPollBrowserTest {
    @Test
    fun pollingAHundredBoundElementsCostsAFewKilobytesAFrame() {
        assertTrue(bindHeapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val hud = BoundHud(units = UNITS)
        val named = CanvasNodeNames(hud.world)
        val bound = BoundFields(named, CanvasFieldReader(hud.world, HUD_BINDINGS))
        bound.begin()
        repeat(UNITS) { bound.add(CanvasBinding("Unit$it", value = "health.current", max = "health.max")) }
        bound.end()
        val poll = measure { named.nextFrame(); bound.changed() }
        val json = Json { encodeDefaults = true; allowSpecialFloatingPointValues = true }
        val exports = measure {
            for (health in hud.health) json.encodeToJsonElement(SceneHudHealth.serializer(), SceneHudHealth(health.current, health.max))
        }
        println(
            "SceneCanvasBindPoll (browser): 100 bound elements, 2 fields each: poll ${poll.first} B and ${poll.second} us a frame; " +
                "the JSON export it replaced ${exports.first} B and ${exports.second} us",
        )
        assertTrue(poll.first <= BUDGET, "a poll grew the heap by ${poll.first} B a frame, over $BUDGET")
    }

    /** Median heap growth and time per call of [frame], after a warm-up. */
    private fun measure(frame: () -> Unit): Pair<Double, Double> {
        repeat(WARMUP) { frame() }
        val micros = DoubleArray(SAMPLES) {
            val start = bindNowMillis()
            repeat(FRAMES) { frame() }
            (bindNowMillis() - start) * MICROS_PER_MILLI / FRAMES
        }.sorted()
        val growth = DoubleArray(SAMPLES) {
            val before = bindCollectedHeap()
            repeat(FRAMES) { frame() }
            (bindHeapNow() - before) / FRAMES
        }.sorted()
        return growth[SAMPLES / 2] to micros[SAMPLES / 2]
    }

    private companion object {
        const val UNITS = 100
        const val WARMUP = 100
        const val FRAMES = 50
        const val SAMPLES = 9
        const val MICROS_PER_MILLI = 1_000.0

        /** A few KB: the exported components, against tens of KB for their JSON. */
        const val BUDGET = 8_192.0
    }
}
