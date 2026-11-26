/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Verifies Android [decodeDispatcher] concurrency bounds and desktop-compat JVM bridge. */
class AndroidDecodeDispatcherTest {
    /** Ensures Android caps concurrent heavy decodes at 2 to prevent decode-burst OOMs. */
    @Test
    fun decodeDispatcherCapsConcurrencyAtTwoOnAndroid() = runBlocking {
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val jobs = List(12) {
            launch(decodeDispatcher) {
                val now = active.incrementAndGet()
                peak.accumulateAndGet(now, ::maxOf)
                Thread.sleep(15)
                active.decrementAndGet()
            }
        }
        jobs.joinAll()
        assertTrue(peak.get() in 1..2, "Android decodeDispatcher peak concurrency must stay <= 2, got ${peak.get()}")
    }

    /** Ensures pure-JVM modules compiled against io-desktop resolve [decodeDispatcher] on Android. */
    @Test
    fun desktopCompatBridgeReturnsDecodeDispatcher() {
        val compatClass = Class.forName("com.awakekt.awake.core.io.IoDispatcher_desktopKt")
        val getter = compatClass.getMethod("getDecodeDispatcher")
        assertSame(decodeDispatcher, getter.invoke(null))
    }
}
