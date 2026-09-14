/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

/** Reference implementation used by common tests and ephemeral browser sessions. */
class InMemoryFileSystem(
    initialFiles: Map<FilePath, ByteArray> = emptyMap(),
) : FileSystem {
    private val files = initialFiles.mapValuesTo(mutableMapOf()) { it.value.copyOf() }
    private val directories = mutableSetOf<FilePath>(FilePath.Root)
    private val watchers = mutableListOf<Watcher>()
    private var clock = 0L
    private val modified = mutableMapOf<FilePath, Long>()

    init {
        files.keys.forEach(::ensureParentDirectories)
    }

    override suspend fun openRead(path: FilePath): Result<ByteReadSession> =
        files[path]?.copyOf()?.let { Result.success(MemoryReadSession(it)) }
            ?: Result.failure(FileSystemException(FileSystemError.NotFound(path)))

    override suspend fun openWrite(path: FilePath, mode: WriteMode): Result<ByteWriteSession> {
        if (directories.contains(path)) {
            return Result.failure(FileSystemException(FileSystemError.Conflict(path, "A directory cannot be written as a file.")))
        }
        if (mode == WriteMode.CreateNew && (files.containsKey(path) || directories.contains(path))) {
            return Result.failure(FileSystemException(FileSystemError.AlreadyExists(path)))
        }
        if (mode == WriteMode.Append && !files.containsKey(path)) {
            return Result.failure(FileSystemException(FileSystemError.NotFound(path)))
        }
        val existing = if (mode == WriteMode.Append) files[path]?.copyOf() else ByteArray(0)
        return Result.success(MemoryWriteSession(path, mode, existing ?: ByteArray(0)))
    }

    override suspend fun list(path: FilePath, recursive: Boolean): Result<List<FileEntry>> {
        if (path != FilePath.Root && !directories.contains(path)) {
            return Result.failure(FileSystemException(FileSystemError.NotFound(path)))
        }
        val prefix = if (path == FilePath.Root) "" else "${path.value}/"
        val entries = buildList {
            directories.filter { it != FilePath.Root }.forEach { directory ->
                if (isChild(prefix, directory.value, recursive)) add(entry(directory, FileKind.Directory))
            }
            files.keys.forEach { file ->
                if (isChild(prefix, file.value, recursive)) add(entry(file, FileKind.File))
            }
        }
        return Result.success(entries.sortedBy { it.path.value })
    }

    override suspend fun stat(path: FilePath): Result<FileEntry?> =
        when {
            path == FilePath.Root || directories.contains(path) -> Result.success(entry(path, FileKind.Directory))
            files.containsKey(path) -> Result.success(entry(path, FileKind.File))
            else -> Result.success(null)
        }

    override suspend fun createDirectories(path: FilePath): Result<Unit> {
        ensureParentDirectories(path)
        directories += path
        return Result.success(Unit)
    }

    override suspend fun delete(path: FilePath, recursive: Boolean): Result<Unit> {
        if (path == FilePath.Root) {
            return Result.failure(FileSystemException(FileSystemError.InvalidPath(path.value, "The root cannot be deleted.")))
        }
        if (files.remove(path) != null) {
            modified.remove(path)
            notify(FileChangeBatch(listOf(FileChange.Deleted(path))))
            return Result.success(Unit)
        }
        if (!directories.contains(path)) return Result.failure(FileSystemException(FileSystemError.NotFound(path)))
        val descendants = files.keys.filter { it.value.startsWith("${path.value}/") } +
            directories.filter { it.value.startsWith("${path.value}/") }
        if (!recursive && descendants.isNotEmpty()) {
            return Result.failure(FileSystemException(FileSystemError.Conflict(path, "Directory is not empty.")))
        }
        val changes = descendants.filterIsInstance<FilePath>().map(FileChange::Deleted).toMutableList()
        descendants.filterIsInstance<FilePath>().forEach { files.remove(it); modified.remove(it); directories.remove(it) }
        directories.remove(path)
        changes += FileChange.Deleted(path)
        notify(FileChangeBatch(changes))
        return Result.success(Unit)
    }

    override suspend fun move(from: FilePath, to: FilePath, replace: Boolean): Result<Unit> {
        val bytes = files[from]
        if (bytes == null && !directories.contains(from)) {
            return Result.failure(FileSystemException(FileSystemError.NotFound(from)))
        }
        if (!replace && (files.containsKey(to) || directories.contains(to))) {
            return Result.failure(FileSystemException(FileSystemError.AlreadyExists(to)))
        }
        ensureParentDirectories(to)
        if (bytes != null) {
            files.remove(from)
            files[to] = bytes
            modified[to] = nextTimestamp()
            modified.remove(from)
        } else {
            val movedFiles = files.filterKeys { it.value == from.value || it.value.startsWith("${from.value}/") }
            val movedDirectories = directories.filter { it == from || it.value.startsWith("${from.value}/") }
            movedFiles.keys.forEach { files.remove(it) }
            movedDirectories.forEach { directories.remove(it) }
            movedDirectories.forEach { old -> directories += remap(from, to, old) }
            movedFiles.forEach { (old, content) -> files[remap(from, to, old)] = content }
        }
        notify(FileChangeBatch(listOf(FileChange.Moved(from, to))))
        return Result.success(Unit)
    }

    override suspend fun <T> transaction(block: suspend FileTransaction.() -> T): Result<T> {
        val snapshot = Snapshot(files.mapValues { it.value.copyOf() }, directories.toSet(), modified.toMap())
        val working = TransactionState(snapshot.files.toMutableMap(), snapshot.directories.toMutableSet(), snapshot.modified.toMutableMap())
        val result = runCatching { working.block() }
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)
        val value = result.getOrThrow()
        val changes = diff(snapshot, working)
        files.clear(); files.putAll(working.files)
        directories.clear(); directories.addAll(working.directories)
        modified.clear(); modified.putAll(working.modified)
        if (changes.isNotEmpty()) notify(FileChangeBatch(changes))
        return Result.success(value)
    }

    override fun watch(path: FilePath, recursive: Boolean, listener: (FileChangeBatch) -> Unit): FileWatch {
        val watcher = Watcher(path, recursive, listener)
        watchers += watcher
        return FileWatch {
            watchers.remove(watcher)
        }
    }

    private fun ensureParentDirectories(path: FilePath) {
        val parts = path.value.split('/').dropLast(1)
        var current = ""
        for (part in parts) {
            current = listOf(current, part).filter { it.isNotEmpty() }.joinToString("/")
            directories += FilePath.of(current)
        }
    }

    private fun entry(path: FilePath, kind: FileKind): FileEntry =
        FileEntry(path, kind, files[path]?.size?.toLong(), modified[path])

    private fun nextTimestamp(): Long {
        clock += 1
        return clock
    }

    private fun notify(batch: FileChangeBatch) {
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

    private data class Snapshot(
        val files: Map<FilePath, ByteArray>,
        val directories: Set<FilePath>,
        val modified: Map<FilePath, Long>,
    )

    private inner class TransactionState(
        val files: MutableMap<FilePath, ByteArray>,
        val directories: MutableSet<FilePath>,
        val modified: MutableMap<FilePath, Long>,
    ) : FileTransaction {
        override suspend fun read(path: FilePath): Result<ByteArray> =
            files[path]?.copyOf()?.let(Result.Companion::success)
                ?: Result.failure(FileSystemException(FileSystemError.NotFound(path)))

        override suspend fun write(path: FilePath, bytes: ByteArray, mode: WriteMode): Result<Unit> {
            if (mode == WriteMode.CreateNew && files.containsKey(path)) {
                return Result.failure(FileSystemException(FileSystemError.AlreadyExists(path)))
            }
            if (mode == WriteMode.Append && !files.containsKey(path)) {
                return Result.failure(FileSystemException(FileSystemError.NotFound(path)))
            }
            ensureParentDirectoriesInTransaction(path)
            val existing = if (mode == WriteMode.Append) files[path] ?: ByteArray(0) else ByteArray(0)
            files[path] = existing + bytes
            modified[path] = nextTimestamp()
            return Result.success(Unit)
        }

        override suspend fun createDirectories(path: FilePath): Result<Unit> {
            ensureParentDirectoriesInTransaction(path)
            directories += path
            return Result.success(Unit)
        }

        override suspend fun delete(path: FilePath, recursive: Boolean): Result<Unit> {
            if (files.remove(path) != null) {
                modified.remove(path)
                return Result.success(Unit)
            }
            if (!directories.contains(path)) return Result.failure(FileSystemException(FileSystemError.NotFound(path)))
            val children = files.keys.any { it.value.startsWith("${path.value}/") } ||
                directories.any { it != path && it.value.startsWith("${path.value}/") }
            if (children && !recursive) return Result.failure(FileSystemException(FileSystemError.Conflict(path, "Directory is not empty.")))
            files.keys.filter { it.value.startsWith("${path.value}/") }.toList().forEach { files.remove(it); modified.remove(it) }
            directories.filter { it == path || it.value.startsWith("${path.value}/") }.toList().forEach { directories.remove(it) }
            return Result.success(Unit)
        }

        override suspend fun move(from: FilePath, to: FilePath, replace: Boolean): Result<Unit> {
            val value = files[from] ?: return Result.failure(FileSystemException(FileSystemError.NotFound(from)))
            if (!replace && files.containsKey(to)) return Result.failure(FileSystemException(FileSystemError.AlreadyExists(to)))
            ensureParentDirectoriesInTransaction(to)
            files.remove(from)
            files[to] = value
            modified.remove(from)
            modified[to] = nextTimestamp()
            return Result.success(Unit)
        }

        private fun ensureParentDirectoriesInTransaction(path: FilePath) {
            val parts = path.value.split('/').dropLast(1)
            var current = ""
            for (part in parts) {
                current = listOf(current, part).filter { it.isNotEmpty() }.joinToString("/")
                directories += FilePath.of(current)
            }
        }
    }

    private class MemoryReadSession(private val bytes: ByteArray) : ByteReadSession {
        private var offset = 0
        override suspend fun readChunk(maxBytes: Int): ByteArray? {
            require(maxBytes > 0) { "maxBytes must be positive" }
            if (offset >= bytes.size) return null
            val end = (offset + maxBytes).coerceAtMost(bytes.size)
            return bytes.copyOfRange(offset, end).also { offset = end }
        }

        override fun close() = Unit
    }

    private inner class MemoryWriteSession(
        private val path: FilePath,
        private val mode: WriteMode,
        initial: ByteArray,
    ) : ByteWriteSession {
        private val chunks = mutableListOf<ByteArray>(initial)
        private var closed = false

        override suspend fun writeChunk(bytes: ByteArray) {
            check(!closed) { "Write session is closed" }
            chunks += bytes.copyOf()
        }

        override suspend fun commit(): Result<Unit> {
            if (closed) return Result.failure(FileSystemException(FileSystemError.Conflict(path, "Session is closed.")))
            val existed = files.containsKey(path)
            val data = chunks.flattenToByteArray()
            ensureParentDirectories(path)
            files[path] = data
            modified[path] = nextTimestamp()
            closed = true
            notify(FileChangeBatch(listOf(if (existed) FileChange.Modified(entry(path, FileKind.File)) else FileChange.Created(entry(path, FileKind.File)))))
            return Result.success(Unit)
        }

        override suspend fun abort() { closed = true; chunks.clear() }
        override fun close() { closed = true }
    }

    private data class Watcher(
        val path: FilePath,
        val recursive: Boolean,
        val listener: (FileChangeBatch) -> Unit,
    ) {
        fun matches(candidate: FilePath): Boolean =
            path == FilePath.Root || candidate == path || (recursive && candidate.value.startsWith("${path.value}/"))
    }

    private fun diff(before: Snapshot, after: TransactionState): List<FileChange> = buildList {
        before.directories.filter { it != FilePath.Root && it !in after.directories }
            .sortedByDescending { it.value.length }
            .forEach { add(FileChange.Deleted(it)) }
        after.directories.filter { it != FilePath.Root && it !in before.directories }
            .sortedBy { it.value.length }
            .forEach { add(FileChange.Created(FileEntry(it, FileKind.Directory, modifiedAtEpochMs = after.modified[it])) ) }
        after.files.forEach { (path, bytes) ->
            val old = before.files[path]
            if (old == null) add(FileChange.Created(FileEntry(path, FileKind.File, bytes.size.toLong(), after.modified[path])))
            else if (!old.contentEquals(bytes)) add(FileChange.Modified(FileEntry(path, FileKind.File, bytes.size.toLong(), after.modified[path])))
        }
        before.files.keys.filter { it !in after.files }.forEach { add(FileChange.Deleted(it)) }
    }

    private fun remap(from: FilePath, to: FilePath, path: FilePath): FilePath {
        val suffix = path.value.removePrefix(from.value).removePrefix("/")
        return FilePath.of(listOf(to.value, suffix).filter { it.isNotEmpty() }.joinToString("/"))
    }

    private fun isChild(prefix: String, value: String, recursive: Boolean): Boolean {
        if (!value.startsWith(prefix)) return false
        if (recursive) return true
        return !value.removePrefix(prefix).contains('/')
    }
}
