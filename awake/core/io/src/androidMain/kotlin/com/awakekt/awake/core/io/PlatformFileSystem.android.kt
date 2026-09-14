/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions", "TooGenericExceptionCaught", "LoopWithTooManyJumpStatements", "ThrowsCount")

package com.awakekt.awake.core.io

import java.io.File
import kotlin.concurrent.thread

actual fun createPlatformFileSystem(root: String?): FileSystem =
    AndroidFileSystem(File(root ?: ".").absoluteFile.normalize())

private class AndroidFileSystem(private val root: File) : FileSystem {
    private val watchers = mutableListOf<AndroidWatcher>()
    private var suppressNotifications = false

    init {
        root.mkdirs()
    }

    override suspend fun openRead(path: FilePath): Result<ByteReadSession> = runCatching {
        val target = resolve(path)
        if (!target.isFile) throw FileSystemException(FileSystemError.NotFound(path))
        AndroidReadSession(target.readBytes())
    }

    override suspend fun openWrite(path: FilePath, mode: WriteMode): Result<ByteWriteSession> = runCatching {
        val target = resolve(path)
        if (mode == WriteMode.CreateNew && target.exists()) {
            throw FileSystemException(FileSystemError.AlreadyExists(path))
        }
        if (mode == WriteMode.Append && !target.isFile) {
            throw FileSystemException(FileSystemError.NotFound(path))
        }
        AndroidWriteSession(this, path, mode, if (mode == WriteMode.Append) target.readBytes() else ByteArray(0))
    }

    override suspend fun list(path: FilePath, recursive: Boolean): Result<List<FileEntry>> = runCatching {
        val directory = resolve(path)
        if (!directory.isDirectory) throw FileSystemException(FileSystemError.NotFound(path))
        val entries = mutableListOf<FileEntry>()
        fun visit(current: File) {
            current.listFiles().orEmpty().sortedBy { it.name }.forEach { child ->
                entries += entry(toPath(child), child)
                if (recursive && child.isDirectory) visit(child)
            }
        }
        visit(directory)
        entries.sortedBy { it.path.value }
    }

    override suspend fun stat(path: FilePath): Result<FileEntry?> = runCatching {
        val target = resolve(path)
        if (!target.exists()) null else entry(path, target)
    }

    override suspend fun createDirectories(path: FilePath): Result<Unit> = runCatching {
        val directory = resolve(path)
        if (!directory.exists() && !directory.mkdirs()) {
            throw FileSystemException(FileSystemError.IoFailure("createDirectories", "Could not create ${path.value}."))
        }
        if (!directory.isDirectory) throw FileSystemException(FileSystemError.Conflict(path, "A file already exists at this path."))
    }

    override suspend fun delete(path: FilePath, recursive: Boolean): Result<Unit> = runCatching {
        if (path == FilePath.Root) throw FileSystemException(FileSystemError.InvalidPath("", "The root cannot be deleted."))
        val target = resolve(path)
        if (!target.exists()) throw FileSystemException(FileSystemError.NotFound(path))
        if (target.isDirectory && !recursive && target.listFiles().orEmpty().isNotEmpty()) {
            throw FileSystemException(FileSystemError.Conflict(path, "Directory is not empty."))
        }
        if (!target.deleteRecursively()) {
            throw FileSystemException(FileSystemError.IoFailure("delete", "Could not delete ${path.value}."))
        }
        notify(FileChangeBatch(listOf(FileChange.Deleted(path))))
    }

    override suspend fun move(from: FilePath, to: FilePath, replace: Boolean): Result<Unit> = runCatching {
        val source = resolve(from)
        val target = resolve(to)
        if (!source.exists()) throw FileSystemException(FileSystemError.NotFound(from))
        if (target.exists() && !replace) throw FileSystemException(FileSystemError.AlreadyExists(to))
        target.parentFile?.mkdirs()
        if (target.exists() && !target.deleteRecursively()) {
            throw FileSystemException(FileSystemError.IoFailure("move", "Could not replace ${to.value}."))
        }
        if (!source.renameTo(target)) {
            throw FileSystemException(FileSystemError.IoFailure("move", "Could not move ${from.value}."))
        }
        notify(FileChangeBatch(listOf(FileChange.Moved(from, to))))
    }

    override suspend fun <T> transaction(block: suspend FileTransaction.() -> T): Result<T> {
        val beforeEntries = snapshotEntries(FilePath.Root, recursive = true)
        val before = snapshotFiles()
        val staged = InMemoryFileSystem(before)
        val result = staged.transaction(block)
        if (result.isFailure) return result
        val afterEntries = captureEntries(staged)
        val after = afterEntries.filterValues { it.kind == FileKind.File }
            .mapValues { staged.read(it.key).getOrThrow() }
        return try {
            suppressNotifications = true
            reconcile(before, after)
            suppressNotifications = false
            val changes = diffEntries(beforeEntries, afterEntries)
            if (changes.isNotEmpty()) notify(FileChangeBatch(changes))
            result
        } catch (cause: Throwable) {
            suppressNotifications = false
            runCatching { reconcile(after, before) }
            Result.failure(cause.toFileSystemException("transaction"))
        }
    }

    override fun watch(path: FilePath, recursive: Boolean, listener: (FileChangeBatch) -> Unit): FileWatch {
        val initial = snapshotEntries(path, recursive)
        var running = true
        val watcher = AndroidWatcher(path, recursive, listener)
        watchers += watcher
        val worker = thread(isDaemon = true, name = "awake-android-file-watch") {
            var previous = initial
            while (running) {
                try {
                    Thread.sleep(WATCH_INTERVAL_MS)
                    val current = snapshotEntries(path, recursive)
                    val changes = diffEntries(previous, current)
                    if (changes.isNotEmpty()) listener(FileChangeBatch(changes))
                    previous = current
                } catch (_: InterruptedException) {
                    break
                } catch (_: Throwable) {
                    // A transient storage error must not kill the application watcher.
                }
            }
        }
        return FileWatch {
            running = false
            watchers.remove(watcher)
            worker.interrupt()
        }
    }

    internal fun resolve(path: FilePath): File = File(root, path.value).absoluteFile.normalize().also {
        check(it.path == root.path || it.path.startsWith(root.path + File.separator)) {
            "Path escaped filesystem root: ${path.value}"
        }
    }

    internal fun publish(path: FilePath, bytes: ByteArray, mode: WriteMode): FileChange {
        val destination = resolve(path)
        val existed = destination.exists()
        destination.parentFile?.mkdirs()
        if (mode == WriteMode.CreateNew && existed) {
            throw FileSystemException(FileSystemError.AlreadyExists(path))
        }
        val temporary = File.createTempFile(".awake-", ".tmp", destination.parentFile ?: resolve(FilePath.Root))
        try {
            temporary.writeBytes(bytes)
            if (existed && !destination.delete()) {
                throw FileSystemException(FileSystemError.IoFailure("write", "Could not replace ${path.value}."))
            }
            if (!temporary.renameTo(destination)) {
                throw FileSystemException(FileSystemError.IoFailure("write", "Could not atomically publish ${path.value}."))
            }
        } finally {
            temporary.delete()
        }
        val published = entry(path, destination)
        return if (existed) FileChange.Modified(published) else FileChange.Created(published)
    }

    private fun toPath(file: File): FilePath = FilePath.of(file.relativeTo(root).path.replace(File.separatorChar, '/'))

    private fun entry(path: FilePath, file: File) =
        FileEntry(path, if (file.isDirectory) FileKind.Directory else FileKind.File, file.length().takeIf { file.isFile }, file.lastModified())

    private fun snapshotFiles(): Map<FilePath, ByteArray> =
        snapshotEntries(FilePath.Root, recursive = true)
            .filterValues { it.kind == FileKind.File }
            .mapValues { resolve(it.key).readBytes() }

    private fun snapshotEntries(path: FilePath, recursive: Boolean): Map<FilePath, FileEntry> {
        val directory = resolve(path)
        if (!directory.isDirectory) return emptyMap()
        val entries = mutableMapOf<FilePath, FileEntry>()
        fun visit(current: File) {
            current.listFiles().orEmpty().forEach { child ->
                val childPath = toPath(child)
                entries[childPath] = entry(childPath, child)
                if (recursive && child.isDirectory) visit(child)
            }
        }
        visit(directory)
        return entries
    }

    private suspend fun reconcile(before: Map<FilePath, ByteArray>, after: Map<FilePath, ByteArray>) {
        before.keys.filter { it !in after }.sortedByDescending { it.value.count { c -> c == '/' } }.forEach {
            delete(it, false).getOrThrow()
        }
        after.keys.sortedBy { it.value.count { c -> c == '/' } }.forEach { path ->
            val bytes = after.getValue(path)
            if (!before[path].contentEqualsOrNull(bytes)) write(path, bytes).getOrThrow()
        }
    }

    internal fun notify(batch: FileChangeBatch) {
        if (suppressNotifications) return
        watchers.toList().forEach { watcher ->
            val visible = batch.changes.filter { change ->
                val paths = when (change) {
                    is FileChange.Created -> listOf(change.entry.path)
                    is FileChange.Modified -> listOf(change.entry.path)
                    is FileChange.Deleted -> listOf(change.path)
                    is FileChange.Moved -> listOf(change.from, change.to)
                }
                paths.any { watcher.matches(it) }
            }
            if (visible.isNotEmpty()) watcher.listener(FileChangeBatch(visible))
        }
    }
}

private class AndroidReadSession(private val bytes: ByteArray) : ByteReadSession {
    private var offset = 0
    override suspend fun readChunk(maxBytes: Int): ByteArray? {
        require(maxBytes > 0) { "maxBytes must be positive" }
        if (offset >= bytes.size) return null
        val end = (offset + maxBytes).coerceAtMost(bytes.size)
        return bytes.copyOfRange(offset, end).also { offset = end }
    }
    override fun close() = Unit
}

private class AndroidWriteSession(
    private val owner: AndroidFileSystem,
    private val path: FilePath,
    private val mode: WriteMode,
    initial: ByteArray,
) : ByteWriteSession {
    private val chunks = mutableListOf(initial)
    private var closed = false

    override suspend fun writeChunk(bytes: ByteArray) {
        check(!closed) { "Write session is closed" }
        chunks += bytes.copyOf()
    }

    override suspend fun commit(): Result<Unit> = runCatching {
        check(!closed) { "Write session is closed" }
        owner.notify(FileChangeBatch(listOf(owner.publish(path, chunks.flattenToByteArray(), mode))))
        closed = true
    }

    override suspend fun abort() {
        closed = true
        chunks.clear()
    }

    override fun close() {
        closed = true
        chunks.clear()
    }
}

private fun List<ByteArray>.flattenToByteArray(): ByteArray {
    val result = ByteArray(sumOf { it.size })
    var offset = 0
    forEach { chunk ->
        chunk.copyInto(result, offset)
        offset += chunk.size
    }
    return result
}

private fun ByteArray?.contentEqualsOrNull(other: ByteArray): Boolean = this?.contentEquals(other) == true

private fun Throwable.toFileSystemException(operation: String): FileSystemException =
    this as? FileSystemException
        ?: FileSystemException(FileSystemError.IoFailure(operation, message ?: this::class.simpleName.orEmpty()))

private fun diffEntries(
    before: Map<FilePath, FileEntry>,
    after: Map<FilePath, FileEntry>,
): List<FileChange> = buildList {
    before.keys.filter { it !in after }.sortedBy { it.value }.forEach { add(FileChange.Deleted(it)) }
    after.keys.filter { it !in before }.sortedBy { it.value }.forEach { add(FileChange.Created(after.getValue(it))) }
    after.keys.filter { it in before && before.getValue(it) != after.getValue(it) }
        .sortedBy { it.value }
        .forEach { add(FileChange.Modified(after.getValue(it))) }
}

private const val WATCH_INTERVAL_MS = 150L

private data class AndroidWatcher(
    val path: FilePath,
    val recursive: Boolean,
    val listener: (FileChangeBatch) -> Unit,
) {
    fun matches(candidate: FilePath): Boolean =
        path == FilePath.Root || candidate == path || (recursive && candidate.value.startsWith("${path.value}/"))
}

private suspend fun captureEntries(fs: FileSystem): Map<FilePath, FileEntry> =
    fs.list(FilePath.Root, recursive = true).getOrThrow().associateBy { it.path }
