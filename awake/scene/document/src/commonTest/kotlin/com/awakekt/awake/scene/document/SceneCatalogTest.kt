/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SceneCatalogTest {

    @Test
    fun registersAndFindsEntries() {
        val catalog = SceneCatalog()

        catalog.register(SceneCatalogEntry("harbor", "Harbor Town"))
        catalog.register(SceneCatalogEntry("arena", "Desert Arena"))

        assertEquals(2, catalog.all.size)
        val entry = catalog.find("harbor")
        assertNotNull(entry)
        assertEquals("Harbor Town", entry.displayName)
    }

    @Test
    fun registersJsonDocumentDirectly() {
        val catalog = SceneCatalog()
        val json = """
            {
              "version": 1,
              "name": "Mountain City",
              "nodes": []
            }
        """.trimIndent()

        val registered = catalog.registerJson("mountain", "Mountain Default", json)
        assertEquals("Mountain City", registered.displayName)
        assertNotNull(registered.document)

        val retrieved = catalog.find("mountain")
        assertNotNull(retrieved)
        assertEquals("Mountain City", retrieved.displayName)
    }
}
