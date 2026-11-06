/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toLong
import platform.posix.pthread_self
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

// The thread's pthread handle as a number, which compares equal on one thread and differs between threads.
@OptIn(ExperimentalForeignApi::class)
internal actual fun currentThreadIdentity(): Any = pthread_self().toLong()

@OptIn(ExperimentalNativeApi::class)
internal actual fun frameThreadChecksByDefault(): Boolean = Platform.isDebugBinary
