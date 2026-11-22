/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("PlatformFileSystem_desktopKt")

package com.awakekt.awake.core.io

/**
 * Binary-compatibility bridge for pure-JVM (`kmp-library`) modules compiled against
 * `com.awakekt.awake.core:io-desktop` when consumed on Android with `io-android`.
 */
@Deprecated(
    "Binary compatibility bridge for pure-JVM modules compiled against io-desktop",
    level = DeprecationLevel.HIDDEN,
)
@JvmName("createPlatformFileSystem")
fun createPlatformFileSystemDesktopCompat(root: String? = null): FileSystem =
    createPlatformFileSystem(root)
