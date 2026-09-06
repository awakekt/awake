/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.state

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.editor.EditorPlugin
import com.awakekt.awake.editor.EditorPluginApi
import com.awakekt.awake.editor.EditorPluginId
import com.awakekt.awake.editor.EditorPluginMetadata
import com.awakekt.awake.editor.EditorPluginRegistry
import com.awakekt.awake.editor.EditorProvider
import com.awakekt.awake.editor.EditorProviderId
import com.awakekt.awake.editor.EditorProviderMetadata
import com.awakekt.awake.editor.EditorProviders
import com.awakekt.awake.editor.shell.EditorDockContribution
import com.awakekt.awake.editor.shell.EditorDockTab
import com.awakekt.awake.editor.shell.dockContributions
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The dock's selection is host state.
 *
 * `AwakePluggableWorkbench` kept it in a `var` inside the component, which is why it could not
 * survive a recomposition or be persisted with the rest of a layout. `EditorDock` is stateless and
 * the store owns the selection instead, so these assertions are possible at all.
 */
class StudioDockStateTest {

    @Test
    fun theConsoleIsSelectedUntilSomethingSelectsOtherwise() {
        assertEquals(DOCK_TAB_CONSOLE, StudioStore().state.value.dockTab)
    }

    @Test
    fun selectingATabIsRecordedAndDoesNotEmitAnEffect() {
        val store = StudioStore()

        store.dispatch(StudioContract.Intent.SelectDockTab(DOCK_TAB_ASSETS))
        assertEquals(DOCK_TAB_ASSETS, store.state.value.dockTab)

        store.dispatch(StudioContract.Intent.SelectDockTab(DOCK_TAB_TIMELINE))
        assertEquals(DOCK_TAB_TIMELINE, store.state.value.dockTab)

        // Choosing a tab changes what is shown and nothing else. An effect here would mean the
        // reducer was asking the application to do something, which is how a view preference ends
        // up reloading a scene.
        assertEquals(emptyList(), store.drainEffects())
    }

    @Test
    fun selectingATabDoesNotDisturbTheRestOfTheState() {
        val store = StudioStore()
        store.dispatch(StudioContract.Intent.SceneSaved("scene.json"))

        store.dispatch(StudioContract.Intent.SelectDockTab(DOCK_TAB_ASSETS))

        assertEquals("scene.json", store.state.value.lastSavedTo, "an unrelated field moved")
    }

    /**
     * A plugin's tab reaches Studio's dock without Studio knowing about it.
     *
     * Studio lists Console, Assets and Timeline in code. Everything after those comes from
     * installed plugins, which is the whole marketplace claim: install an addon, get a tab.
     */
    @Test
    fun studioShowsItsOwnTabsFirstAndPluginTabsAfter() {
        val providers = EditorProviders()
        EditorPluginRegistry(providers).install(MarkdownPlugin())

        val contributed = providers.dockContributions().map { it.tab.id }

        assertEquals(listOf("markdown"), contributed, "the registry did not surface the plugin's tab")
    }
}

private class MarkdownDock : EditorDockContribution {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("sample.markdown"),
        displayName = "Markdown",
    )
    override val tab = EditorDockTab("markdown", "Markdown")

    context(_: Composer)
    override fun content() = Unit
}

private class MarkdownPlugin : EditorPlugin {
    override val metadata = EditorPluginMetadata(
        id = EditorPluginId("sample.markdown"),
        displayName = "Markdown Viewer",
        version = "1.0.0",
        requiredApiVersion = EditorPluginApi.currentVersion,
    )

    override fun createProviders(): List<EditorProvider> = listOf(MarkdownDock())
}
