/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [AssetSource.exists] finds an asset without reading it where a source can, and by reading where it can't. */
class AssetSourceExistsTest {
    private val scene = FilePath.of("scenes/main.scene.json")

    @Test
    fun aLambdaSourceStillCompilesAndAnswersByReading() = runTest {
        val reads = mutableListOf<String>()
        val source = AssetSource { path ->
            reads += path.value
            if (path.value == scene.value) Result.success(ByteArray(1)) else Result.failure(NoSuchElementException(path.value))
        }

        assertTrue(source.exists(AssetPath(scene.value)))
        assertFalse(source.exists(AssetPath("scenes/missing.scene.json")))
        assertEquals(listOf(scene.value, "scenes/missing.scene.json"), reads, "the default reads to find out")
    }

    @Test
    fun aFileSystemSourceAsksTheFilesystemWithoutReading() = runTest {
        val files = CountingFileSystem(InMemoryFileSystem(mapOf(scene to "{}".encodeToByteArray())))
        val source = FileSystemAssetSource(files)

        assertTrue(source.exists(AssetPath(scene.value)))
        assertFalse(source.exists(AssetPath("scenes/missing.scene.json")))
        assertFalse(source.exists(AssetPath("scenes")), "a directory is not an asset")
        assertEquals(0, files.reads)
    }

    @Test
    fun aFileSystemSourceReadsWhenTheFilesystemCannotSay() = runTest {
        val files = CountingFileSystem(InMemoryFileSystem(mapOf(scene to "{}".encodeToByteArray())), statFails = true)
        val source = FileSystemAssetSource(files)

        assertTrue(source.exists(AssetPath(scene.value)))
        assertFalse(source.exists(AssetPath("scenes/missing.scene.json")))
        assertEquals(2, files.reads)
    }

    @Test
    fun aResourceSourceAnswersByReading() = runTest {
        val source = ResourceAssetSource { path -> if (path == "logo.png") Result.success(ByteArray(1)) else Result.failure(NoSuchElementException(path)) }

        assertTrue(source.exists(AssetPath("logo.png")))
        assertFalse(source.exists(AssetPath("missing.png")))
    }

    /** [files], counting the reads that reach it, and with a [stat] that fails when [statFails]. */
    private class CountingFileSystem(private val files: FileSystem, private val statFails: Boolean = false) : FileSystem by files {
        var reads = 0
            private set

        override suspend fun read(path: FilePath): Result<ByteArray> {
            reads++
            return files.read(path)
        }

        override suspend fun openRead(path: FilePath): Result<ByteReadSession> {
            reads++
            return files.openRead(path)
        }

        override suspend fun stat(path: FilePath): Result<FileEntry?> =
            if (statFails) Result.failure(FileSystemException(FileSystemError.NotFound(path))) else files.stat(path)
    }
}
