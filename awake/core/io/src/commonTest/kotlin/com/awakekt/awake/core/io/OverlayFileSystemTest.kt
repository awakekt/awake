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

class OverlayFileSystemTest {
    @Test
    fun overlayReadsPreferLocalChangesAndMergeListings() = runTest {
        val base = InMemoryFileSystem(
            mapOf(
                FilePath.of("shared.txt") to "remote".encodeToByteArray(),
                FilePath.of("remote-only.txt") to byteArrayOf(1),
            ),
        )
        val local = InMemoryFileSystem(
            mapOf(FilePath.of("shared.txt") to "local".encodeToByteArray()),
        )
        val fileSystem = OverlayFileSystem(base, local)

        assertEquals("local", fileSystem.read(FilePath.of("shared.txt")).getOrThrow().decodeToString())
        assertEquals(
            listOf("remote-only.txt", "shared.txt"),
            fileSystem.list(recursive = true).getOrThrow().map { it.path.value },
        )
    }

    @Test
    fun writesDeletesAndMovesStayInTheOverlay() = runTest {
        val base = InMemoryFileSystem(
            mapOf(FilePath.of("source.txt") to "source".encodeToByteArray()),
        )
        val local = InMemoryFileSystem()
        val fileSystem = OverlayFileSystem(base, local)

        fileSystem.write(FilePath.of("source.txt"), "edited".encodeToByteArray()).getOrThrow()
        fileSystem.move(FilePath.of("source.txt"), FilePath.of("moved.txt")).getOrThrow()

        assertNull(fileSystem.stat(FilePath.of("source.txt")).getOrThrow())
        assertContentEquals("edited".encodeToByteArray(), fileSystem.read(FilePath.of("moved.txt")).getOrThrow())
        assertContentEquals("source".encodeToByteArray(), base.read(FilePath.of("source.txt")).getOrThrow())

        fileSystem.delete(FilePath.of("moved.txt")).getOrThrow()

        assertNull(fileSystem.stat(FilePath.of("moved.txt")).getOrThrow())
        assertTrue(fileSystem.read(FilePath.of("moved.txt")).isFailure)
    }

    @Test
    fun appendingToRemoteFileSeedsTheLocalOverlay() = runTest {
        val path = FilePath.of("log.txt")
        val fileSystem = OverlayFileSystem(
            InMemoryFileSystem(mapOf(path to "before".encodeToByteArray())),
            InMemoryFileSystem(),
        )

        val session = fileSystem.openWrite(path, WriteMode.Append).getOrThrow()
        session.writeChunk("-after".encodeToByteArray())
        session.commit().getOrThrow()

        assertEquals("before-after", fileSystem.read(path).getOrThrow().decodeToString())
    }

    @Test
    fun watchReportsOverlayChanges() = runTest {
        val fileSystem = OverlayFileSystem(InMemoryFileSystem(), InMemoryFileSystem())
        val changes = mutableListOf<FileChange>()
        val watch = fileSystem.watch(FilePath.Root, recursive = true) { changes += it.changes }

        fileSystem.write(FilePath.of("nested/value.txt"), byteArrayOf(1)).getOrThrow()
        fileSystem.delete(FilePath.of("nested/value.txt")).getOrThrow()
        watch.close()

        assertTrue(changes.any { it is FileChange.Created && it.entry.path == FilePath.of("nested/value.txt") })
        assertTrue(changes.any { it is FileChange.Deleted && it.path == FilePath.of("nested/value.txt") })
    }
}
