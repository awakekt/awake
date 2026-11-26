/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("IoDispatcher_desktopKt")

package com.awakekt.awake.core.io

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Binary-compatibility bridge for pure-JVM (`kmp-library`) modules compiled against
 * `com.awakekt.awake.core:io-desktop` when consumed on Android with `io-android`.
 */
@Deprecated(
    "Binary compatibility bridge for pure-JVM modules compiled against io-desktop",
    level = DeprecationLevel.HIDDEN,
)
@JvmName("getIoDispatcher")
fun ioDispatcherDesktopCompat(): CoroutineDispatcher = ioDispatcher

/**
 * Binary-compatibility bridge for [decodeDispatcher] from pure-JVM modules compiled against
 * `com.awakekt.awake.core:io-desktop` when consumed on Android with `io-android`.
 */
@Deprecated(
    "Binary compatibility bridge for pure-JVM modules compiled against io-desktop",
    level = DeprecationLevel.HIDDEN,
)
@JvmName("getDecodeDispatcher")
fun decodeDispatcherDesktopCompat(): CoroutineDispatcher = decodeDispatcher
