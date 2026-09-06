/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins.discovery

import com.awakekt.awake.editor.core.plugin.PluginManifest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Filter categories for Studio Marketplace discovery.
 */
@Serializable
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
@Serializable
data class MarketplacePluginEntry(
    val manifest: PluginManifest,
    val category: PluginCategory = PluginCategory.Tools,
    val isPro: Boolean = false,
    var isInstalled: Boolean = false,
    var isEnabled: Boolean = true,
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

/**
 * Discovery service that parses a JSON catalog payload (e.g. from an HTTP endpoint or remote bundle),
 * gracefully falling back to [fallback] on network/parse failure.
 */
class JsonPluginDiscovery(
    private val jsonProvider: () -> String?,
    private val fallback: PluginDiscovery = PluginDiscoveryOfficial(),
) : PluginDiscovery {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun discover(): List<MarketplacePluginEntry> {
        val payload = try {
            jsonProvider()
        } catch (_: Throwable) {
            null
        }
        if (payload.isNullOrBlank()) return fallback.discover()
        return try {
            json.decodeFromString<List<MarketplacePluginEntry>>(payload)
        } catch (_: Throwable) {
            fallback.discover()
        }
    }
}
