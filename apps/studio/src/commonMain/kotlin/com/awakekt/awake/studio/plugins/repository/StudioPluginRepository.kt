/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins.repository

import com.awakekt.awake.editor.core.plugin.EditorPluginId
import com.awakekt.awake.editor.core.plugin.EditorPluginMetadata
import com.awakekt.awake.editor.core.plugin.PluginInstallResult
import com.awakekt.awake.editor.core.plugin.PluginManifest
import com.awakekt.awake.studio.plugins.discovery.MarketplacePluginEntry
import com.awakekt.awake.studio.plugins.discovery.PluginCategory

/**
 * Listener notified when plugins are installed, uninstalled, or toggled enabled/disabled.
 */
interface PluginLifecycleListener {
    fun onPluginInstalled(manifest: PluginManifest) {}
    fun onPluginUninstalled(id: EditorPluginId) {}
    fun onPluginStateChanged(id: EditorPluginId, enabled: Boolean) {}
}

/**
 * Repository abstraction providing access to marketplace catalog extensions,
 * installed plugins, search/filtering, and installation lifecycle.
 */
interface StudioPluginRepository {
    /** Returns all available extensions in the catalog. */
    fun getCatalog(): List<MarketplacePluginEntry>

    /** Returns currently installed/persisted plugins. */
    fun getInstalled(): List<EditorPluginMetadata>

    /** Searches catalog entries by query string and optional category filter. */
    fun search(query: String, category: PluginCategory = PluginCategory.All): List<MarketplacePluginEntry>

    /** Finds a catalog entry by unique plugin ID. */
    fun findById(id: String): MarketplacePluginEntry?

    /** Installs an extension from its manifest. */
    fun install(manifest: PluginManifest): PluginInstallResult

    /** Uninstalls an installed extension by ID. */
    fun uninstall(id: EditorPluginId): Boolean

    /** Checks whether the installed extension is currently active/enabled. */
    fun isEnabled(id: EditorPluginId): Boolean = true

    /** Sets whether an installed extension is active/enabled. */
    fun setEnabled(id: EditorPluginId, enabled: Boolean): Boolean = false

    /** Registers a lifecycle listener. */
    fun addLifecycleListener(listener: PluginLifecycleListener) {}

    /** Unregisters a lifecycle listener. */
    fun removeLifecycleListener(listener: PluginLifecycleListener) {}
}
