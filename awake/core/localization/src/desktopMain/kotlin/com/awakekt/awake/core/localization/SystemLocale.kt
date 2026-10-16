/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import java.util.Locale

actual fun systemLocale(): LocaleId = LocaleId.parseOrNull(Locale.getDefault().toLanguageTag()) ?: LocaleId.English
