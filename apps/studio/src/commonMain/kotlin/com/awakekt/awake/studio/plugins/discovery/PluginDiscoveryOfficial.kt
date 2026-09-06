/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins.discovery

import com.awakekt.awake.editor.core.plugin.PluginManifest

/**
 * Official Awake Studio extension catalog discovery service.
 *
 * Provides curated first-party and verified community plugins for Awake Studio.
 */
class PluginDiscoveryOfficial : PluginDiscovery {

    private val catalog: List<MarketplacePluginEntry> = emptyList()

    override fun discover(): List<MarketplacePluginEntry> = catalog
}
