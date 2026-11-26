/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.player

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.ioDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads project or scene files from a local desktop [directory] on [ioDispatcher], rejecting any
 * path that resolves outside [directory]'s canonical root.
 */
fun directoryAssetSource(directory: File): AssetSource {
    require(directory.isDirectory) { "Project directory does not exist: ${directory.path}" }
    val root = directory.canonicalFile
    val prefix = root.path + File.separator
    return object : AssetSource {
        override suspend fun read(path: AssetPath): Result<ByteArray> = withContext(ioDispatcher) {
            runCatching {
                val target = resolveSafely(root, prefix, path)
                target.readBytes()
            }
        }

        override suspend fun exists(path: AssetPath): Boolean = withContext(ioDispatcher) {
            runCatching { resolveSafely(root, prefix, path).isFile }.getOrDefault(false)
        }
    }
}

/** Convenience overload for [directoryAssetSource] taking a path string. */
fun directoryAssetSource(directoryPath: String): AssetSource = directoryAssetSource(File(directoryPath))

private fun resolveSafely(root: File, prefix: String, path: AssetPath): File {
    val target = File(root, path.value).canonicalFile
    require(target.path == root.path || target.path.startsWith(prefix)) {
        "Asset path escapes project directory: ${path.value}"
    }
    return target
}
