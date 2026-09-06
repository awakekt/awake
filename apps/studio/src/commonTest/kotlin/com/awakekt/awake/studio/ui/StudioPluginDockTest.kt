/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.ui

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.ecs.World
import com.awakekt.awake.editor.EditorPlugin
import com.awakekt.awake.editor.EditorPluginApi
import com.awakekt.awake.editor.EditorPluginId
import com.awakekt.awake.editor.EditorPluginMetadata
import com.awakekt.awake.editor.EditorPluginRegistry
import com.awakekt.awake.editor.EditorProvider
import com.awakekt.awake.editor.EditorProviderId
import com.awakekt.awake.editor.EditorProviderMetadata
import com.awakekt.awake.editor.shell.EditorDockContribution
import com.awakekt.awake.editor.shell.EditorDockTab
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioStore
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The marketplace claim, rendered: install a plugin, and its tab is in Studio's dock.
 *
 * The registry test next door proves the contribution is *found*. This proves a user would see it,
 * which is the part that would still be false if the host forgot to merge the list -- and the host
 * forgetting to wire a seam is how `viewportPicker`, `EditorProviders` and the capability panels
 * each ended up reachable from nothing.
 */
class StudioPluginDockTest {

    private class MarkdownDock : EditorDockContribution {
        override val metadata = EditorProviderMetadata(
            id = EditorProviderId("sample.markdown"),
            displayName = "Markdown",
        )
        override val tab = EditorDockTab("markdown", "Markdown")

        context(_: Composer)
        override fun content() {
            ShadcnText("# rendered by the plugin")
        }
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

    private fun render(install: Boolean): List<String> {
        val world = World()
        val store = StudioStore()
        val bridge = StudioEditorBridge(store)
        if (install) EditorPluginRegistry(bridge.providers).install(MarkdownPlugin())
        val host = StudioTestHost(world, width = 1440, height = 900)
        // Twice: the first frame has no placed tree, and the second is the steady state.
        host.frame { StudioShell(store, bridge, backend = "Vulkan") }
        val frame = host.frame { StudioShell(store, bridge, backend = "Vulkan") }

        val labels = mutableListOf<String>()
        fun walk(nodes: List<SemanticsNode>) {
            nodes.forEach { node ->
                node.label?.let { labels += it }
                walk(node.children)
            }
        }
        walk(frame.semantics)
        return labels
    }

    @Test
    fun aPluginsTabAppearsInTheDock() {
        assertNotNull(
            render(install = true).firstOrNull { it == "Markdown" },
            "installing the plugin did not put its tab in Studio's dock",
        )
    }

    /**
     * The control. Without it, a "Markdown" label from anywhere else in the shell would make the
     * test above pass whether or not the seam works.
     */
    @Test
    fun withoutThePluginThereIsNoSuchTab() {
        assertNull(
            render(install = false).firstOrNull { it == "Markdown" },
            "a tab appeared with no plugin installed",
        )
    }
}
