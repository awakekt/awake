/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.builder.plugin

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.editor.EditorPlugin
import com.awakekt.awake.editor.EditorPluginApi
import com.awakekt.awake.editor.EditorPluginId
import com.awakekt.awake.editor.EditorPluginMetadata
import com.awakekt.awake.editor.EditorProvider
import com.awakekt.awake.editor.EditorProviderId
import com.awakekt.awake.editor.EditorProviderMetadata
import com.awakekt.awake.editor.shell.EditorDockContribution
import com.awakekt.awake.editor.shell.EditorDockTab
import com.awakekt.awake.ui.builder.state.UiBuilderStore
import com.awakekt.awake.ui.builder.ui.UiBuilderHost

/**
 * Dock contribution making the UI Builder available in an editor host's dock.
 */
class UiBuilderDockContribution(
    private val store: UiBuilderStore = UiBuilderStore(),
) : EditorDockContribution {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("awake.ui.builder.dock"),
        displayName = "UI Builder",
    )

    override val tab = EditorDockTab("ui_builder", "UI Builder")

    context(_: Composer)
    override fun content() {
        UiBuilderHost(store = store)
    }
}

/**
 * Editor plugin contributing the UI Builder to an installed [EditorPluginRegistry].
 */
class UiBuilderEditorPlugin(
    private val store: UiBuilderStore = UiBuilderStore(),
) : EditorPlugin {
    override val metadata = EditorPluginMetadata(
        id = EditorPluginId("awake.ui.builder"),
        displayName = "UI Builder",
        version = "1.0.0",
        requiredApiVersion = EditorPluginApi.currentVersion,
    )

    override fun createProviders(): List<EditorProvider> = listOf(
        UiBuilderDockContribution(store),
    )
}
