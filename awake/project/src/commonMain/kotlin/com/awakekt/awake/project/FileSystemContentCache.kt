/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.FileKind
import com.awakekt.awake.core.io.FilePath
import com.awakekt.awake.core.io.FileSystem
import com.awakekt.awake.core.io.WriteMode

/**
 * Caches [IndexedAssetSource]'s verified files on a [FileSystem], keyed by SHA-256.
 *
 * Files are sharded by their first two hex characters (e.g. `ca/cafe...`) to avoid flat directory limits.
 *
 * The cache never fails a load: storage failures (full disk, permission errors, etc.) drop silently
 * so the engine continues seamlessly with the fetched bytes.
 *
 * @param fileSystem The filesystem rooted at the cache directory.
 * @param namespace Subdirectory under [fileSystem] root separating projects, or null to use the root directly.
 */
class FileSystemContentCache(
    private val fileSystem: FileSystem,
    private val namespace: String? = null,
) : ContentCache {

    init {
        namespace?.let {
            require(it.isNotBlank() && !it.contains('/') && !it.contains('\\')) {
                "Namespace must be a single directory name without slashes, not \"$it\"."
            }
        }
    }

    override suspend fun get(sha256: String): ByteArray? {
        val path = filePathFor(sha256) ?: return null
        return fileSystem.read(path).getOrNull()
    }

    override suspend fun put(sha256: String, bytes: ByteArray) {
        val path = filePathFor(sha256) ?: return
        runCatching {
            val parent = path.value.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) {
                fileSystem.createDirectories(FilePath.of(parent))
            }
            fileSystem.write(path, bytes, WriteMode.Replace)
        }
    }

    /**
     * Deletes cached files that [index] does not list, such as files an earlier release
     * used and this one doesn't.
     *
     * @param index The project index whose files should be retained.
     * @return The number of deleted files.
     */
    suspend fun retainOnly(index: ProjectIndex): Int {
        val keep = index.files.map { it.sha256.lowercase() }.toSet()
        val rootPath = namespace?.let { FilePath.of(it) } ?: FilePath.Root
        val entries = fileSystem.list(rootPath, recursive = true).getOrNull() ?: return 0
        return entries
            .filter { it.kind == FileKind.File }
            .filter { it.path.value.substringAfterLast('/') !in keep }
            .count { fileSystem.delete(it.path).isSuccess }
    }

    private fun filePathFor(sha256: String): FilePath? {
        val clean = sha256.lowercase()
        if (clean.length < 2) return null
        val prefix = clean.substring(0, 2)
        val relative = if (namespace != null) {
            "$namespace/$prefix/$clean"
        } else {
            "$prefix/$clean"
        }
        return runCatching { FilePath.of(relative) }.getOrNull()
    }
}
