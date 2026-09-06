/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins.repository

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.editor.core.plugin.EditorPluginApiVersion
import com.awakekt.awake.editor.core.plugin.EditorPluginId
import com.awakekt.awake.editor.core.plugin.EditorPluginMetadata
import com.awakekt.awake.editor.core.plugin.PluginInstallResult
import com.awakekt.awake.editor.core.plugin.PluginManifest
import com.awakekt.awake.editor.core.plugin.PluginVerificationResult
import com.awakekt.awake.editor.core.plugin.StudioPluginPackage
import com.awakekt.awake.studio.plugins.StudioPluginPersistence
import com.awakekt.awake.studio.plugins.createPlatformPluginPersistence
import com.awakekt.awake.studio.plugins.discovery.MarketplacePluginEntry
import com.awakekt.awake.studio.plugins.discovery.PluginCategory
import com.awakekt.awake.studio.plugins.discovery.PluginDiscovery
import com.awakekt.awake.studio.plugins.discovery.PluginDiscoveryOfficial

/**
 * Production plugin repository combining official/verified catalog discovery,
 * persistent cross-session installed plugin records, and live installation.
 */
class DefaultStudioPluginRepository(
    discovery: PluginDiscovery = PluginDiscoveryOfficial(),
    private val persistence: StudioPluginPersistence = createPlatformPluginPersistence(),
) : StudioPluginRepository {

    private val cachedCatalog = discovery.discover().toMutableList()
    private val disabledIds = persistence.loadDisabledPluginIds().toMutableSet()
    private val listeners = mutableListOf<PluginLifecycleListener>()

    init {
        val persisted = persistence.loadInstalled()
        persisted.forEach { manifest ->
            val existing = cachedCatalog.firstOrNull { it.manifest.id == manifest.id }
            if (existing != null) {
                existing.isInstalled = true
                existing.isEnabled = manifest.id !in disabledIds
            } else {
                cachedCatalog.add(
                    MarketplacePluginEntry(
                        manifest = manifest,
                        isInstalled = true,
                        isEnabled = manifest.id !in disabledIds,
                    ),
                )
            }
        }
    }

    override fun getCatalog(): List<MarketplacePluginEntry> = cachedCatalog.toList()

    override fun getInstalled(): List<EditorPluginMetadata> {
        val persisted = persistence.loadInstalled()
        return persisted.map { manifest ->
            EditorPluginMetadata(
                id = EditorPluginId(manifest.id),
                displayName = manifest.name,
                version = manifest.version,
                requiredApiVersion = EditorPluginApiVersion(manifest.requiredApiVersion),
            )
        }
    }

    override fun search(query: String, category: PluginCategory): List<MarketplacePluginEntry> {
        val q = query.trim()
        return cachedCatalog.filter { entry ->
            val matchesCategory = category == PluginCategory.All || entry.category == category ||
                (category == PluginCategory.Pro && entry.isPro) ||
                (category == PluginCategory.Community && !entry.isPro)
            val matchesQuery = q.isEmpty() ||
                entry.manifest.name.contains(q, ignoreCase = true) ||
                entry.manifest.description.contains(q, ignoreCase = true) ||
                entry.tags.any { it.contains(q, ignoreCase = true) } ||
                entry.manifest.id.contains(q, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    override fun findById(id: String): MarketplacePluginEntry? =
        cachedCatalog.firstOrNull { it.manifest.id == id }

    private companion object {
        val log = Logger("studio.repository")
    }

    override fun install(manifest: PluginManifest): PluginInstallResult {
        log.info { "Marketplace package installed: ${manifest.id} v${manifest.version}" }
        val entry = findById(manifest.id)
        if (entry != null) {
            entry.isInstalled = true
            entry.isEnabled = true
        } else {
            cachedCatalog.add(
                MarketplacePluginEntry(
                    manifest = manifest,
                    isInstalled = true,
                    isEnabled = true,
                ),
            )
        }
        disabledIds.remove(manifest.id)
        persistence.saveDisabledPluginIds(disabledIds)
        val current = persistence.loadInstalled().toMutableList()
        current.removeAll { it.id == manifest.id }
        current.add(manifest)
        persistence.saveInstalled(current)

        listeners.forEach { it.onPluginInstalled(manifest) }

        return PluginInstallResult.Success(
            StudioPluginPackage(
                manifest = manifest,
                verification = PluginVerificationResult.Verified(manifest.author),
            ),
        )
    }

    override fun uninstall(id: EditorPluginId): Boolean {
        log.info { "Marketplace package uninstalled: ${id.value}" }
        val entry = findById(id.value)
        val wasInstalled = entry?.isInstalled == true
        if (entry != null) {
            entry.isInstalled = false
            entry.isEnabled = false
        }
        disabledIds.remove(id.value)
        persistence.saveDisabledPluginIds(disabledIds)
        val current = persistence.loadInstalled().toMutableList()
        val removed = current.removeAll { it.id == id.value }
        if (removed) {
            persistence.saveInstalled(current)
        }
        if (wasInstalled || removed) {
            listeners.forEach { it.onPluginUninstalled(id) }
            return true
        }
        return false
    }

    override fun isEnabled(id: EditorPluginId): Boolean =
        id.value !in disabledIds

    override fun setEnabled(id: EditorPluginId, enabled: Boolean): Boolean {
        log.info { "Plugin '${id.value}' state updated: enabled=$enabled" }
        val entry = findById(id.value) ?: return false
        if (!entry.isInstalled) return false

        entry.isEnabled = enabled
        if (enabled) {
            disabledIds.remove(id.value)
        } else {
            disabledIds.add(id.value)
        }
        persistence.saveDisabledPluginIds(disabledIds)
        listeners.forEach { it.onPluginStateChanged(id, enabled) }
        return true
    }

    override fun addLifecycleListener(listener: PluginLifecycleListener) {
        if (listener !in listeners) {
            listeners.add(listener)
        }
    }

    override fun removeLifecycleListener(listener: PluginLifecycleListener) {
        listeners.remove(listener)
    }
}
