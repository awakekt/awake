/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

/**
 * Reads engine assets without coupling parsers to a filesystem or browser API.
 *
 * Only [read] has to be written, so a lambda is a source; [exists] answers by reading unless a
 * source knows a cheaper way.
 */
fun interface AssetSource {
    /**
     * Asynchronously loads raw asset bytes identified by [path].
     *
     * @param path The logical asset path to read.
     * @return Success result containing the byte array, or failure.
     */
    suspend fun read(path: AssetPath): Result<ByteArray>

    /**
     * Whether there is an asset at [path], for a caller that only needs to know it is there, such as
     * a check that a path a scene names leads somewhere. A source that can tell without loading the
     * bytes, as a filesystem or an index of files can, overrides it; this default reads the asset and
     * reports whether the read succeeded. A read of an asset that is there can still fail for what
     * only reading finds, such as bytes that don't match what they should be.
     *
     * @param path The logical asset path to look for.
     * @return True when the asset is there.
     */
    suspend fun exists(path: AssetPath): Boolean = read(path).isSuccess
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

    /** Whether [path] is a file, asked of the filesystem's [FileSystem.stat]; read instead when that fails. */
    override suspend fun exists(path: AssetPath): Boolean =
        fileSystem.stat(path.asFilePath()).fold(
            onSuccess = { entry -> entry?.kind == FileKind.File },
            onFailure = { read(path).isSuccess },
        )
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
