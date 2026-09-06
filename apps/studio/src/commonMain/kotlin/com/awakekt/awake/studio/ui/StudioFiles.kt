/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.studio.ui

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.editor.panels.files.EditorFileEntry
import com.awakekt.awake.editor.panels.files.EditorFilePanel
import com.awakekt.awake.studio.files.StudioFileOps
import com.awakekt.awake.studio.files.createPlatformFileOps
import com.awakekt.awake.studio.fixture.StudioSceneRegistry
import com.awakekt.awake.studio.state.StudioContract
import com.awakekt.awake.studio.state.StudioStore

internal val STUDIO_FILES = listOf(
    EditorFileEntry(path = "assets/docs/welcome.md", name = "welcome.md"),
) + StudioSceneRegistry.all.map { scene ->
    EditorFileEntry(path = scene.path, name = scene.path.substringAfterLast('/'))
}

/**
 * The bundled and dynamic project file contents.
 */
internal class StudioFileContents(
    private val loaded: MutableMap<String, ByteArray> = mutableMapOf(),
    private val dynamicEntries: MutableList<EditorFileEntry> = mutableListOf(),
    private val fileOps: StudioFileOps = createPlatformFileOps(),
) {

    val allEntries: List<EditorFileEntry>
        get() = STUDIO_FILES + dynamicEntries

    operator fun get(entry: EditorFileEntry): ByteArray? {
        val inMemory = loaded[entry.path]
        if (inMemory != null) return inMemory
        val fromDisk = fileOps.readFileBytes(entry.path)
        if (fromDisk != null) {
            loaded[entry.path] = fromDisk
            return fromDisk
        }
        return null
    }

    suspend fun preload() {
        STUDIO_FILES.forEach { entry ->
            runCatching { loaded[entry.path] = readResourceBytes(entry.path) }
                .onFailure { log.warn { "Bundled file ${entry.path} did not load: ${it.message}" } }
        }
    }

    fun scanProjectDirectory(directoryPath: String): List<EditorFileEntry> {
        val scanned = fileOps.scanDirectory(directoryPath)
        dynamicEntries.clear()
        dynamicEntries.addAll(scanned)
        log.info { "Scanned project directory '$directoryPath': found ${scanned.size} files" }
        return scanned
    }

    fun writeText(path: String, content: String) {
        fileOps.writeText(path, content)
        loaded[path] = content.encodeToByteArray()
    }

    private companion object {
        val log = Logger("studio.files")
    }
}

/** Studio's Files tab: bundled documents & scanned project files, previewed by whichever viewer claims each one. */
context(_: Composer)
internal fun StudioFilesTab(store: StudioStore, contents: StudioFileContents) {
    val selectedPath = store.state.value.selectedFile
    val files = contents.allEntries
    EditorFilePanel(
        files = files,
        selected = files.firstOrNull { it.path == selectedPath },
        onSelect = { store.dispatch(StudioContract.Intent.SelectFile(it.path)) },
        bytesOf = { contents[it] },
    )
}
