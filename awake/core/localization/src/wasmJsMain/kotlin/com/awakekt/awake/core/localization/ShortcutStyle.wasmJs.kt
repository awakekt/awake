/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

private fun navigatorPlatform(): String =
    js("(typeof navigator !== 'undefined' && ((navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform)) || ''")

internal actual fun systemShortcutStyle(): ShortcutStyle {
    val platform = navigatorPlatform().lowercase()
    return when {
        "mac" in platform || "iphone" in platform || "ipad" in platform -> ShortcutStyle.Apple
        "win" in platform -> ShortcutStyle.Windows
        else -> ShortcutStyle.Linux
    }
}
