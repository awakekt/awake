/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.studio.ui

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.core.host.readResourceBytes
import io.github.awakelab.awake.core.logging.Logger
import io.github.awakelab.awake.editor.files.EditorFileEntry
import io.github.awakelab.awake.editor.files.EditorFilePanel
import io.github.awakelab.awake.studio.state.StudioContract
import io.github.awakelab.awake.studio.state.StudioStore

/**
 * The files Studio offers to open.
 *
 * A fixed list, because there is no directory to walk: [readResourceBytes] is the whole of the
 * engine's file access and wasmJs has no filesystem, so a host knows what it bundled and nothing
 * more. The Markdown file is deliberately one the shipped viewer does not claim -- it is what makes
 * the plugin seam visible in the running app rather than only in a test.
 */
internal val STUDIO_FILES = listOf(
    EditorFileEntry(path = "assets/docs/welcome.md", name = "welcome.md"),
    EditorFileEntry(path = "assets/examples/rotating-cube.scene.json", name = "rotating-cube.scene.json"),
)

/**
 * The bundled file contents, read once at startup.
 *
 * Read ahead of time rather than on selection because [readResourceBytes] suspends -- on the web it
 * is a `fetch` -- and composition cannot wait. The panel reads whatever has arrived; a file that
 * failed to load simply stays absent, and the panel keeps saying it is loading.
 */
internal class StudioFileContents(private val loaded: MutableMap<String, ByteArray> = mutableMapOf()) {

    operator fun get(entry: EditorFileEntry): ByteArray? = loaded[entry.path]

    suspend fun preload() {
        STUDIO_FILES.forEach { entry ->
            runCatching { loaded[entry.path] = readResourceBytes(entry.path) }
                .onFailure { log.warn { "Bundled file ${entry.path} did not load: ${it.message}" } }
        }
    }

    private companion object {
        val log = Logger("studio-files")
    }
}

/** Studio's Files tab: the bundled documents, previewed by whichever viewer claims each one. */
context(_: Composer)
internal fun StudioFilesTab(store: StudioStore, contents: StudioFileContents) {
    val selectedPath = store.state.value.selectedFile
    EditorFilePanel(
        files = STUDIO_FILES,
        selected = STUDIO_FILES.firstOrNull { it.path == selectedPath },
        onSelect = { store.dispatch(StudioContract.Intent.SelectFile(it.path)) },
        bytesOf = { contents[it] },
    )
}
