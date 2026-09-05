/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.builder.plugin

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.editor.EditorPlugin
import io.github.awakelab.awake.editor.EditorPluginApi
import io.github.awakelab.awake.editor.EditorPluginId
import io.github.awakelab.awake.editor.EditorPluginMetadata
import io.github.awakelab.awake.editor.EditorProvider
import io.github.awakelab.awake.editor.EditorProviderId
import io.github.awakelab.awake.editor.EditorProviderMetadata
import io.github.awakelab.awake.editor.shell.EditorDockContribution
import io.github.awakelab.awake.editor.shell.EditorDockTab
import io.github.awakelab.awake.ui.builder.state.UiBuilderStore
import io.github.awakelab.awake.ui.builder.ui.UiBuilderHost

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
