/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/**
 * Reads a bundled resource from iOS application bundle resources at [path] as raw bytes, offloading
 * onto [Dispatchers.IO] when called from a confined dispatcher.
 *
 * @param path The relative resource path to read.
 * @return The raw byte contents of the resource.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun readResourceBytes(path: String): ByteArray = withHostIoIfConfined(Dispatchers.IO) {
    val fullPath = NSBundle.mainBundle.resourcePath?.plus("/$path")
        ?: error("Bundle resource path unavailable")
    val data: NSData = NSData.dataWithContentsOfFile(fullPath)
        ?: error("Resource not found: $fullPath")
    val size = data.length.toInt()
    if (size == 0) return@withHostIoIfConfined ByteArray(0)
    ByteArray(size).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }
}
