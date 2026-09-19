/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import kotlin.jvm.JvmInline

/** Stable identity for an installed editor extension artifact. */
@JvmInline
value class EditorPluginId(val value: String) {
    init {
        require(value.isNotBlank()) { "An editor plugin ID must not be blank." }
    }
}

/** Source-compatible editor-plugin contract version. */
@JvmInline
value class EditorPluginApiVersion(val value: Int) {
    init {
        require(value >= 1) { "An editor plugin API version must be positive." }
    }
}

object EditorPluginApi {
    val currentVersion = EditorPluginApiVersion(1)
}

data class EditorPluginMetadata(
    val id: EditorPluginId,
    val displayName: String,
    val version: String,
    val requiredApiVersion: EditorPluginApiVersion,
    val description: String = "",
) {
    init {
        require(displayName.isNotBlank()) { "An editor plugin display name must not be blank." }
        require(version.isNotBlank()) { "An editor plugin version must not be blank." }
    }
}

/**
 * Build-time linked extension point for an editor host.
 *
 * A plugin returns provider instances rather than mutating a registry itself, allowing the host
 * to reject duplicate provider IDs atomically. Dynamic code loading is intentionally excluded:
 * Kotlin/Native and wasmJs require ordinary, pinned application dependencies.
 */
interface EditorPlugin {
    val metadata: EditorPluginMetadata

    fun createProviders(): List<EditorProvider>
}

/**
 * Optional lifecycle for plugins that own resources beyond their providers.
 *
 * Providers are always disposed by [EditorProviders]. This hook covers plugin-owned resources
 * such as native handles, background jobs, or caches and runs after the plugin's providers have
 * been unregistered.
 */
interface EditorPluginLifecycle {
    fun dispose()
}

/**
 * Host-owned installation registry for explicitly linked [EditorPlugin] instances.
 *
 * This is not a marketplace or runtime loader. Manifest verification, permissions, locks, and
 * Gradle resolution live above this API in the application installer.
 */
class EditorPluginRegistry(
    private val providers: EditorProviders,
    private val supportedApiVersion: EditorPluginApiVersion = EditorPluginApi.currentVersion,
) {
    private data class InstalledPlugin(
        val plugin: EditorPlugin,
        val providers: List<EditorProvider>,
    )

    private val installedById = linkedMapOf<EditorPluginId, InstalledPlugin>()

    val installed: List<EditorPluginMetadata> get() = installedById.values.map { it.plugin.metadata }

    fun install(plugin: EditorPlugin) {
        val metadata = plugin.metadata
        require(metadata.requiredApiVersion == supportedApiVersion) {
            "Editor plugin '${metadata.id.value}' requires API ${metadata.requiredApiVersion.value}, " +
                "but this host supports ${supportedApiVersion.value}."
        }
        require(metadata.id !in installedById) {
            "Editor plugin '${metadata.id.value}' is already installed."
        }
        val created = plugin.createProviders()
        providers.registerAll(created)
        installedById[metadata.id] = InstalledPlugin(plugin, created)
    }

    /**
     * Uninstalls an editor plugin by its ID, cleanly disposing and removing all providers it registered.
     */
    fun uninstall(pluginId: EditorPluginId): Boolean {
        val installed = installedById.remove(pluginId)
        if (installed != null) {
            providers.unregisterAll(installed.providers)
            (installed.plugin as? EditorPluginLifecycle)?.dispose()
            return true
        }
        return false
    }

    fun installAll(plugins: Iterable<EditorPlugin>) {
        plugins.forEach(::install)
    }
}
