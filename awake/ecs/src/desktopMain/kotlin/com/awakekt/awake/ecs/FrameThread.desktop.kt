/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

internal actual fun currentThreadIdentity(): Any = Thread.currentThread()

internal actual fun frameThreadChecksByDefault(): Boolean =
    FrameThreadGuard::class.java.desiredAssertionStatus() || java.lang.System.getProperty("awake.frameThreadChecks") == "true"
