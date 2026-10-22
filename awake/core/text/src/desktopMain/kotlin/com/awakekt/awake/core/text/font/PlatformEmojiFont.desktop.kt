/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import java.io.File

internal actual fun readPlatformEmojiFontBytes(): ByteArray? {
    val os = System.getProperty("os.name")?.lowercase() ?: ""
    val candidatePaths = when {
        os.contains("win") -> listOf("C:\\Windows\\Fonts\\seguiemj.ttf")
        os.contains("mac") -> listOf("/System/Library/Fonts/Apple Color Emoji.ttc")
        else -> listOf(
            "/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf",
            "/usr/share/fonts/google-noto-color-emoji/NotoColorEmoji.ttf",
            "/usr/share/fonts/google-noto/NotoColorEmoji.ttf",
        )
    }
    for (path in candidatePaths) {
        val file = File(path)
        if (file.exists() && file.canRead()) {
            val bytes = runCatching { file.readBytes() }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }
    }
    return null
}
