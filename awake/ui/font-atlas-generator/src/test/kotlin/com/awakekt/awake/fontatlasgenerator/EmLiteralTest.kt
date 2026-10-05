/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** The generated source must not depend on the locale of the machine that generated it. */
class EmLiteralTest {

    private fun inLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun writesADecimalPointWhateverTheDefaultLocale() {
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("ar-EG"))) {
            inLocale(locale) {
                assertEquals("0.927735", emLiteral(0.927735f), "in $locale")
                assertEquals("-1.500000", emLiteral(-1.5f), "in $locale")
            }
        }
    }

    @Test
    fun roundsToSixDecimals() {
        assertEquals("0.333333", emLiteral(1f / 3f))
        assertEquals("1.000000", emLiteral(1f))
    }
}
