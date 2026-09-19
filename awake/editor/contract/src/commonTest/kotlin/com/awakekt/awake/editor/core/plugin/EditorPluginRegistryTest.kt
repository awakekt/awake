/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorPluginRegistryTest {
    @Test
    fun installsLinkedPluginsInOrderAndRegistersTheirProviders() {
        val providers = ProviderRegistry()
        val plugins = PluginRegistry(providers)
        val transform = RecordingPlugin("awake.transform", "transform")
        val gltf = RecordingPlugin("awake.gltf", "gltf")

        plugins.installAll(listOf(transform, gltf))

        assertEquals(listOf(transform.metadata, gltf.metadata), plugins.installed)
        assertEquals(listOf("transform", "gltf"), providers.all.map { it.metadata.id.value })
    }

    @Test
    fun rejectsAnIncompatiblePluginBeforeItCreatesProviders() {
        val providers = ProviderRegistry()
        val plugins = PluginRegistry(providers)
        val plugin = RecordingPlugin("private.terrain", "terrain", requiredApiVersion = 2)

        assertFailsWith<IllegalArgumentException> {
            plugins.install(plugin)
        }

        assertEquals(0, plugin.createdCount)
        assertEquals(emptyList(), providers.all)
        assertEquals(emptyList(), plugins.installed)
    }

    @Test
    fun rejectsDuplicatePluginIdsAndRollsBackAConflictingProviderBatch() {
        val providers = ProviderRegistry()
        val plugins = PluginRegistry(providers)
        plugins.install(RecordingPlugin("awake.transform", "transform"))

        assertFailsWith<IllegalArgumentException> {
            plugins.install(RecordingPlugin("private.terrain", "terrain", "transform"))
        }
        assertFailsWith<IllegalArgumentException> {
            plugins.install(RecordingPlugin("awake.transform", "camera"))
        }

        assertEquals(listOf("transform"), providers.all.map { it.metadata.id.value })
        assertEquals(listOf("awake.transform"), plugins.installed.map { it.id.value })
    }

    @Test
    fun uninstallsPluginAndDisposesAndRemovesItsProviders() {
        val providers = ProviderRegistry()
        val plugins = PluginRegistry(providers)
        val plugin = RecordingPlugin("community.tool", "tool.viewer", "tool.panel")

        plugins.install(plugin)
        assertEquals(listOf(plugin.metadata), plugins.installed)
        assertEquals(listOf("tool.viewer", "tool.panel"), providers.all.map { it.metadata.id.value })

        val uninstalled = plugins.uninstall(PluginId("community.tool"))
        assertTrue(uninstalled)
        assertEquals(emptyList(), plugins.installed)
        assertEquals(emptyList(), providers.all)
        assertTrue(plugin.disposed)

        val uninstallMissing = plugins.uninstall(PluginId("community.tool"))
        assertFalse(uninstallMissing)
    }
}

private class RecordingPlugin(
    id: String,
    private vararg val providerIds: String,
    requiredApiVersion: Int = PluginApi.currentVersion.value,
) : EditorPlugin,
    PluginLifecycle {
    var createdCount = 0
        private set
    var disposed = false
        private set

    override val metadata = PluginMetadata(
        id = PluginId(id),
        displayName = id,
        version = "1.0.0",
        requiredApiVersion = PluginApiVersion(requiredApiVersion),
    )

    override fun createProviders(): List<EditorProvider> {
        createdCount += 1
        return providerIds.map(::RecordingPluginProvider)
    }

    override fun dispose() {
        disposed = true
    }
}

private class RecordingPluginProvider(id: String) : ComponentProvider {
    override val metadata = ProviderMetadata(ProviderId(id), id)
    override val codec: ProviderCodec = PluginTestCodec
}

private object PluginTestCodec : ProviderCodec {
    override val currentVersion = 1

    override fun validate(configuration: ProviderConfiguration): List<ValidationMessage> = emptyList()
}
