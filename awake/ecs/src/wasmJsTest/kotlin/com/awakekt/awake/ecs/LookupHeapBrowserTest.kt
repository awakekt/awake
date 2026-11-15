/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * On Kotlin/Wasm a reified `World.get<T>` built a class wrapper for `T::class` and hashed its name
 * with an allocating `String.hashCode`, 56 bytes a call, and `World.typeId(KClass)` rebound the
 * type's pool through a map that hashed the class's qualified name, 176 bytes. Each per-entity call
 * now allocates nothing once warm, measured as the median heap growth over several passes.
 */
class LookupHeapBrowserTest {
    @Test
    fun aComponentCallAllocatesNothingOnceWarm() {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val lookups = LookupCalls()
        val costs = lookups.calls.map { (name, pass) -> name to bytesPerCall(pass) }
        println("PERF wasm bytes per call: " + costs.joinToString { (name, bytes) -> "$name=$bytes" })
        println("PERF wasm ns per call: " + lookups.calls.joinToString { (name, pass) -> "$name=${nanosPerCall(pass)}" } + " (${lookups.sink})")
        costs.forEach { (name, bytes) -> assertTrue(bytes < MAX_BYTES_PER_CALL, "$name allocates $bytes bytes a call") }
    }

    private fun bytesPerCall(pass: () -> Unit): Double {
        repeat(WARMUP) { pass() }
        val growth = DoubleArray(SAMPLES) {
            val before = collectedHeap()
            pass()
            heapNow() - before
        }
        growth.sort()
        return growth[SAMPLES / 2] / LookupCalls.ENTITIES
    }

    private fun nanosPerCall(pass: () -> Unit): Int {
        val start = now()
        repeat(TIMED_PASSES) { pass() }
        return ((now() - start) * 1_000_000 / (TIMED_PASSES * LookupCalls.ENTITIES)).toInt()
    }

    private companion object {
        const val TIMED_PASSES = 200
        const val WARMUP = 3
        const val SAMPLES = 9

        // Below one byte a call: a lookup that allocates costs tens of bytes, while the heap's own
        // bookkeeping between two readings is a few hundred bytes a pass, a fraction of a byte a call.
        const val MAX_BYTES_PER_CALL = 1.0
    }
}

@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
private external fun heapMeasurable(): Boolean

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun collectedHeap(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun heapNow(): Double

@JsFun("() => performance.now()")
private external fun now(): Double
