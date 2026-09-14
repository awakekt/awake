/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions", "TooGenericExceptionCaught", "ThrowsCount")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.awakekt.awake.core.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.posix.memcpy

actual fun createPlatformFileSystem(root: String?): FileSystem =
    IosFileSystem(root ?: defaultIosRoot())

private class IosFileSystem(rootPath: String) : FileSystem {
    private val manager = NSFileManager.defaultManager
    internal val root = rootPath.trimEnd('/').ifEmpty { "/" }
    private val watchers = mutableListOf<IosWatcher>()
    private var suppressNotifications = false

    init {
        manager.createDirectoryAtPath(root, withIntermediateDirectories = true, attributes = null, error = null)
    }

    override suspend fun openRead(path: FilePath): Result<ByteReadSession> = runCatching {
        val data = manager.contentsAtPath(resolve(path))
            ?: throw FileSystemException(FileSystemError.NotFound(path))
        IosReadSession(data.toByteArray())
    }

    override suspend fun openWrite(path: FilePath, mode: WriteMode): Result<ByteWriteSession> = runCatching {
        val target = resolve(path)
        if (mode == WriteMode.CreateNew && manager.fileExistsAtPath(target)) {
            throw FileSystemException(FileSystemError.AlreadyExists(path))
        }
        val initial = if (mode == WriteMode.Append) {
            manager.contentsAtPath(target)?.toByteArray()
                ?: throw FileSystemException(FileSystemError.NotFound(path))
        } else {
            ByteArray(0)
        }
        IosWriteSession(this, path, mode, initial)
    }

    override suspend fun list(path: FilePath, recursive: Boolean): Result<List<FileEntry>> = runCatching {
        val directory = resolve(path)
        if (!manager.isDirectory(directory)) {
            throw FileSystemException(FileSystemError.NotFound(path))
        }
        val names = if (recursive) {
            manager.subpathsAtPath(directory).orEmpty()
        } else {
            manager.contentsOfDirectoryAtPath(directory, error = null).orEmpty()
        }
        names.mapNotNull { name ->
            val relative = name.toString().removePrefix(directory.trimEnd('/') + "/")
            val child = if (path == FilePath.Root) relative else "${path.value}/$relative"
            val childPath = FilePath.of(child)
            val childFile = resolve(childPath)
            if (!manager.fileExistsAtPath(childFile)) null else entry(childPath, childFile)
        }.sortedBy { it.path.value }
    }

    override suspend fun stat(path: FilePath): Result<FileEntry?> = runCatching {
        val target = resolve(path)
        if (!manager.fileExistsAtPath(target)) null else entry(path, target)
    }

    override suspend fun createDirectories(path: FilePath): Result<Unit> = runCatching {
        if (!manager.createDirectoryAtPath(resolve(path), withIntermediateDirectories = true, attributes = null, error = null) &&
            !manager.isDirectory(resolve(path))
        ) {
            throw FileSystemException(FileSystemError.IoFailure("createDirectories", "Could not create ${path.value}."))
        }
    }

    override suspend fun delete(path: FilePath, recursive: Boolean): Result<Unit> = runCatching {
        if (path == FilePath.Root) throw FileSystemException(FileSystemError.InvalidPath("", "The root cannot be deleted."))
        val target = resolve(path)
        if (!manager.fileExistsAtPath(target)) throw FileSystemException(FileSystemError.NotFound(path))
        if (!recursive && manager.isDirectory(target) && list(path, false).getOrThrow().isNotEmpty()) {
            throw FileSystemException(FileSystemError.Conflict(path, "Directory is not empty."))
        }
        if (!manager.removeItemAtPath(target, error = null)) {
            throw FileSystemException(FileSystemError.IoFailure("delete", "Could not delete ${path.value}."))
        }
        notify(FileChangeBatch(listOf(FileChange.Deleted(path))))
    }

    override suspend fun move(from: FilePath, to: FilePath, replace: Boolean): Result<Unit> = runCatching {
        val source = resolve(from)
        val target = resolve(to)
        if (!manager.fileExistsAtPath(source)) throw FileSystemException(FileSystemError.NotFound(from))
        if (manager.fileExistsAtPath(target) && !replace) throw FileSystemException(FileSystemError.AlreadyExists(to))
        createDirectories(FilePath.of(to.value.substringBeforeLast('/', missingDelimiterValue = "."))).getOrThrow()
        if (manager.fileExistsAtPath(target)) manager.removeItemAtPath(target, error = null)
        if (!manager.moveItemAtPath(source, toPath = target, error = null)) {
            throw FileSystemException(FileSystemError.IoFailure("move", "Could not move ${from.value}."))
        }
        notify(FileChangeBatch(listOf(FileChange.Moved(from, to))))
    }

    override suspend fun <T> transaction(block: suspend FileTransaction.() -> T): Result<T> {
        val beforeEntries = snapshotEntriesFromDisk()
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
        val watcher = IosWatcher(path, recursive, listener)
        watchers += watcher
        return FileWatch { watchers.remove(watcher) }
    }

    private fun resolve(path: FilePath): String =
        if (path == FilePath.Root) root else "$root/${path.value}"

    private fun entry(path: FilePath, target: String): FileEntry {
        val directory = manager.isDirectory(target)
        val size = if (directory) null else manager.contentsAtPath(target)?.length?.toLong()
        return FileEntry(path, if (directory) FileKind.Directory else FileKind.File, size)
    }

    private fun snapshotFiles(): Map<FilePath, ByteArray> =
        snapshotEntriesFromDisk().filterValues { it.kind == FileKind.File }
            .mapValues { manager.contentsAtPath(resolve(it.key))!!.toByteArray() }

    private fun snapshotEntriesFromDisk(): Map<FilePath, FileEntry> =
        manager.subpathsAtPath(root).orEmpty().mapNotNull { name ->
            val path = FilePath.of(name.toString())
            path to entry(path, "$root/${path.value}")
        }.toMap()

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

private class IosReadSession(private val bytes: ByteArray) : ByteReadSession {
    private var offset = 0
    override suspend fun readChunk(maxBytes: Int): ByteArray? {
        require(maxBytes > 0) { "maxBytes must be positive" }
        if (offset >= bytes.size) return null
        val end = (offset + maxBytes).coerceAtMost(bytes.size)
        return bytes.copyOfRange(offset, end).also { offset = end }
    }
    override fun close() = Unit
}

private class IosWriteSession(
    private val owner: IosFileSystem,
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

private fun IosFileSystem.publish(path: FilePath, bytes: ByteArray, mode: WriteMode): FileChange {
    val target = resolveForWrite(path)
    val existed = NSFileManager.defaultManager.fileExistsAtPath(target)
    if (mode == WriteMode.CreateNew && existed) {
        throw FileSystemException(FileSystemError.AlreadyExists(path))
    }
    val parent = target.substringBeforeLast('/', missingDelimiterValue = root)
    NSFileManager.defaultManager.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
    val temporary = "$target.awake-${NSUUID.UUID().UUIDString}.tmp"
    val data = bytes.toNSData()
    if (!NSFileManager.defaultManager.createFileAtPath(temporary, contents = data, attributes = null)) {
        throw FileSystemException(FileSystemError.IoFailure("write", "Could not stage ${path.value}."))
    }
    try {
        if (NSFileManager.defaultManager.fileExistsAtPath(target)) NSFileManager.defaultManager.removeItemAtPath(target, error = null)
        if (!NSFileManager.defaultManager.moveItemAtPath(temporary, toPath = target, error = null)) {
            throw FileSystemException(FileSystemError.IoFailure("write", "Could not atomically publish ${path.value}."))
        }
    } finally {
        NSFileManager.defaultManager.removeItemAtPath(temporary, error = null)
    }
    val entry = FileEntry(path, FileKind.File, bytes.size.toLong())
    return if (existed) FileChange.Modified(entry) else FileChange.Created(entry)
}

private fun IosFileSystem.resolveForWrite(path: FilePath): String =
    if (path == FilePath.Root) root else "$root/${path.value}"

private fun NSFileManager.isDirectory(path: String): Boolean =
    fileExistsAtPath(path) && contentsOfDirectoryAtPath(path, error = null) != null

private data class IosWatcher(
    val path: FilePath,
    val recursive: Boolean,
    val listener: (FileChangeBatch) -> Unit,
) {
    fun matches(candidate: FilePath): Boolean =
        path == FilePath.Root || candidate == path || (recursive && candidate.value.startsWith("${path.value}/"))
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply { usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) } }
}

private fun ByteArray.toNSData(): NSData = memScoped { NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong()) }

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

private fun diffEntries(before: Map<FilePath, FileEntry>, after: Map<FilePath, FileEntry>): List<FileChange> = buildList {
    before.keys.filter { it !in after }.sortedBy { it.value }.forEach { add(FileChange.Deleted(it)) }
    after.keys.filter { it !in before }.sortedBy { it.value }.forEach { add(FileChange.Created(after.getValue(it))) }
    after.keys.filter { it in before && before.getValue(it) != after.getValue(it) }.sortedBy { it.value }
        .forEach { add(FileChange.Modified(after.getValue(it))) }
}

private fun defaultIosRoot(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull()?.toString()
        ?: NSTemporaryDirectory()

private suspend fun captureEntries(fs: FileSystem): Map<FilePath, FileEntry> =
    fs.list(FilePath.Root, recursive = true).getOrThrow().associateBy { it.path }
