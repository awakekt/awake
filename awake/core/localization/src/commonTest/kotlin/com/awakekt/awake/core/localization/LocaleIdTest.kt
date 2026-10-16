/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LocaleIdTest {

    @Test
    fun tagsAreNormalized() {
        assertEquals("zh-Hant-TW", LocaleId.of("ZH_hant_tw").tag)
        assertEquals("pt-BR", LocaleId.of("pt-br").tag)
        assertEquals("en-US", LocaleId.of("en_US.UTF-8").tag)
        assertEquals("es-419", LocaleId.of("es-419").tag)
        assertEquals("fil", LocaleId.of("fil-x-private").tag)
        assertEquals(LocaleId.of("ko"), LocaleId.of("KO"))
    }

    @Test
    fun notALanguageIsRejected() {
        assertNull(LocaleId.parseOrNull("C"))
        assertNull(LocaleId.parseOrNull(""))
        assertNull(LocaleId.parseOrNull("POSIX"))
        assertFailsWith<IllegalArgumentException> { LocaleId.of("1x") }
    }

    @Test
    fun theFallbackChainDropsOneSubtagAtATime() {
        assertEquals(listOf("zh-Hant-TW", "zh-Hant", "zh"), LocaleId.of("zh-Hant-TW").fallbackChain.map { it.tag })
        assertEquals(listOf("en"), LocaleId.English.fallbackChain.map { it.tag })
    }

    @Test
    fun theSystemLocaleIsALocale() {
        // Whatever the machine is set to, it parses to a language.
        assertEquals(true, systemLocale().language.length in 2..3)
    }
}
