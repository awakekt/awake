/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.plugins.discovery

import io.github.awakelab.awake.editor.core.plugin.PluginManifest

/**
 * Official Awake Studio extension catalog discovery service.
 *
 * Provides curated first-party and verified community plugins for Awake Studio.
 */
class PluginDiscoveryOfficial : PluginDiscovery {

    private val catalog: List<MarketplacePluginEntry> = listOf(
        MarketplacePluginEntry(
            manifest = PluginManifest(
                id = "awake.terrain",
                name = "Pro Terrain Generator",
                version = "1.2.0",
                author = "Awake Studio",
                description = "Procedural heightfield terrain, hydraulic erosion filters, and multi-biome splat painter.",
            ),
            category = PluginCategory.Rendering,
            isPro = true,
            isInstalled = true,
            tags = listOf("terrain", "heightfield", "erosion", "pro"),
            detailedDescription = "Complete AAA procedural terrain tool suite including GPU clipmaps, brush-based height sculpting, hydraulic & thermal erosion simulation, and automatic texture splatting.",
            documentationUrl = "https://github.com/awake-engine/awake/docs/terrain",
        ),
        MarketplacePluginEntry(
            manifest = PluginManifest(
                id = "awake.dialogue",
                name = "Visual Dialogue Node Graph",
                version = "1.0.0",
                author = "Awake Studio",
                description = "Visual drag-and-drop conversation branching, quest triggers, and character expression editor.",
            ),
            category = PluginCategory.Gameplay,
            isPro = true,
            tags = listOf("dialogue", "nodes", "quest", "narrative"),
            detailedDescription = "Node-based branching dialogue system supporting actor portraits, audio cue triggers, localization tokens, and quest state condition evaluation.",
            documentationUrl = "https://github.com/awake-engine/awake/docs/dialogue",
        ),
        MarketplacePluginEntry(
            manifest = PluginManifest(
                id = "awake.shader-graph",
                name = "Visual Shader Graph Editor",
                version = "1.1.0",
                author = "Awake Studio",
                description = "Interactive node-based WGSL procedural shader graph compiler and material generator.",
            ),
            category = PluginCategory.Rendering,
            isPro = true,
            tags = listOf("shader", "wgsl", "materials", "vfx"),
            detailedDescription = "Visual material authoring tool compiling node graphs directly into optimized WGSL and SPIR-V shaders with live viewport preview.",
            documentationUrl = "https://github.com/awake-engine/awake/docs/shader-graph",
        ),
        MarketplacePluginEntry(
            manifest = PluginManifest(
                id = "awake.behavior-tree",
                name = "Visual Behavior Tree AI",
                version = "1.0.2",
                author = "Awake Studio",
                description = "Hierarchical behavior tree designer with live AI debugging and breakpoint stepping.",
            ),
            category = PluginCategory.Gameplay,
            isPro = true,
            tags = listOf("ai", "behavior", "nodes", "simulation"),
            detailedDescription = "Full visual AI behavior tree authoring tool with decorators, selectors, sequences, and runtime visual execution inspection.",
            documentationUrl = "https://github.com/awake-engine/awake/docs/behavior-tree",
        ),
        MarketplacePluginEntry(
            manifest = PluginManifest(
                id = "community.lod-gen",
                name = "Auto Mesh LOD Generator",
                version = "0.9.0",
                author = "Acme Tools",
                description = "Automatic 3D mesh polygon decimation and hierarchical LOD chain generator.",
            ),
            category = PluginCategory.Tools,
            isPro = false,
            tags = listOf("mesh", "lod", "optimization", "geometry"),
            detailedDescription = "Automated quadric mesh decimation tool that generates LOD0 to LOD4 chains on import with custom error thresholds.",
            documentationUrl = "https://github.com/acme-tools/awake-lod-gen",
        ),
        MarketplacePluginEntry(
            manifest = PluginManifest(
                id = "community.pixel-art",
                name = "Pixel Art Post-Processing",
                version = "1.0.1",
                author = "RetroWorks",
                description = "Retro CRT rasterizer, palette quantizer, and dithering post-processing pass.",
            ),
            category = PluginCategory.Rendering,
            isPro = false,
            tags = listOf("pixel-art", "retro", "post-process", "shaders"),
            detailedDescription = "Fullscreen custom render pass providing integer scaling, Bayer matrix dithering, and custom 16-color palette quantization.",
            documentationUrl = "https://github.com/retroworks/awake-pixel-art",
        ),
    )

    override fun discover(): List<MarketplacePluginEntry> = catalog
}
