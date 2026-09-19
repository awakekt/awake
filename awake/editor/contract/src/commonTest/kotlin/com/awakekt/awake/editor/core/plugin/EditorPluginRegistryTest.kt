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
        val providers = EditorProviders()
        val plugins = EditorPluginRegistry(providers)
        val transform = RecordingPlugin("awake.transform", "transform")
        val gltf = RecordingPlugin("awake.gltf", "gltf")

        plugins.installAll(listOf(transform, gltf))

        assertEquals(listOf(transform.metadata, gltf.metadata), plugins.installed)
        assertEquals(listOf("transform", "gltf"), providers.all.map { it.metadata.id.value })
    }

    @Test
    fun rejectsAnIncompatiblePluginBeforeItCreatesProviders() {
        val providers = EditorProviders()
        val plugins = EditorPluginRegistry(providers)
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
        val providers = EditorProviders()
        val plugins = EditorPluginRegistry(providers)
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
        val providers = EditorProviders()
        val plugins = EditorPluginRegistry(providers)
        val plugin = RecordingPlugin("community.tool", "tool.viewer", "tool.panel")

        plugins.install(plugin)
        assertEquals(listOf(plugin.metadata), plugins.installed)
        assertEquals(listOf("tool.viewer", "tool.panel"), providers.all.map { it.metadata.id.value })

        val uninstalled = plugins.uninstall(EditorPluginId("community.tool"))
        assertTrue(uninstalled)
        assertEquals(emptyList(), plugins.installed)
        assertEquals(emptyList(), providers.all)
        assertTrue(plugin.disposed)

        val uninstallMissing = plugins.uninstall(EditorPluginId("community.tool"))
        assertFalse(uninstallMissing)
    }
}

private class RecordingPlugin(
    id: String,
    private vararg val providerIds: String,
    requiredApiVersion: Int = EditorPluginApi.currentVersion.value,
) : EditorPlugin,
    EditorPluginLifecycle {
    var createdCount = 0
        private set
    var disposed = false
        private set

    override val metadata = EditorPluginMetadata(
        id = EditorPluginId(id),
        displayName = id,
        version = "1.0.0",
        requiredApiVersion = EditorPluginApiVersion(requiredApiVersion),
    )

    override fun createProviders(): List<EditorProvider> {
        createdCount += 1
        return providerIds.map(::RecordingPluginProvider)
    }

    override fun dispose() {
        disposed = true
    }
}

private class RecordingPluginProvider(id: String) : EditorComponentProvider {
    override val metadata = EditorProviderMetadata(EditorProviderId(id), id)
    override val codec: EditorProviderCodec = PluginTestCodec
}

private object PluginTestCodec : EditorProviderCodec {
    override val currentVersion = 1

    override fun validate(configuration: EditorProviderConfiguration): List<EditorValidationMessage> = emptyList()
}
