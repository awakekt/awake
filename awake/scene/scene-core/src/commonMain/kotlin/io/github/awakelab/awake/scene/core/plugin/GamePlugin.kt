/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.core.plugin

import io.github.awakelab.awake.ecs.World
import kotlin.jvm.JvmInline

/**
 * Stable identifier for an installed game runtime plugin.
 */
@JvmInline
value class PluginId(
    /** The raw unique string identifier for this plugin. */
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "A plugin ID must not be blank." }
    }
}

/**
 * Metadata descriptor for a game plugin.
 *
 * @property id Unique identifier for this plugin (e.g. "awake.physics").
 * @property displayName Human-readable display name.
 * @property version Version string (e.g. "1.0.0").
 * @property description Short description of what this plugin provides.
 */
data class PluginMetadata(
    val id: PluginId,
    val displayName: String,
    val version: String,
    val description: String = "",
) {
    init {
        require(displayName.isNotBlank()) { "A plugin display name must not be blank." }
        require(version.isNotBlank()) { "A plugin version must not be blank." }
    }
}

/**
 * Build-time linked runtime plugin contract for the Awake microkernel engine.
 *
 * A [GamePlugin] installs systems, event listeners, or initial component configurations
 * into an active [World]. It carries zero editor or Compose dependencies, ensuring it runs
 * safely across Desktop, Mobile, WebAssembly, and headless dedicated servers.
 */
interface GamePlugin {
    /** The metadata descriptor identifying this plugin. */
    val metadata: PluginMetadata

    /**
     * Installs systems, entity factories, or resources into [world].
     */
    fun install(world: World)
}

/**
 * Registry holding and managing installed [GamePlugin] instances for an application or scene.
 */
class GamePluginRegistry {
    private val installedById = linkedMapOf<PluginId, GamePlugin>()

    /** Read-only view of all installed plugins in installation order. */
    val installed: List<GamePlugin> get() = installedById.values.toList()

    /**
     * Installs [plugin]. If [world] is non-null, immediately calls [GamePlugin.install].
     *
     * @throws IllegalArgumentException if a plugin with the same ID is already installed.
     */
    fun install(plugin: GamePlugin, world: World? = null) {
        val id = plugin.metadata.id
        require(id !in installedById) {
            "Plugin '${id.value}' is already installed."
        }
        installedById[id] = plugin
        world?.let { plugin.install(it) }
    }

    /**
     * Installs all [plugins] into this registry.
     */
    fun installAll(plugins: Iterable<GamePlugin>, world: World? = null) {
        plugins.forEach { install(it, world) }
    }

    /**
     * Checks if a plugin with [id] is installed.
     */
    fun isInstalled(id: PluginId): Boolean = id in installedById

    /**
     * Retrieves the installed plugin with [id], or null if not found.
     */
    fun getPlugin(id: PluginId): GamePlugin? = installedById[id]
}
