/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryFileSystemTest {
    private val scene = FilePath.of("scenes/main.scene.json")

    @Test
    fun writesReadsAndListsFiles() = runTest {
        val fs = InMemoryFileSystem()
        assertTrue(fs.write(scene, "{}".encodeToByteArray()).isSuccess)
        assertContentEquals("{}".encodeToByteArray(), fs.read(scene).getOrThrow())
        val entries = fs.list(FilePath.of("scenes")).getOrThrow()
        assertEquals(listOf(scene), entries.map { it.path })
    }

    @Test
    fun chunksStayBoundedAndRoundTrip() = runTest {
        val fs = InMemoryFileSystem()
        val path = FilePath.of("large.bin")
        val bytes = ByteArray(DEFAULT_CHUNK_SIZE + 17) { (it % 251).toByte() }
        fs.write(path, bytes).getOrThrow()
        val session = fs.openRead(path).getOrThrow()
        val first = session.readChunk(11)!!
        assertEquals(11, first.size)
        val remainder = buildList {
            while (true) add(session.readChunk(13) ?: break)
        }.flattenToByteArrayForTest()
        assertContentEquals(bytes, first + remainder)
    }

    @Test
    fun transactionCommitsAsOneChangeBatch() = runTest {
        val fs = InMemoryFileSystem()
        val batches = mutableListOf<FileChangeBatch>()
        fs.watch(FilePath.Root, recursive = true) { batches += it }
        val result = fs.transaction {
            write(FilePath.of("a.txt"), "a".encodeToByteArray()).getOrThrow()
            write(FilePath.of("b.txt"), "b".encodeToByteArray()).getOrThrow()
            "committed"
        }
        assertEquals("committed", result.getOrThrow())
        assertEquals(1, batches.size)
        assertEquals(2, batches.single().changes.size)
    }

    @Test
    fun failedTransactionDoesNotPublishChanges() = runTest {
        val fs = InMemoryFileSystem()
        val result = fs.transaction {
            write(FilePath.of("temporary.txt"), byteArrayOf(1)).getOrThrow()
            error("stop")
        }
        assertTrue(result.isFailure)
        assertNull(fs.stat(FilePath.of("temporary.txt")).getOrThrow())
    }

    @Test
    fun writeSessionIsInvisibleUntilAtomicCommit() = runTest {
        val fs = InMemoryFileSystem()
        val path = FilePath.of("atomic.json")
        val session = fs.openWrite(path).getOrThrow()

        assertNull(fs.stat(path).getOrThrow())
        session.writeChunk("{".encodeToByteArray())
        session.writeChunk("}".encodeToByteArray())
        assertNull(fs.stat(path).getOrThrow())

        session.commit().getOrThrow()
        assertEquals("{}", fs.read(path).getOrThrow().decodeToString())
    }

    @Test
    fun transactionChangesAreHiddenUntilCommitAndWatcherOrderIsStable() = runTest {
        val fs = InMemoryFileSystem()
        val observed = mutableListOf<FileChange>()
        fs.watch(FilePath.Root, recursive = true) { observed += it.changes }

        fs.transaction {
            write(FilePath.of("nested/value.txt"), byteArrayOf(1)).getOrThrow()
            assertNull(fs.stat(FilePath.of("nested/value.txt")).getOrThrow())
        }.getOrThrow()

        val expected: List<FileChange> = listOf(
            FileChange.Created(FileEntry(FilePath.of("nested"), FileKind.Directory)),
            FileChange.Created(FileEntry(FilePath.of("nested/value.txt"), FileKind.File, 1L, 1L)),
        )
        assertEquals(expected, observed)
    }

    @Test
    fun failuresKeepTheirStructuredCategory() = runTest {
        val failure = InMemoryFileSystem().read(FilePath.of("missing.bin")).exceptionOrNull()
        val exception = assertIs<FileSystemException>(failure)
        assertEquals(FileSystemError.NotFound(FilePath.of("missing.bin")), exception.error)
    }
}

private fun List<ByteArray>.flattenToByteArrayForTest(): ByteArray {
    val size = sumOf { it.size }
    val result = ByteArray(size)
    var offset = 0
    forEach {
        it.copyInto(result, offset)
        offset += it.size
    }
    return result
}
