/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

/**
 * The locale the device or browser is set to, for a game to start in when the player has not
 * chosen one.
 *
 * Desktop and Android read the JVM default locale, iOS the first of the user's preferred languages,
 * and the browser `navigator.language`. A platform that names no language gives [LocaleId.English].
 *
 * @return The system locale.
 */
expect fun systemLocale(): LocaleId
