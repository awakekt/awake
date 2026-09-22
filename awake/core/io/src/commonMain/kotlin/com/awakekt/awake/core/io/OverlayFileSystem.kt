/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions", "ReturnCount")

package com.awakekt.awake.core.io

/**
 * A writable view over a read-only or remote filesystem.
 *
 * Reads prefer [overlay]. Missing files fall back to [base]. Deletes are represented as tombstones
 * so a local browser workspace can hide files that still exist in its remote project source.
 */
class OverlayFileSystem(
    private val base: FileSystem,
    private val overlay: FileSystem,
) : FileSystem {
    private val deleted = mutableSetOf<FilePath>()
    private val watchers = mutableListOf<Watcher>()

    override suspend fun openRead(path: FilePath): Result<ByteReadSession> {
        if (isDeleted(path)) return notFound(path)
        return overlay.openRead(path).fold(
            onSuccess = { Result.success(it) },
            onFailure = { base.openRead(path) },
        )
    }

    override suspend fun openWrite(path: FilePath, mode: WriteMode): Result<ByteWriteSession> {
        if (mode == WriteMode.CreateNew && stat(path).getOrNull() != null) {
            return Result.failure(FileSystemException(FileSystemError.AlreadyExists(path)))
        }
        val existed = stat(path).getOrNull() != null
        if (mode == WriteMode.Append && existed && overlay.stat(path).getOrNull() == null) {
            val existingBytes = base.read(path).getOrElse { return Result.failure(it) }
            overlay.write(path, existingBytes, WriteMode.Replace).getOrElse { return Result.failure(it) }
        }
        return overlay.openWrite(path, mode)
            .map { session ->
                CommittingWriteSession(session) {
                    deleted.remove(path)
                    notify(
                        FileChangeBatch(
                            listOf(
                                if (existed) {
                                    FileChange.Modified(fileEntry(path))
                                } else {
                                    FileChange.Created(fileEntry(path))
                                },
                            ),
                        ),
                    )
                    Result.success(Unit)
                }
            }
    }

    override suspend fun list(path: FilePath, recursive: Boolean): Result<List<FileEntry>> {
        if (isDeleted(path)) return notFound(path)
        val baseEntries = listOrEmpty(base, path, recursive).getOrElse { return Result.failure(it) }
        val overlayEntries = listOrEmpty(overlay, path, recursive).getOrElse { return Result.failure(it) }
        val merged = (baseEntries + overlayEntries)
            .filterNot { isDeleted(it.path) }
            .associateBy(FileEntry::path)
            .values
            .sortedBy { it.path.value }
        return Result.success(merged)
    }

    override suspend fun stat(path: FilePath): Result<FileEntry?> {
        if (isDeleted(path)) return Result.success(null)
        val overlayEntry = overlay.stat(path).getOrNull()
        return if (overlayEntry != null) Result.success(overlayEntry) else base.stat(path)
    }

    override suspend fun createDirectories(path: FilePath): Result<Unit> =
        overlay.createDirectories(path).onSuccess {
            deleted.remove(path)
            notify(FileChangeBatch(listOf(FileChange.Created(fileEntry(path, FileKind.Directory)))))
        }

    override suspend fun delete(path: FilePath, recursive: Boolean): Result<Unit> {
        val before = if (recursive) list(path, recursive = true).getOrElse { emptyList() } else {
            listOfNotNull(stat(path).getOrNull())
        }
        if (before.isEmpty()) return notFound(path)

        overlay.delete(path, recursive).onFailure { failure ->
            if (!isNotFound(failure)) return Result.failure(failure)
        }
        before.forEach { deleted += it.path }
        notify(FileChangeBatch(before.map { FileChange.Deleted(it.path) }))
        return Result.success(Unit)
    }

    override suspend fun move(from: FilePath, to: FilePath, replace: Boolean): Result<Unit> {
        val source = stat(from).getOrNull() ?: return notFound(from)
        if (!replace && stat(to).getOrNull() != null) {
            return Result.failure(FileSystemException(FileSystemError.AlreadyExists(to)))
        }
        if (source.kind == FileKind.Directory) {
            val entries = list(from, recursive = true).getOrThrow()
            entries.filter { it.kind == FileKind.File }.forEach { entry ->
                val relative = entry.path.value.removePrefix("${from.value}/")
                copyFile(entry.path, FilePath.of("${to.value}/$relative"))
            }
            delete(from, recursive = true).getOrThrow()
        } else {
            copyFile(from, to)
            delete(from, recursive = false).getOrThrow()
        }
        notify(FileChangeBatch(listOf(FileChange.Moved(from, to))))
        return Result.success(Unit)
    }

    override suspend fun <T> transaction(block: suspend FileTransaction.() -> T): Result<T> {
        val stagedDeletes = deleted.toMutableSet()
        val result = overlay.transaction {
            val delegate = this
            val transaction = object : FileTransaction {
                override suspend fun read(path: FilePath): Result<ByteArray> {
                    if (stagedDeletes.any { it == path }) return notFound(path)
                    return delegate.read(path).fold(
                        onSuccess = { Result.success(it) },
                        onFailure = { base.read(path) },
                    )
                }

                override suspend fun write(path: FilePath, bytes: ByteArray, mode: WriteMode): Result<Unit> {
                    stagedDeletes.remove(path)
                    return delegate.write(path, bytes, mode)
                }

                override suspend fun createDirectories(path: FilePath): Result<Unit> = delegate.createDirectories(path)

                override suspend fun delete(path: FilePath, recursive: Boolean): Result<Unit> {
                    val target = stat(path).getOrNull() ?: return notFound(path)
                    val paths = if (recursive) {
                        list(path, recursive = true).getOrThrow().map(FileEntry::path)
                    } else {
                        listOf(target.path)
                    }
                    stagedDeletes += paths
                    return delegate.delete(path, recursive)
                }

                override suspend fun move(from: FilePath, to: FilePath, replace: Boolean): Result<Unit> {
                    val bytes = read(from).getOrElse { return Result.failure(it) }
                    stagedDeletes.remove(to)
                    stagedDeletes += from
                    return delegate.write(to, bytes, if (replace) WriteMode.Replace else WriteMode.CreateNew)
                }
            }
            transaction.block()
        }
        if (result.isSuccess) {
            deleted.clear()
            deleted += stagedDeletes
        }
        return result
    }

    override fun watch(path: FilePath, recursive: Boolean, listener: (FileChangeBatch) -> Unit): FileWatch {
        val watcher = Watcher(path, recursive, listener)
        watchers += watcher
        return FileWatch { watchers.remove(watcher) }
    }

    private suspend fun copyFile(from: FilePath, to: FilePath) {
        val input = openRead(from).getOrThrow()
        val output = overlay.openWrite(to, WriteMode.Replace).getOrThrow()
        try {
            while (true) {
                val chunk = input.readChunk() ?: break
                output.writeChunk(chunk)
            }
            output.commit().getOrThrow()
        } finally {
            input.close()
            output.close()
        }
        deleted.remove(to)
    }

    private suspend fun fileEntry(path: FilePath, kind: FileKind = FileKind.File): FileEntry =
        stat(path).getOrNull() ?: FileEntry(path, kind)

    private fun isDeleted(path: FilePath): Boolean = deleted.any {
        it == path || (it.value.isNotEmpty() && path.value.startsWith("${it.value}/"))
    }

    private suspend fun listOrEmpty(fileSystem: FileSystem, path: FilePath, recursive: Boolean): Result<List<FileEntry>> =
        fileSystem.list(path, recursive).fold(
            onSuccess = { Result.success(it) },
            onFailure = { if (isNotFound(it)) Result.success(emptyList()) else Result.failure(it) },
        )

    private fun isNotFound(throwable: Throwable): Boolean =
        (throwable as? FileSystemException)?.error is FileSystemError.NotFound

    private fun <T> notFound(path: FilePath): Result<T> =
        Result.failure(FileSystemException(FileSystemError.NotFound(path)))

    private fun notify(batch: FileChangeBatch) {
        watchers.toList().forEach { watcher ->
            val visible = batch.changes.filter { change ->
                val paths = when (change) {
                    is FileChange.Created -> listOf(change.entry.path)
                    is FileChange.Modified -> listOf(change.entry.path)
                    is FileChange.Deleted -> listOf(change.path)
                    is FileChange.Moved -> listOf(change.from, change.to)
                }
                paths.any(watcher::matches)
            }
            if (visible.isNotEmpty()) watcher.listener(FileChangeBatch(visible))
        }
    }

    private class CommittingWriteSession(
        private val delegate: ByteWriteSession,
        private val afterCommit: suspend () -> Result<Unit>,
    ) : ByteWriteSession {
        override suspend fun writeChunk(bytes: ByteArray) = delegate.writeChunk(bytes)
        override suspend fun commit(): Result<Unit> = delegate.commit().fold(
            onSuccess = { afterCommit() },
            onFailure = { Result.failure(it) },
        )
        override suspend fun abort() = delegate.abort()
        override fun close() = delegate.close()
    }

    private class Watcher(
        private val path: FilePath,
        private val recursive: Boolean,
        val listener: (FileChangeBatch) -> Unit,
    ) {
        fun matches(candidate: FilePath): Boolean =
            candidate == path || (recursive && (path == FilePath.Root || candidate.value.startsWith("${path.value}/")))
    }
}
