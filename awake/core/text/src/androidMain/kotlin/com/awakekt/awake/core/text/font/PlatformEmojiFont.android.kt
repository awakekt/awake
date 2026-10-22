/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import java.io.File

internal actual fun readPlatformEmojiFontBytes(): ByteArray? {
    val file = File("/system/fonts/NotoColorEmoji.ttf")
    return if (file.exists() && file.canRead()) {
        runCatching { file.readBytes() }.getOrNull()
    } else {
        null
    }
}
