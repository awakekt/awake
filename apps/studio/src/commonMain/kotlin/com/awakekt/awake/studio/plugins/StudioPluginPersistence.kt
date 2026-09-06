/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins

import com.awakekt.awake.editor.core.plugin.PluginManifest

/**
 * Persistence contract for installed Awake Studio extensions across sessions.
 */
interface StudioPluginPersistence {
    /**
     * Loads the list of saved plugin manifests from storage.
     */
    fun loadInstalled(): List<PluginManifest>

    /**
     * Saves the list of installed plugin manifests to storage.
     */
    fun saveInstalled(manifests: List<PluginManifest>)

    /**
     * Loads the set of disabled plugin IDs.
     */
    fun loadDisabledPluginIds(): Set<String> = emptySet()

    /**
     * Saves the set of disabled plugin IDs to storage.
     */
    fun saveDisabledPluginIds(ids: Set<String>) {}
}

/**
 * In-memory persistence implementation suitable for tests and ephemeral environments.
 */
class InMemoryStudioPluginPersistence(
    initial: List<PluginManifest> = emptyList(),
    initialDisabled: Set<String> = emptySet(),
) : StudioPluginPersistence {
    private val installed = initial.toMutableList()
    private val disabled = initialDisabled.toMutableSet()

    override fun loadInstalled(): List<PluginManifest> = installed.toList()

    override fun saveInstalled(manifests: List<PluginManifest>) {
        installed.clear()
        installed.addAll(manifests)
    }

    override fun loadDisabledPluginIds(): Set<String> = disabled.toSet()

    override fun saveDisabledPluginIds(ids: Set<String>) {
        disabled.clear()
        disabled.addAll(ids)
    }
}

/**
 * Creates the platform-appropriate [StudioPluginPersistence].
 */
expect fun createPlatformPluginPersistence(): StudioPluginPersistence
