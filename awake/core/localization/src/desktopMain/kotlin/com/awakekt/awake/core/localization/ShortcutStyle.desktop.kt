/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

internal actual fun systemShortcutStyle(): ShortcutStyle {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        "mac" in os || "darwin" in os -> ShortcutStyle.Apple
        "windows" in os -> ShortcutStyle.Windows
        else -> ShortcutStyle.Linux
    }
}
