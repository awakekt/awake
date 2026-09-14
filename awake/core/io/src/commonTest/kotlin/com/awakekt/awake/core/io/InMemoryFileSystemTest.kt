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
}

private fun List<ByteArray>.flattenToByteArrayForTest(): ByteArray {
    val size = sumOf { it.size }
    val result = ByteArray(size)
    var offset = 0
    forEach { it.copyInto(result, offset); offset += it.size }
    return result
}
