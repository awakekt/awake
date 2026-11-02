/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [awaitDeviceRelease] over scripted registry samples and a fake clock, without a GPU. The samples
 * replay what wgpu-native shows while another thread runs a device's last release: the device is
 * unregistered first and the queue it owns a moment later (#1263).
 */
class WgpuDeviceReleaseTest {
    @Test
    fun waitsPastTheDeviceForTheQueueItsReleaseUnregistersLast() {
        val samples = Samples(mapOf("devices" to 1L, "queues" to 1L), mapOf("queues" to 1L), emptyMap())
        val clock = FakeClock()

        val live = awaitDeviceRelease(TIMEOUT_NANOS, clock::now, clock::tick, samples::next)

        assertEquals(emptyMap(), live, "A queue sampled after its device is a release still running, not a leak")
        assertEquals(3, samples.taken)
    }

    @Test
    fun reportsAQueueStillAliveAtTheDeadline() {
        val samples = Samples(mapOf("queues" to 1L))
        val clock = FakeClock()

        val live = awaitDeviceRelease(TIMEOUT_NANOS, clock::now, clock::tick, samples::next)

        assertEquals(mapOf("queues" to 1L), live, "A queue handle nobody releases must still be reported")
        assertEquals(TIMEOUT_NANOS, clock.now(), "The wait ends at its deadline")
    }

    @Test
    fun reportsOtherLeftoversWithoutWaiting() {
        val samples = Samples(mapOf("textures" to 2L))
        val clock = FakeClock()

        val live = awaitDeviceRelease(TIMEOUT_NANOS, clock::now, clock::tick, samples::next)

        assertEquals(mapOf("textures" to 2L), live)
        assertEquals(1, samples.taken, "Only the device's own release is waited for")
    }

    /** Hands out [sequence] in order, then repeats its last sample. */
    private class Samples(vararg sequence: Map<String, Long>) {
        private val sequence = sequence.toList()
        var taken = 0
            private set

        fun next(): Map<String, Long> = sequence[minOf(taken++, sequence.lastIndex)]
    }

    /** A clock each pause moves on by one millisecond. */
    private class FakeClock {
        private var nanos = 0L

        fun now(): Long = nanos

        fun tick() {
            nanos += NANOS_PER_MILLI
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val TIMEOUT_NANOS = 5 * NANOS_PER_MILLI
    }
}
