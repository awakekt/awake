/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.localization.StringTables
import com.awakekt.awake.showcase.examples.LocalizationExampleDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizationShowcaseTest {

    @Test
    fun theSampleShowsItsTextInBothLanguages() = runTest {
        LocalizationExampleDriver.preload()
        val strings = checkNotNull(LocalizationExampleDriver.strings)
        try {
            assertEquals("Localization", strings.text("localization.title"))
            assertEquals("1 item in the bag", strings.text("localization.inventory.count", "count" to 1))
            assertEquals("4 items in the bag", strings.text("localization.inventory.count", "count" to 4))
            assertEquals("Type {name} to rename your character.", strings.text("localization.hint"))

            strings.setLocale(LocalizationExampleDriver.Filipino)
            assertEquals("Lokalisasyon", strings.text("localization.title"))
            assertEquals("Maligayang pagbabalik, Mira!", strings.text("localization.greeting", "name" to "Mira"))
            // Filipino's CLDR rule: a count ending in 4, 6 or 9 is `other`, the rest `one`.
            assertEquals("5 gamit sa bag", strings.text("localization.inventory.count", "count" to 5))
            assertEquals("4 na gamit sa bag", strings.text("localization.inventory.count", "count" to 4))
            assertEquals("Walang laman ang bag", strings.text("localization.inventory.count", "count" to 0))
        } finally {
            strings.setLocale(LocalizationExampleDriver.English)
        }
    }

    @Test
    fun bothTablesHaveTheSameKeys() = runTest {
        val english = StringTables.decode(readResourceBytes("assets/examples/strings/en.json"))
        val filipino = StringTables.decode(readResourceBytes("assets/examples/strings/fil.json"))
        assertEquals(english.keys, filipino.keys)
    }
}
