/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.ui

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.editor.EditorPlugin
import io.github.awakelab.awake.editor.EditorPluginApi
import io.github.awakelab.awake.editor.EditorPluginId
import io.github.awakelab.awake.editor.EditorPluginMetadata
import io.github.awakelab.awake.editor.EditorPluginRegistry
import io.github.awakelab.awake.editor.EditorProvider
import io.github.awakelab.awake.editor.EditorProviderId
import io.github.awakelab.awake.editor.EditorProviderMetadata
import io.github.awakelab.awake.editor.panels.files.EditorFileEntry
import io.github.awakelab.awake.editor.panels.files.EditorFileViewer
import io.github.awakelab.awake.studio.StudioHostResources
import io.github.awakelab.awake.studio.state.DOCK_TAB_FILES
import io.github.awakelab.awake.studio.state.StudioContract
import io.github.awakelab.awake.studio.state.StudioEditorBridge
import io.github.awakelab.awake.studio.state.StudioStore
import io.github.awakelab.awake.studio.studioHostResources
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The file-viewer seam as a user would meet it: Studio's Files tab, with and without an add-on.
 *
 * Rendered through the whole shell rather than the panel alone, because the panel's own test already
 * proves the lookup and what remains to go wrong is the host -- a tab never listed, or a viewer never
 * registered, both of which leave a working seam nobody can reach.
 */
class StudioFilesTabTest {

    /** What a Markdown add-on registers. Renders headings rather than showing the `#`. */
    private class MarkdownViewer : EditorFileViewer {
        override val metadata = EditorProviderMetadata(
            id = EditorProviderId("sample.markdown.viewer"),
            displayName = "Markdown",
        )

        override fun handles(entry: EditorFileEntry): Boolean = entry.extension == "md"

        context(_: Composer)
        override fun view(entry: EditorFileEntry, bytes: ByteArray) {
            bytes.decodeToString().lineSequence().forEach { line ->
                ShadcnText(if (line.startsWith("# ")) line.removePrefix("# ") else line)
            }
        }
    }

    private class MarkdownPlugin : EditorPlugin {
        override val metadata = EditorPluginMetadata(
            id = EditorPluginId("sample.markdown"),
            displayName = "Markdown Viewer",
            version = "1.0.0",
            requiredApiVersion = EditorPluginApi.currentVersion,
        )

        override fun createProviders(): List<EditorProvider> = listOf(MarkdownViewer())
    }

    private val markdown = STUDIO_FILES.first { it.extension == "md" }
    private val json = STUDIO_FILES.first { it.extension == "json" }

    private fun render(selected: EditorFileEntry, install: Boolean): List<String> {
        val store = StudioStore()
        val bridge = StudioEditorBridge(store)
        if (install) EditorPluginRegistry(bridge.providers).install(MarkdownPlugin())
        store.dispatch(StudioContract.Intent.SelectDockTab(DOCK_TAB_FILES))
        store.dispatch(StudioContract.Intent.SelectFile(selected.path))
        val resources: StudioHostResources = studioHostResources(
            files = StudioFileContents(
                STUDIO_FILES.associate { it.path to "# Heading of ${it.name}".encodeToByteArray() }.toMutableMap(),
            ),
        )
        val host = StudioTestHost(World(), width = 1440, height = 900)
        // Twice: the first frame has no placed tree, and the second is the steady state.
        host.frame { StudioShell(store, bridge, backend = "Vulkan", resources = resources) }
        val frame = host.frame { StudioShell(store, bridge, backend = "Vulkan", resources = resources) }

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
    fun theShippedViewerOpensABundledTextFile() {
        val labels = render(json, install = false)

        assertTrue(
            labels.any { it.contains("Heading of ${json.name}") },
            "the built-in text viewer did not draw the file: $labels",
        )
    }

    /** The marketplace claim: Studio cannot show Markdown until an add-on teaches it how. */
    @Test
    fun markdownNeedsAnAddOn() {
        val before = render(markdown, install = false)
        assertTrue("No viewer for .md" in before, "Studio should admit it cannot draw this: $before")

        val after = render(markdown, install = true)
        assertTrue(
            after.any { it == "Heading of ${markdown.name}" },
            "the add-on's viewer did not draw the heading: $after",
        )
        assertTrue("No viewer for .md" !in after, "the add-on was installed and still not used")
    }
}
