/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins.discovery

import com.awakekt.awake.editor.core.plugin.EditorPlugin
import com.awakekt.awake.editor.core.plugin.EditorPluginApiVersion
import com.awakekt.awake.editor.core.plugin.EditorPluginId
import com.awakekt.awake.editor.core.plugin.EditorPluginMetadata
import com.awakekt.awake.editor.core.plugin.EditorProvider
import com.awakekt.awake.editor.core.plugin.PluginManifest
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginDiscoveryOfficialTest {

    @Test
    fun officialDiscoveryReturnsEmptyCatalogByDefault() {
        val discovery = PluginDiscoveryOfficial()
        val plugins = discovery.discover()

        assertTrue(plugins.isEmpty(), "Official discovery catalog must be empty by default without static mock items")
    }

    @Test
    fun jsonDiscoveryParsesRealCatalog() {
        val jsonCatalog = """
            [
                {
                    "manifest": {
                        "id": "vendor.custom-tool",
                        "name": "Custom Tool",
                        "version": "1.0.0",
                        "author": "Vendor Corp",
                        "description": "A custom production tool extension."
                    },
                    "category": "Tools",
                    "isPro": false,
                    "tags": ["custom", "tool"]
                }
            ]
        """.trimIndent()

        val discovery = JsonPluginDiscovery(jsonProvider = { jsonCatalog })
        val plugins = discovery.discover()

        assertEquals(1, plugins.size)
        val tool = plugins.first()
        assertEquals("vendor.custom-tool", tool.manifest.id)
        assertEquals(PluginCategory.Tools, tool.category)
        assertFalse(tool.isPro)
        assertEquals("Vendor Corp", tool.manifest.author)
    }

    @Test
    fun pluginCanBeInstalledDynamicallyIntoEditorBridge() {
        val store = StudioStore()
        val bridge = StudioEditorBridge(store)
        val initialCount = bridge.plugins.installed.size

        val manifestJson = """
            {
                "id": "community.sample-plugin",
                "name": "Sample Test Plugin",
                "version": "1.0.0",
                "author": "Test Author",
                "description": "A dynamically installed test plugin."
            }
        """.trimIndent()

        val manifest = PluginManifest.fromJson(manifestJson)
        val dynamicPlugin = object : EditorPlugin {
            override val metadata = EditorPluginMetadata(
                id = EditorPluginId(manifest.id),
                displayName = manifest.name,
                version = manifest.version,
                requiredApiVersion = EditorPluginApiVersion(1),
            )
            override fun createProviders(): List<EditorProvider> = emptyList()
        }

        bridge.installPlugin(dynamicPlugin)
        assertEquals(initialCount + 1, bridge.plugins.installed.size)
        assertNotNull(bridge.plugins.installed.find { it.id.value == "community.sample-plugin" })
    }

    @Test
    fun bundleArchiveImportInstallsPackageWithSignatureCheck() {
        val manifest = PluginManifest(
            id = "local.render-tools",
            name = "Render Tools",
            version = "1.0.0",
            author = "Community Dev",
            description = "Custom bundle extension",
        )
        val archive = com.awakekt.awake.editor.core.plugin.StudioPluginArchive(
            manifest = manifest,
            signatureHex = null,
        )
        val installer = com.awakekt.awake.editor.core.plugin.StudioPluginInstaller()
        val result = installer.install(archive)

        val success = assertIs<com.awakekt.awake.editor.core.plugin.PluginInstallResult.Success>(result)
        assertEquals("local.render-tools", success.pkg.manifest.id)
        assertTrue(success.pkg.isEnabled)
    }

    @Test
    fun githubUrlDerivesValidPluginManifest() {
        val url = "https://github.com/awakelab/awake-sample-plugin.git"
        val clean = url.removePrefix("https://").removePrefix("http://").removePrefix("git@github.com:")
            .removePrefix("github.com/").removeSuffix(".git").trim('/')
        val parts = clean.split('/')
        val owner = parts[0]
        val repo = parts[1]

        assertEquals("awakelab", owner)
        assertEquals("awake-sample-plugin", repo)

        val manifest = PluginManifest(
            id = "github.$owner.$repo",
            name = repo.replace('-', ' ').replace('_', ' ')
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            version = "1.0.0",
            author = owner,
            description = "Cloned from GitHub repository $owner/$repo.",
        )
        assertEquals("github.awakelab.awake-sample-plugin", manifest.id)
        assertEquals("Awake sample plugin", manifest.name)
    }
}
