/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import kotlin.jvm.JvmInline

/**
 * Stable identity for an installed editor extension artifact.
 *
 * @property value Underlying string identifier.
 */
@JvmInline
value class PluginId(val value: String) {
    init {
        require(value.isNotBlank()) { "A plugin ID must not be blank." }
    }
}

/**
 * Source-compatible plugin contract version.
 *
 * @property value Integer API version number.
 */
@JvmInline
value class PluginApiVersion(val value: Int) {
    init {
        require(value >= 1) { "A plugin API version must be positive." }
    }
}

/**
 * Global constants representing the current plugin API specification.
 */
object PluginApi {
    /** The active plugin API contract version supported by this build. */
    val currentVersion = PluginApiVersion(1)
}

/**
 * Metadata describing an editor plugin and its compatibility requirements.
 *
 * @property id Unique identifier of the plugin.
 * @property displayName Human-readable plugin name.
 * @property version Semantic version string of the plugin.
 * @property requiredApiVersion Plugin API version required by this plugin.
 * @property description Optional human-readable description of plugin functionality.
 */
data class PluginMetadata(
    val id: PluginId,
    val displayName: String,
    val version: String,
    val requiredApiVersion: PluginApiVersion,
    val description: String = "",
) {
    init {
        require(displayName.isNotBlank()) { "A plugin display name must not be blank." }
        require(version.isNotBlank()) { "A plugin version must not be blank." }
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
    /** Metadata detailing plugin identity, version, and requirements. */
    val metadata: PluginMetadata

    /**
     * Instantiates editor providers contributed by this plugin.
     *
     * @return List of newly constructed [EditorProvider] instances.
     */
    fun createProviders(): List<EditorProvider>
}

/**
 * Optional lifecycle for plugins that own resources beyond their providers.
 *
 * Providers are always disposed by [ProviderRegistry]. This hook covers plugin-owned resources
 * such as native handles, background jobs, or caches and runs after the plugin's providers have
 * been unregistered.
 */
interface PluginLifecycle {
    /**
     * Releases plugin-owned resources after all contributed providers have been unregistered.
     */
    fun dispose()
}

/**
 * Host-owned installation registry for explicitly linked [EditorPlugin] instances.
 *
 * This is not a marketplace or runtime loader. Manifest verification, permissions, locks, and
 * Gradle resolution live above this API in the application installer.
 *
 * @param providers Underlying provider registry where plugin providers are registered.
 * @param supportedApiVersion Maximum plugin API version supported by the hosting editor.
 */
class PluginRegistry(
    private val providers: ProviderRegistry,
    private val supportedApiVersion: PluginApiVersion = PluginApi.currentVersion,
) {
    private data class InstalledPlugin(
        val plugin: EditorPlugin,
        val providers: List<EditorProvider>,
    )

    private val installedById = linkedMapOf<PluginId, InstalledPlugin>()

    /** List of metadata for all currently installed plugins. */
    val installed: List<PluginMetadata> get() = installedById.values.map { it.plugin.metadata }

    /**
     * Installs [plugin] into the registry and registers all of its contributed providers.
     *
     * @param plugin Plugin instance to install.
     * @throws IllegalArgumentException If required API version does not match or plugin is already installed.
     */
    fun install(plugin: EditorPlugin) {
        val metadata = plugin.metadata
        require(metadata.requiredApiVersion == supportedApiVersion) {
            "Plugin '${metadata.id.value}' requires API ${metadata.requiredApiVersion.value}, " +
                "but this host supports ${supportedApiVersion.value}."
        }
        require(metadata.id !in installedById) {
            "Plugin '${metadata.id.value}' is already installed."
        }
        val created = plugin.createProviders()
        providers.registerAll(created)
        installedById[metadata.id] = InstalledPlugin(plugin, created)
    }

    /**
     * Uninstalls a plugin by its ID, cleanly disposing and removing all providers it registered.
     */
    fun uninstall(pluginId: PluginId): Boolean {
        val installed = installedById.remove(pluginId)
        if (installed != null) {
            providers.unregisterAll(installed.providers)
            (installed.plugin as? PluginLifecycle)?.dispose()
            return true
        }
        return false
    }

    /**
     * Installs all given [plugins] sequentially into the registry.
     *
     * @param plugins Collection of plugins to install.
     */
    fun installAll(plugins: Iterable<EditorPlugin>) {
        plugins.forEach(::install)
    }
}

// ── Backward-compatible typealiases (compile-time only, zero runtime cost) ───
@Deprecated("Use PluginId", ReplaceWith("PluginId"))
typealias EditorPluginId = PluginId

@Deprecated("Use PluginApiVersion", ReplaceWith("PluginApiVersion"))
typealias EditorPluginApiVersion = PluginApiVersion

@Deprecated("Use PluginApi", ReplaceWith("PluginApi"))
typealias EditorPluginApi = PluginApi

@Deprecated("Use PluginMetadata", ReplaceWith("PluginMetadata"))
typealias EditorPluginMetadata = PluginMetadata

@Deprecated("Use PluginLifecycle", ReplaceWith("PluginLifecycle"))
typealias EditorPluginLifecycle = PluginLifecycle

@Deprecated("Use PluginRegistry", ReplaceWith("PluginRegistry"))
typealias EditorPluginRegistry = PluginRegistry
