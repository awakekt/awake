/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

private fun navigatorLanguage(): String? = js("(typeof navigator !== 'undefined' && navigator.language) || null")

actual fun systemLocale(): LocaleId = navigatorLanguage()?.let(LocaleId::parseOrNull) ?: LocaleId.English
