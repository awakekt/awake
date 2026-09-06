/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.files

import com.awakekt.awake.editor.panels.files.EditorFileEntry
import java.io.File

actual fun createPlatformFileOps(): StudioFileOps = DesktopStudioFileOps()

internal class DesktopStudioFileOps : StudioFileOps {
    override fun scanDirectory(directoryPath: String): List<EditorFileEntry> {
        val root = File(directoryPath)
        if (!root.exists() || !root.isDirectory) return emptyList()
        val list = mutableListOf<EditorFileEntry>()
        val supportedExtensions = setOf("json", "awakescene", "glb", "gltf", "md", "txt", "png", "jpg", "obj", "fbx")
        root.walkTopDown()
            .maxDepth(5)
            .filter { file ->
                file.isFile && !file.name.startsWith(".") &&
                    (file.extension.lowercase() in supportedExtensions || file.name.contains('.'))
            }
            .forEach { file ->
                val relPath = file.relativeTo(root).path.replace('\\', '/')
                list.add(EditorFileEntry(path = file.absolutePath, name = relPath))
            }
        return list
    }

    override fun readFileBytes(path: String): ByteArray? {
        val file = File(path)
        return if (file.exists() && file.isFile) {
            try {
                file.readBytes()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    override fun writeText(path: String, content: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
