/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions", "TooGenericExceptionCaught", "LoopWithTooManyJumpStatements")

package com.awakekt.awake.core.io

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

actual fun createPlatformFileSystem(root: String?): FileSystem =
    NioFileSystem(Path.of(root ?: ".").toAbsolutePath().normalize())

private class NioFileSystem(private val root: Path) : FileSystem {
    init {
        Files.createDirectories(root)
    }

    override suspend fun openRead(path: FilePath): Result<ByteReadSession> = runCatching {
        val target = resolve(path)
        if (!Files.isRegularFile(target)) throw FileSystemException(FileSystemError.NotFound(path))
        MemoryByteReadSession(Files.readAllBytes(target))
    }

    override suspend fun openWrite(path: FilePath, mode: WriteMode): Result<ByteWriteSession> = runCatching {
        val target = resolve(path)
        if (mode == WriteMode.CreateNew && Files.exists(target)) {
            throw FileSystemException(FileSystemError.AlreadyExists(path))
        }
        if (mode == WriteMode.Append && !Files.isRegularFile(target)) {
            throw FileSystemException(FileSystemError.NotFound(path))
        }
        NioByteWriteSession(this, path, mode, if (mode == WriteMode.Append) Files.readAllBytes(target) else ByteArray(0))
    }

    override suspend fun list(path: FilePath, recursive: Boolean): Result<List<FileEntry>> = runCatching {
        val directory = resolve(path)
        if (!Files.isDirectory(directory)) throw FileSystemException(FileSystemError.NotFound(path))
        val stream = if (recursive) Files.walk(directory) else Files.list(directory)
        stream.use { paths ->
            paths.filter { it != directory }
                .map { child ->
                    val relative = root.relativize(child).toString().replace('\\', '/')
                    FileEntry(
                        FilePath.of(relative),
                        if (Files.isDirectory(child)) FileKind.Directory else FileKind.File,
                        Files.size(child).takeIf { Files.isRegularFile(child) },
                        Files.getLastModifiedTime(child).toMillis(),
                    )
                }
                .sorted(Comparator.comparing { it.path.value })
                .toList()
        }
    }

    override suspend fun stat(path: FilePath): Result<FileEntry?> = runCatching {
        val target = resolve(path)
        if (!Files.exists(target)) return@runCatching null
        FileEntry(
            path,
            if (Files.isDirectory(target)) FileKind.Directory else FileKind.File,
            Files.size(target).takeIf { Files.isRegularFile(target) },
            Files.getLastModifiedTime(target).toMillis(),
        )
    }

    override suspend fun createDirectories(path: FilePath): Result<Unit> = runCatching {
        Files.createDirectories(resolve(path))
    }

    override suspend fun delete(path: FilePath, recursive: Boolean): Result<Unit> = runCatching {
        val target = resolve(path)
        if (!Files.exists(target)) throw FileSystemException(FileSystemError.NotFound(path))
        if (Files.isDirectory(target)) {
            val children = Files.list(target).use { it.findAny().isPresent }
            if (children && !recursive) throw FileSystemException(FileSystemError.Conflict(path, "Directory is not empty."))
            if (recursive) {
                Files.walk(target).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            } else {
                Files.delete(target)
            }
        } else {
            Files.delete(target)
        }
    }

    override suspend fun move(from: FilePath, to: FilePath, replace: Boolean): Result<Unit> = runCatching {
        val source = resolve(from)
        val target = resolve(to)
        if (!Files.exists(source)) throw FileSystemException(FileSystemError.NotFound(from))
        Files.createDirectories(target.parent)
        if (replace) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } else {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        }
    }

    override suspend fun <T> transaction(block: suspend FileTransaction.() -> T): Result<T> {
        val before = snapshotFiles()
        val staged = InMemoryFileSystem(before)
        val result = staged.transaction(block)
        if (result.isFailure) return result

        val after = staged.list(FilePath.Root, recursive = true).getOrThrow()
            .filter { it.kind == FileKind.File }
            .associate { it.path to staged.read(it.path).getOrThrow() }
        return try {
            reconcile(before, after)
            result
        } catch (cause: Throwable) {
            runCatching { reconcile(after, before) }
            Result.failure(
                cause.takeIf { it is FileSystemException }
                    ?: FileSystemException(FileSystemError.IoFailure("transaction", cause.message ?: cause::class.simpleName.orEmpty())),
            )
        }
    }

    override fun watch(path: FilePath, recursive: Boolean, listener: (FileChangeBatch) -> Unit): FileWatch {
        val watchService = FileSystems.getDefault().newWatchService()
        val directory = resolve(path)
        runCatching {
            directory.register(
                watchService,
                java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                java.nio.file.StandardWatchEventKinds.ENTRY_DELETE,
            )
        }
        val worker = thread(isDaemon = true, name = "awake-file-watch") {
            while (!Thread.currentThread().isInterrupted) {
                val key = runCatching { watchService.take() }.getOrNull() ?: break
                val changes = key.pollEvents().mapNotNull { event ->
                    val name = event.context() as? Path ?: return@mapNotNull null
                    val child = FilePath.of(listOf(path.value, name.toString()).filter { it.isNotEmpty() }.joinToString("/"))
                    FileChange.Modified(FileEntry(child, FileKind.File))
                }
                if (changes.isNotEmpty()) listener(FileChangeBatch(changes))
                if (!key.reset()) break
            }
        }
        return FileWatch {
            worker.interrupt()
            watchService.close()
        }
    }

    private fun resolve(path: FilePath): Path = root.resolve(path.value).normalize().also {
        require(it.startsWith(root)) { "Path escaped filesystem root: $path" }
    }

    private fun snapshotFiles(): Map<FilePath, ByteArray> =
        Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile).toList().associate { path ->
                FilePath.of(root.relativize(path).toString().replace('\\', '/')) to Files.readAllBytes(path)
            }
        }

    private suspend fun reconcile(before: Map<FilePath, ByteArray>, after: Map<FilePath, ByteArray>) {
        before.keys.filter { it !in after }.sortedByDescending { it.value.length }.forEach { delete(it, false).getOrThrow() }
        after.keys.sortedBy { it.value.count { character -> character == '/' } }.forEach { path ->
            val bytes = after.getValue(path)
            if (!before[path].contentEqualsOrNull(bytes)) write(path, bytes).getOrThrow()
        }
    }

    private class NioByteWriteSession(
        private val owner: NioFileSystem,
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
            val destination = owner.resolve(path)
            Files.createDirectories(destination.parent)
            val temporary = Files.createTempFile(destination.parent, ".awake-", ".tmp")
            try {
                Files.write(temporary, chunks.flattenToByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
                if (mode == WriteMode.CreateNew && Files.exists(destination)) {
                    throw FileSystemException(FileSystemError.AlreadyExists(path))
                }
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(temporary)
            }
            closed = true
        }

        override suspend fun abort() {
            closed = true
            chunks.clear()
        }
        override fun close() {
            closed = true
        }
    }
}

private fun ByteArray?.contentEqualsOrNull(other: ByteArray): Boolean =
    this?.contentEquals(other) == true

private class MemoryByteReadSession(private val bytes: ByteArray) : ByteReadSession {
    private var offset = 0
    override suspend fun readChunk(maxBytes: Int): ByteArray? {
        require(maxBytes > 0) { "maxBytes must be positive" }
        if (offset >= bytes.size) return null
        val end = (offset + maxBytes).coerceAtMost(bytes.size)
        return bytes.copyOfRange(offset, end).also { offset = end }
    }

    override fun close() = Unit
}

private fun List<ByteArray>.flattenToByteArray(): ByteArray {
    val total = sumOf { it.size }
    val result = ByteArray(total)
    var offset = 0
    forEach { chunk ->
        chunk.copyInto(result, offset)
        offset += chunk.size
    }
    return result
}
