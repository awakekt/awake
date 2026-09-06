/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins.repository

import com.awakekt.awake.editor.core.plugin.EditorPluginApiVersion
import com.awakekt.awake.editor.core.plugin.EditorPluginId
import com.awakekt.awake.editor.core.plugin.EditorPluginMetadata
import com.awakekt.awake.editor.core.plugin.PluginInstallResult
import com.awakekt.awake.editor.core.plugin.PluginManifest
import com.awakekt.awake.studio.plugins.InMemoryStudioPluginPersistence
import com.awakekt.awake.studio.plugins.discovery.MarketplacePluginEntry
import com.awakekt.awake.studio.plugins.discovery.PluginCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudioPluginRepositoryTest {

    @Test
    fun mockRepositoryProvidesInMemCatalogAndInstalled() {
        val entry1 = MarketplacePluginEntry(
            manifest = PluginManifest(id = "ext.shader", name = "Shader Graph", version = "1.0.0"),
            category = PluginCategory.Rendering,
            isPro = true,
        )
        val entry2 = MarketplacePluginEntry(
            manifest = PluginManifest(id = "ext.audio", name = "Audio Mixer", version = "2.0.0"),
            category = PluginCategory.Gameplay,
            isPro = false,
        )
        val mockRepo = MockStudioPluginRepository(
            initialCatalog = listOf(entry1, entry2),
            initialInstalled = listOf(
                EditorPluginMetadata(
                    id = EditorPluginId("ext.shader"),
                    displayName = "Shader Graph",
                    version = "1.0.0",
                    requiredApiVersion = EditorPluginApiVersion(1),
                ),
            ),
        )

        assertEquals(2, mockRepo.getCatalog().size)
        assertEquals(1, mockRepo.getInstalled().size)
        assertEquals("ext.shader", mockRepo.getInstalled()[0].id.value)

        // Search
        val audioResults = mockRepo.search("audio")
        assertEquals(1, audioResults.size)
        assertEquals("ext.audio", audioResults[0].manifest.id)

        // Category filter
        val proResults = mockRepo.search("", category = PluginCategory.Pro)
        assertEquals(1, proResults.size)
        assertEquals("ext.shader", proResults[0].manifest.id)

        val communityResults = mockRepo.search("", category = PluginCategory.Community)
        assertEquals(1, communityResults.size)
        assertEquals("ext.audio", communityResults[0].manifest.id)
    }

    @Test
    fun mockRepositoryInstallAndUninstallLifecycle() {
        val entry = MarketplacePluginEntry(
            manifest = PluginManifest(id = "ext.nav", name = "NavMesh Pathfinding", version = "1.0.0"),
            category = PluginCategory.Tools,
        )
        val mockRepo = MockStudioPluginRepository(initialCatalog = listOf(entry))

        assertFalse(mockRepo.findById("ext.nav")?.isInstalled == true)
        assertEquals(0, mockRepo.getInstalled().size)

        // Install
        val result = mockRepo.install(entry.manifest)
        assertTrue(result is PluginInstallResult.Success)
        assertTrue(mockRepo.findById("ext.nav")?.isInstalled == true)
        assertEquals(1, mockRepo.getInstalled().size)
        assertEquals("ext.nav", mockRepo.getInstalled()[0].id.value)

        // Uninstall
        val uninstalled = mockRepo.uninstall(EditorPluginId("ext.nav"))
        assertTrue(uninstalled)
        assertFalse(mockRepo.findById("ext.nav")?.isInstalled == true)
        assertEquals(0, mockRepo.getInstalled().size)
    }

    @Test
    fun defaultRepositoryLoadsCatalogAndSyncsWithPersistence() {
        val persistence = InMemoryStudioPluginPersistence(
            initial = listOf(
                PluginManifest(id = "persisted.plugin", name = "Persisted Plugin", version = "1.0.0"),
            ),
        )
        val repo = DefaultStudioPluginRepository(
            persistence = persistence,
        )

        val installed = repo.getInstalled()
        assertEquals(1, installed.size)
        assertEquals("persisted.plugin", installed[0].id.value)

        val found = repo.findById("persisted.plugin")
        assertNotNull(found)
        assertTrue(found.isInstalled)

        // Install new plugin
        val newManifest = PluginManifest(id = "new.plugin", name = "New Plugin", version = "1.1.0")
        val result = repo.install(newManifest)
        assertTrue(result is PluginInstallResult.Success)

        val updatedInstalled = repo.getInstalled()
        assertEquals(2, updatedInstalled.size)
        assertTrue(persistence.loadInstalled().any { it.id == "new.plugin" })

        // Uninstall
        val uninstalled = repo.uninstall(EditorPluginId("persisted.plugin"))
        assertTrue(uninstalled)
        assertFalse(persistence.loadInstalled().any { it.id == "persisted.plugin" })
    }
}
