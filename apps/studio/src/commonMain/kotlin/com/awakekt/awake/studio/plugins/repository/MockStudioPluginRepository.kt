/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins.repository

import com.awakekt.awake.editor.core.plugin.EditorPluginApiVersion
import com.awakekt.awake.editor.core.plugin.EditorPluginId
import com.awakekt.awake.editor.core.plugin.EditorPluginMetadata
import com.awakekt.awake.editor.core.plugin.PluginInstallResult
import com.awakekt.awake.editor.core.plugin.PluginManifest
import com.awakekt.awake.editor.core.plugin.PluginVerificationResult
import com.awakekt.awake.editor.core.plugin.StudioPluginPackage
import com.awakekt.awake.studio.plugins.discovery.MarketplacePluginEntry
import com.awakekt.awake.studio.plugins.discovery.PluginCategory

/**
 * In-memory test repository pre-seeded with mock extensions or custom test fixtures.
 * Enables zero-I/O, deterministic testing without disk or network access.
 */
class MockStudioPluginRepository(
    initialCatalog: List<MarketplacePluginEntry> = emptyList(),
    initialInstalled: List<EditorPluginMetadata> = emptyList(),
) : StudioPluginRepository {

    private val catalogList = initialCatalog.toMutableList()
    private val installedList = initialInstalled.toMutableList()

    override fun getCatalog(): List<MarketplacePluginEntry> = catalogList.toList()

    override fun getInstalled(): List<EditorPluginMetadata> = installedList.toList()

    override fun search(query: String, category: PluginCategory): List<MarketplacePluginEntry> {
        val q = query.trim()
        return catalogList.filter { entry ->
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
        catalogList.firstOrNull { it.manifest.id == id }

    override fun install(manifest: PluginManifest): PluginInstallResult {
        val entry = findById(manifest.id)
        if (entry != null) {
            entry.isInstalled = true
        } else {
            catalogList.add(MarketplacePluginEntry(manifest = manifest, isInstalled = true))
        }
        if (installedList.none { it.id.value == manifest.id }) {
            installedList.add(
                EditorPluginMetadata(
                    id = EditorPluginId(manifest.id),
                    displayName = manifest.name,
                    version = manifest.version,
                    requiredApiVersion = EditorPluginApiVersion(manifest.requiredApiVersion),
                ),
            )
        }
        return PluginInstallResult.Success(
            StudioPluginPackage(
                manifest = manifest,
                verification = PluginVerificationResult.Verified(manifest.author),
            ),
        )
    }

    override fun uninstall(id: EditorPluginId): Boolean {
        val removed = installedList.removeAll { it.id == id }
        findById(id.value)?.let { it.isInstalled = false }
        return removed
    }

    fun addCatalogEntry(entry: MarketplacePluginEntry) {
        catalogList.add(entry)
    }
}
