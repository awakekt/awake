/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import java.lang.management.ManagementFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * The CPU time this thread spends in [block]. A frame budget read on the wall clock also counts the
 * time a loaded machine gives other work, which failed budgets in CI's loaded suite step (#1592); a
 * frame runs on the calling thread, so its own CPU time is the frame's cost.
 */
internal inline fun cpuTime(block: () -> Unit): Duration {
    val threads = ManagementFactory.getThreadMXBean()
    val start = threads.currentThreadCpuTime
    block()
    return (threads.currentThreadCpuTime - start).nanoseconds
}
