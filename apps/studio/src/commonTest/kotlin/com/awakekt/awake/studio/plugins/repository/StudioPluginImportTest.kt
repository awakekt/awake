/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins.repository

import com.awakekt.awake.editor.core.plugin.PluginInstallResult
import com.awakekt.awake.editor.core.plugin.PluginManifest
import com.awakekt.awake.studio.plugins.InMemoryStudioPluginPersistence
import com.awakekt.awake.studio.plugins.discovery.MarketplacePluginEntry
import com.awakekt.awake.studio.plugins.discovery.PluginDiscovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudioPluginImportTest {

    @Test
    fun parseManifestFromJsonAndRegisterIntoRepository() {
        val json = """
            {
              "id": "community.terrain-tools",
              "name": "Terrain Tools",
              "version": "1.2.0",
              "author": "Awake Community",
              "description": "Custom sculpting tools and brush utilities."
            }
        """.trimIndent()

        val manifest = PluginManifest.fromJson(json)
        assertEquals("community.terrain-tools", manifest.id)
        assertEquals("Terrain Tools", manifest.name)
        assertEquals("1.2.0", manifest.version)

        val persistence = InMemoryStudioPluginPersistence()
        val emptyDiscovery = object : PluginDiscovery {
            override fun discover(): List<MarketplacePluginEntry> = emptyList()
        }
        val repository = DefaultStudioPluginRepository(discovery = emptyDiscovery, persistence = persistence)

        assertEquals(0, repository.getCatalog().size)
        assertEquals(0, repository.getInstalled().size)

        // Install imported manifest
        val result = repository.install(manifest)
        assertTrue(result is PluginInstallResult.Success)

        // Verify catalog updated
        assertEquals(1, repository.getCatalog().size)
        val catalogEntry = repository.findById("community.terrain-tools")
        assertNotNull(catalogEntry)
        assertTrue(catalogEntry.isInstalled)
        assertEquals("Terrain Tools", catalogEntry.manifest.name)

        // Verify persistence reloads cleanly
        val reloadedRepository = DefaultStudioPluginRepository(discovery = emptyDiscovery, persistence = persistence)
        assertEquals(1, reloadedRepository.getCatalog().size)
        val reloadedEntry = reloadedRepository.findById("community.terrain-tools")
        assertNotNull(reloadedEntry)
        assertTrue(reloadedEntry.isInstalled)
    }
}
