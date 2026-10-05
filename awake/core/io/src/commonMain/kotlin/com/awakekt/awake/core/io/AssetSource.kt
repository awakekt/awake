/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

/**
 * Reads engine assets without coupling parsers to a filesystem or browser API.
 */
fun interface AssetSource {
    /**
     * Asynchronously loads raw asset bytes identified by [path].
     *
     * @param path The logical asset path to read.
     * @return Success result containing the byte array, or failure.
     */
    suspend fun read(path: AssetPath): Result<ByteArray>
}

/**
 * Adapts a Core filesystem to an asset source rooted at the same project boundary.
 *
 * @param fileSystem The backing filesystem instance.
 */
class FileSystemAssetSource(
    private val fileSystem: FileSystem,
) : AssetSource {
    override suspend fun read(path: AssetPath): Result<ByteArray> = fileSystem.read(path.asFilePath())
}

/**
 * Adapts a bundled resource reader to the same byte-oriented asset contract.
 *
 * @param reader The underlying resource reader instance.
 */
class ResourceAssetSource(
    private val reader: ResourceReader,
) : AssetSource {
    override suspend fun read(path: AssetPath): Result<ByteArray> = reader.read(path.value)
}

/**
 * Platform-independent read access for packaged resources.
 */
fun interface ResourceReader {
    /**
     * Reads raw bytes for a resource at the given [path].
     *
     * @param path The relative resource path string.
     * @return Success result containing the byte array, or failure.
     */
    suspend fun read(path: String): Result<ByteArray>
}

/**
 * Wraps an existing suspend byte loader while preserving failures as structured results.
 *
 * @param loader The suspend loading function converting a path string to byte content.
 */
class FunctionResourceReader(
    private val loader: suspend (String) -> ByteArray,
) : ResourceReader {
    override suspend fun read(path: String): Result<ByteArray> = runCatching { loader(path) }
}
