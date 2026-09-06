/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins.repository

import com.awakekt.awake.editor.core.plugin.EditorPluginId
import com.awakekt.awake.editor.core.plugin.PluginManifest
import com.awakekt.awake.editor.shell.dockContributions
import com.awakekt.awake.studio.plugins.InMemoryStudioPluginPersistence
import com.awakekt.awake.studio.plugins.discovery.MarketplacePluginEntry
import com.awakekt.awake.studio.plugins.discovery.PluginDiscovery
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudioPluginLifecycleTest {

    private val sampleManifest = PluginManifest(
        id = "community.dialogue-system",
        name = "Dialogue System",
        version = "1.0.0",
        author = "Test Author",
        description = "Interactive branching dialogue editor",
        contributesDockTab = true,
        dockTabTitle = "Dialogue Editor",
    )

    @Test
    fun installEnableDisableUninstallUpdatesRepositoryAndBridge() {
        val persistence = InMemoryStudioPluginPersistence()
        val emptyDiscovery = object : PluginDiscovery {
            override fun discover(): List<MarketplacePluginEntry> = emptyList()
        }
        val repository = DefaultStudioPluginRepository(discovery = emptyDiscovery, persistence = persistence)
        val studioStore = StudioStore()
        val bridge = StudioEditorBridge(
            studio = studioStore,
            persistence = persistence,
            pluginRepository = repository,
        )

        val pluginId = EditorPluginId(sampleManifest.id)

        // 1. Initially not installed
        assertFalse(repository.getCatalog().any { it.manifest.id == sampleManifest.id })
        assertFalse(bridge.plugins.installed.any { it.id == pluginId })

        // 2. Install
        repository.install(sampleManifest)
        assertTrue(repository.isEnabled(pluginId))
        val entryAfterInstall = repository.findById(sampleManifest.id)
        assertTrue(entryAfterInstall != null && entryAfterInstall.isInstalled && entryAfterInstall.isEnabled)
        assertTrue(bridge.plugins.installed.any { it.id == pluginId })
        // Check dock tab contribution
        val tabId = "ext-${sampleManifest.id.replace('.', '-')}"
        assertTrue(bridge.providers.dockContributions().any { it.tab.id == tabId })

        // 3. Disable
        val disableResult = repository.setEnabled(pluginId, false)
        assertTrue(disableResult)
        assertFalse(repository.isEnabled(pluginId))
        assertFalse(bridge.plugins.installed.any { it.id == pluginId })
        assertFalse(bridge.providers.dockContributions().any { it.tab.id == tabId })

        // 4. Re-enable
        val enableResult = repository.setEnabled(pluginId, true)
        assertTrue(enableResult)
        assertTrue(repository.isEnabled(pluginId))
        assertTrue(bridge.plugins.installed.any { it.id == pluginId })
        assertTrue(bridge.providers.dockContributions().any { it.tab.id == tabId })

        // 5. Uninstall
        val uninstallResult = repository.uninstall(pluginId)
        assertTrue(uninstallResult)
        assertFalse(repository.findById(sampleManifest.id)?.isInstalled == true)
        assertFalse(bridge.plugins.installed.any { it.id == pluginId })
        assertFalse(bridge.providers.dockContributions().any { it.tab.id == tabId })
    }

    @Test
    fun disabledPluginIsNotInstalledOnStartup() {
        val pluginId = EditorPluginId(sampleManifest.id)
        val persistence = InMemoryStudioPluginPersistence(
            initial = listOf(sampleManifest),
            initialDisabled = setOf(sampleManifest.id),
        )
        val emptyDiscovery = object : PluginDiscovery {
            override fun discover(): List<MarketplacePluginEntry> = emptyList()
        }
        val repository = DefaultStudioPluginRepository(discovery = emptyDiscovery, persistence = persistence)
        val studioStore = StudioStore()
        val bridge = StudioEditorBridge(
            studio = studioStore,
            persistence = persistence,
            pluginRepository = repository,
        )

        assertFalse(repository.isEnabled(pluginId))
        assertFalse(bridge.plugins.installed.any { it.id == pluginId })

        // Enabling it now installs it into the bridge
        repository.setEnabled(pluginId, true)
        assertTrue(bridge.plugins.installed.any { it.id == pluginId })
    }
}
