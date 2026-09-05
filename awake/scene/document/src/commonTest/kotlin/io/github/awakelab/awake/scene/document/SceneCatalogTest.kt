/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SceneCatalogTest {

    @Test
    fun registersAndFindsEntries() {
        val catalog = SceneCatalog()

        catalog.register(SceneCatalogEntry("flarine", "Flarine Town"))
        catalog.register(SceneCatalogEntry("arena", "Madrigal Arena"))

        assertEquals(2, catalog.all.size)
        val entry = catalog.find("flarine")
        assertNotNull(entry)
        assertEquals("Flarine Town", entry.displayName)
    }

    @Test
    fun registersJsonDocumentDirectly() {
        val catalog = SceneCatalog()
        val json = """
            {
              "version": 1,
              "name": "Darkon City",
              "nodes": []
            }
        """.trimIndent()

        val registered = catalog.registerJson("darkon", "Darkon Default", json)
        assertEquals("Darkon City", registered.displayName)
        assertNotNull(registered.document)

        val retrieved = catalog.find("darkon")
        assertNotNull(retrieved)
        assertEquals("Darkon City", retrieved.displayName)
    }
}
