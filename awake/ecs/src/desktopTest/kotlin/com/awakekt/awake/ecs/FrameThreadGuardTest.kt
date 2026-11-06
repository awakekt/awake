/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FrameThreadGuardTest {
    private val enabledBefore = FrameThreadGuard.enabled

    @AfterTest
    fun restore() {
        FrameThreadGuard.enabled = enabledBefore
    }

    @Test
    fun aBoundGuardThrowsOffItsThreadAndNotOnIt() {
        FrameThreadGuard.enabled = true
        val guard = FrameThreadGuard().apply { bind() }

        guard.check("on the frame")
        assertFailsWith<IllegalStateException> { offThread { guard.check("off the frame") }?.let { throw it } }
    }

    /** A release build leaves the flag off, so a guard bound there checks nothing. */
    @Test
    fun aGuardBoundWithTheChecksOffChecksNothing() {
        FrameThreadGuard.enabled = false
        val guard = FrameThreadGuard().apply { bind() }

        assertNull(offThread { guard.check("off the frame") })
    }

    @Test
    fun anUnboundGuardChecksNothing() {
        FrameThreadGuard.enabled = true

        assertNull(offThread { FrameThreadGuard().check("anywhere") })
    }

    private fun offThread(block: () -> Unit): Throwable? {
        var thrown: Throwable? = null
        thread { thrown = runCatching(block).exceptionOrNull() }.join()
        return thrown
    }
}
