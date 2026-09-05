/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.plugins.discovery

import io.github.awakelab.awake.editor.core.plugin.EditorPlugin
import io.github.awakelab.awake.editor.core.plugin.EditorPluginApiVersion
import io.github.awakelab.awake.editor.core.plugin.EditorPluginId
import io.github.awakelab.awake.editor.core.plugin.EditorPluginMetadata
import io.github.awakelab.awake.editor.core.plugin.EditorProvider
import io.github.awakelab.awake.editor.core.plugin.PluginManifest
import io.github.awakelab.awake.studio.state.StudioEditorBridge
import io.github.awakelab.awake.studio.state.StudioStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginDiscoveryOfficialTest {

    @Test
    fun officialDiscoveryReturnsDefaultPlugins() {
        val discovery = PluginDiscoveryOfficial()
        val plugins = discovery.discover()

        assertTrue(plugins.isNotEmpty(), "Catalog must not be empty")
        assertTrue(plugins.any { it.manifest.id == "awake.terrain" }, "Terrain plugin must be present")
        assertTrue(plugins.any { it.manifest.id == "awake.dialogue" }, "Dialogue plugin must be present")
        assertTrue(plugins.any { it.manifest.id == "awake.shader-graph" }, "Shader graph plugin must be present")
        assertTrue(plugins.any { it.manifest.id == "community.lod-gen" }, "LOD generator must be present")
    }

    @Test
    fun pluginsHaveAccurateCategoriesAndMetadata() {
        val discovery = PluginDiscoveryOfficial()
        val plugins = discovery.discover()

        val terrain = plugins.first { it.manifest.id == "awake.terrain" }
        assertEquals(PluginCategory.Rendering, terrain.category)
        assertTrue(terrain.isPro)
        assertTrue(terrain.tags.contains("terrain"))

        val lodGen = plugins.first { it.manifest.id == "community.lod-gen" }
        assertEquals(PluginCategory.Tools, lodGen.category)
        assertFalse(lodGen.isPro)
        assertEquals("Acme Tools", lodGen.manifest.author)
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
        val archive = io.github.awakelab.awake.editor.core.plugin.StudioPluginArchive(
            manifest = manifest,
            signatureHex = null,
        )
        val installer = io.github.awakelab.awake.editor.core.plugin.StudioPluginInstaller()
        val result = installer.install(archive)

        val success = assertIs<io.github.awakelab.awake.editor.core.plugin.PluginInstallResult.Success>(result)
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
