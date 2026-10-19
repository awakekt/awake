/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

import kotlin.time.TimeSource

private val nativeClock = TimeSource.Monotonic
private val nativeMark = nativeClock.markNow()

internal actual fun hostNanoTime(): Long = (nativeClock.markNow() - nativeMark).inWholeNanoseconds

internal actual fun hostCurrentTimeMillis(): Long = (nativeClock.markNow() - nativeMark).inWholeMilliseconds
