/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class DesktopFileWatchTest {
    private val root: Path = Files.createTempDirectory("awake-watch-")
    private val fileSystem = createPlatformFileSystem(root.toString())
    private val watches = mutableListOf<FileWatch>()

    @AfterTest
    fun cleanUp() {
        watches.forEach(FileWatch::close)
        root.toFile().deleteRecursively()
    }

    @Test
    fun nestedChangeIsReportedOnlyWhenRecursive() {
        Files.createDirectories(root.resolve("a/b"))
        val recursive = watch(recursive = true)
        val direct = watch(recursive = false)

        Files.createFile(root.resolve("a/b/deep.txt"))
        recursive.await("created a/b/deep.txt") { it.isCreated("a/b/deep.txt", FileKind.File) }
        Files.createFile(root.resolve("top.txt"))
        direct.await("created top.txt") { it.isCreated("top.txt", FileKind.File) }

        assertTrue(direct.seen.none { it.path().value.startsWith("a/") }, "Non-recursive watch saw ${direct.seen}")
    }

    @Test
    fun subdirectoryCreatedAfterWatchStartsIsReportedAsDirectoryAndWatched() {
        val recursive = watch(recursive = true)

        Files.createDirectory(root.resolve("later"))
        recursive.await("created directory later") { it.isCreated("later", FileKind.Directory) }
        Files.createFile(root.resolve("later/file.txt"))
        recursive.await("created later/file.txt") { it.isCreated("later/file.txt", FileKind.File) }
    }

    @Test
    fun createdModifiedAndDeletedFilesAreReportedAsSuch() {
        val direct = watch(recursive = false)
        val file = root.resolve("asset.txt")

        Files.createFile(file)
        direct.await("created asset.txt") { it.isCreated("asset.txt", FileKind.File) }
        Files.writeString(file, "changed")
        direct.await("modified asset.txt") {
            it is FileChange.Modified && it.entry.path.value == "asset.txt" && it.entry.kind == FileKind.File
        }
        Files.delete(file)
        direct.await("deleted asset.txt") { it is FileChange.Deleted && it.path.value == "asset.txt" }
    }

    private fun watch(recursive: Boolean): Recorder =
        Recorder().also { watches += fileSystem.watch(FilePath.Root, recursive, it::record) }

    /** Collects changes from the watch thread and waits for them on the test thread. */
    private class Recorder {
        private val pending = LinkedBlockingQueue<FileChange>()

        /** Every change taken so far, in arrival order. */
        val seen = mutableListOf<FileChange>()

        fun record(batch: FileChangeBatch) {
            pending.addAll(batch.changes)
        }

        /** Waits for a matching change; macOS polls for file events, so a change can take several seconds. */
        fun await(description: String, matches: (FileChange) -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
            while (true) {
                val change = pending.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
                    ?: fail("Timed out waiting for $description; saw $seen")
                seen += change
                if (matches(change)) return
            }
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 60L

        fun FileChange.isCreated(path: String, kind: FileKind): Boolean =
            this is FileChange.Created && entry.path.value == path && entry.kind == kind

        fun FileChange.path(): FilePath = when (this) {
            is FileChange.Created -> entry.path
            is FileChange.Modified -> entry.path
            is FileChange.Deleted -> path
            is FileChange.Moved -> to
        }
    }
}
