/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins

import com.awakekt.awake.editor.core.plugin.EditorPlugin
import com.awakekt.awake.editor.core.plugin.EditorPluginApiVersion
import com.awakekt.awake.editor.core.plugin.EditorPluginId
import com.awakekt.awake.editor.core.plugin.EditorPluginMetadata
import com.awakekt.awake.editor.core.plugin.EditorProvider
import com.awakekt.awake.editor.core.plugin.PluginManifest
import com.awakekt.awake.editor.shell.EditorDockTab

/**
 * An [EditorPlugin] instance constructed dynamically from an imported or marketplace [PluginManifest].
 */
class DynamicStudioExtensionPlugin(
    val manifest: PluginManifest,
    override val metadata: EditorPluginMetadata = EditorPluginMetadata(
        id = EditorPluginId(manifest.id),
        displayName = manifest.name,
        version = manifest.version,
        requiredApiVersion = EditorPluginApiVersion(manifest.requiredApiVersion),
    ),
) : EditorPlugin {

    override fun createProviders(): List<EditorProvider> {
        if (!manifest.contributesDockTab) return emptyList()
        val tabId = "ext-${manifest.id.replace('.', '-')}"
        val label = manifest.dockTabTitle?.ifBlank { null } ?: manifest.name
        return listOf(
            DynamicPluginDockContribution(
                tab = EditorDockTab(tabId, label),
                manifest = manifest,
            ),
        )
    }
}
