/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.files

import com.awakekt.awake.editor.panels.files.EditorFileEntry

/**
 * File operations contract for scanning project files and reading/writing documents on disk.
 */
interface StudioFileOps {
    fun scanDirectory(directoryPath: String): List<EditorFileEntry>
    fun readFileBytes(path: String): ByteArray?
    fun writeText(path: String, content: String)
}

expect fun createPlatformFileOps(): StudioFileOps

/**
 * In-memory fallback file operations implementation for testing and browser runtime.
 */
class InMemoryStudioFileOps(
    private val files: MutableMap<String, ByteArray> = mutableMapOf(),
) : StudioFileOps {
    override fun scanDirectory(directoryPath: String): List<EditorFileEntry> =
        files.keys
            .filter { it.startsWith(directoryPath) }
            .map { EditorFileEntry(path = it, name = it.substringAfterLast('/')) }

    override fun readFileBytes(path: String): ByteArray? = files[path]

    override fun writeText(path: String, content: String) {
        files[path] = content.encodeToByteArray()
    }
}
