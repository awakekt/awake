/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins

import com.awakekt.awake.editor.core.plugin.EditorPluginId
import com.awakekt.awake.editor.core.plugin.PluginManifest
import com.awakekt.awake.editor.shell.dockContributions
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudioPluginPersistenceTest {

    @Test
    fun inMemoryPersistenceSavesAndLoadsManifests() {
        val persistence = InMemoryStudioPluginPersistence()
        assertEquals(emptyList(), persistence.loadInstalled())

        val manifests = listOf(
            PluginManifest(
                id = "tool.lod",
                name = "LOD Tool",
                version = "1.0.0",
                description = "LOD Mesh Generator",
            ),
            PluginManifest(
                id = "tool.pixel",
                name = "Pixel Filter",
                version = "1.2.0",
            ),
        )

        persistence.saveInstalled(manifests)
        val loaded = persistence.loadInstalled()
        assertEquals(2, loaded.size)
        assertEquals("tool.lod", loaded[0].id)
        assertEquals("tool.pixel", loaded[1].id)
    }

    @Test
    fun studioEditorBridgeLoadsPersistedPluginsAndRegistersDockTabs() {
        val manifest = PluginManifest(
            id = "community.mesh-tools",
            name = "Mesh Toolkit",
            version = "1.0.0",
            contributesDockTab = true,
            dockTabTitle = "Mesh Tools",
        )
        val persistence = InMemoryStudioPluginPersistence(listOf(manifest))
        val store = StudioStore()
        val bridge = StudioEditorBridge(store, persistence = persistence)

        val installedMeta = bridge.plugins.installed.find { it.id.value == "community.mesh-tools" }
        assertNotNull(installedMeta)
        assertEquals("Mesh Toolkit", installedMeta.displayName)

        val dock = bridge.providers.dockContributions().find { it.tab.label == "Mesh Tools" }
        assertNotNull(dock)
        assertEquals("ext-community-mesh-tools", dock.tab.id)
    }

    @Test
    fun uninstallationRemovesPluginAndDockContributionAndUpdatesPersistence() {
        val manifest = PluginManifest(
            id = "community.temp-tool",
            name = "Temp Tool",
            version = "1.0.0",
            contributesDockTab = true,
        )
        val persistence = InMemoryStudioPluginPersistence(listOf(manifest))
        val store = StudioStore()
        val bridge = StudioEditorBridge(store, persistence = persistence)

        assertNotNull(bridge.plugins.installed.find { it.id.value == "community.temp-tool" })
        assertNotNull(bridge.providers.dockContributions().find { it.tab.id == "ext-community-temp-tool" })

        val uninstalled = bridge.uninstallPlugin(EditorPluginId("community.temp-tool"))
        assertTrue(uninstalled)

        assertNull(bridge.plugins.installed.find { it.id.value == "community.temp-tool" })
        assertNull(bridge.providers.dockContributions().find { it.tab.id == "ext-community-temp-tool" })
        assertFalse(persistence.loadInstalled().any { it.id == "community.temp-tool" })
    }
}
