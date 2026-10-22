/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
internal actual fun readPlatformEmojiFontBytes(): ByteArray? {
    val fileManager = NSFileManager.defaultManager
    val candidates = listOf(
        "/System/Library/Fonts/Apple Color Emoji.ttc",
        "/System/Library/Fonts/CoreAddition/AppleColorEmoji-160px.ttc",
    )
    for (path in candidates) {
        if (!fileManager.fileExistsAtPath(path)) continue
        val data = NSData.dataWithContentsOfFile(path)
        val bytes = data?.bytes
        if (data != null && bytes != null) {
            return bytes.readBytes(data.length.toInt())
        }
    }
    return null
}
