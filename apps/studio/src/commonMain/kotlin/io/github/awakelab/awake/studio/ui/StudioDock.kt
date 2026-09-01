/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.studio.ui

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.core.logging.LogRingBuffer
import io.github.awakelab.awake.studio.StudioHostResources
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.editor.LocalEditorProviders
import io.github.awakelab.awake.editor.capability.EditorAnimationPanel
import io.github.awakelab.awake.editor.capability.EditorAssetItem
import io.github.awakelab.awake.editor.capability.EditorAssetPanel
import io.github.awakelab.awake.editor.console.EditorConsolePanel
import io.github.awakelab.awake.editor.shell.EditorDock
import io.github.awakelab.awake.editor.shell.EditorDockTab
import io.github.awakelab.awake.editor.shell.dockContributions
import io.github.awakelab.awake.studio.fixture.StudioFixtureBounds
import io.github.awakelab.awake.studio.state.DOCK_TAB_ASSETS
import io.github.awakelab.awake.studio.state.DOCK_TAB_CONSOLE
import io.github.awakelab.awake.studio.state.DOCK_TAB_FILES
import io.github.awakelab.awake.studio.state.DOCK_TAB_TIMELINE
import io.github.awakelab.awake.studio.state.StudioContract
import io.github.awakelab.awake.studio.state.StudioStore

/**
 * Studio's bottom dock: console, assets, timeline.
 *
 * Composed here rather than configured, which is the shape `AwakePluggableWorkbench` got wrong.
 * That component derived its tabs from data presence -- an empty asset list meant no Assets tab --
 * so a user who deleted their last asset watched a tab disappear. Listing the tabs explicitly means
 * the tab is always there and the *panel* says it is empty, which is what a reader expects and what
 * every editor does.
 *
 * Selection lives in [StudioStore] rather than inside the dock, so it survives recomposition and a
 * host can persist it with the rest of the layout. `EditorDock` is stateless for that reason.
 */
context(composer: Composer)
internal fun StudioDock(store: StudioStore, resources: StudioHostResources) {
    val logBuffer = resources.logBuffer
    val selected = store.state.value.dockTab
    // Read rather than passed: the shell already provides this, and threading it through
    // StudioWorkspace would add a parameter to a function that has no other use for it.
    val providers = LocalEditorProviders.current
    // Studio's own tabs first, plugins after. The host concatenates rather than being handed a
    // merged list: a plugin that could insert itself ahead of the console would be a plugin that
    // can hide the console.
    val contributions = providers.dockContributions()
    EditorDock(
        tabs = listOf(
            EditorDockTab(DOCK_TAB_CONSOLE, "Console"),
            EditorDockTab(DOCK_TAB_ASSETS, "Assets"),
            EditorDockTab(DOCK_TAB_FILES, "Files"),
            EditorDockTab(DOCK_TAB_TIMELINE, "Timeline"),
        ) + contributions.map { it.tab },
        selectedId = selected,
        onSelectedChange = { store.dispatch(StudioContract.Intent.SelectDockTab(it)) },
    ) { tab ->
        when (tab.id) {
            DOCK_TAB_ASSETS -> EditorAssetPanel(
                assets = StudioFixtureBounds.meshIds.map {
                    EditorAssetItem(id = it, name = it, category = "Mesh")
                },
                selectedAssetId = null,
                // Selecting an asset does nothing yet: there is nowhere for it to go until
                // drag-to-viewport or an assign-to-selection action exists. Wired as a no-op
                // rather than omitting the panel, so the gap is visible instead of hidden.
                onSelectAsset = {},
            )

            DOCK_TAB_FILES -> StudioFilesTab(store, resources.files)

            DOCK_TAB_TIMELINE -> EditorAnimationPanel(
                // Studio's fixture has no animation clips. The panel's own empty state says so,
                // which is more honest than a tab that vanishes when there is nothing to show.
                clips = emptyList(),
                selectedClipId = null,
                onSelectClip = {},
                currentTimeSeconds = 0f,
                onSeekSeconds = {},
                isPlaying = false,
                onTogglePlay = {},
            )

            DOCK_TAB_CONSOLE -> EditorConsolePanel(
                logBuffer,
                onClear = logBuffer::clear,
                tag = "studio-console",
            )

            // A tab Studio does not know about is a plugin's. Falling back to the console for an
            // unknown id would show the wrong panel silently; there is nothing sensible to draw,
            // so an unmatched id draws nothing and the tab reads as empty.
            else -> contributions.firstOrNull { it.tab.id == tab.id }?.content()
        }
    }
}
