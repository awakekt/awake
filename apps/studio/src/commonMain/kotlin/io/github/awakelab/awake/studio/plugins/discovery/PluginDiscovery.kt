/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.plugins.discovery

import io.github.awakelab.awake.editor.core.plugin.PluginManifest

/**
 * Filter categories for Studio Marketplace discovery.
 */
enum class PluginCategory(val label: String) {
    All("All"),
    Tools("Tools"),
    Rendering("Rendering"),
    Gameplay("Gameplay"),
    Pro("Pro Tier"),
    Community("Community"),
}

/**
 * Featured Studio Marketplace extension item metadata.
 */
data class MarketplacePluginEntry(
    val manifest: PluginManifest,
    val category: PluginCategory = PluginCategory.Tools,
    val isPro: Boolean = false,
    var isInstalled: Boolean = false,
    val tags: List<String> = emptyList(),
    val detailedDescription: String = "",
    val documentationUrl: String = "",
)

/**
 * Contract for discovering Studio extension packages from registries, catalogs, or disk.
 */
interface PluginDiscovery {
    fun discover(): List<MarketplacePluginEntry>

    fun findById(id: String): MarketplacePluginEntry? =
        discover().firstOrNull { it.manifest.id == id }
}
