/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages

actual fun systemLocale(): LocaleId {
    val preferred = NSLocale.preferredLanguages.firstOrNull() as? String
    return preferred?.let(LocaleId::parseOrNull)
        ?: LocaleId.parseOrNull(NSLocale.currentLocale.localeIdentifier)
        ?: LocaleId.English
}
