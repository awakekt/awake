/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions")

package com.awakekt.awake.core.io

/** Whether a filesystem entry is a regular file or directory. */
enum class FileKind { File, Directory }

/** Metadata returned by filesystem listing and stat operations. */
data class FileEntry(
    val path: FilePath,
    val kind: FileKind,
    val sizeBytes: Long? = null,
    val modifiedAtEpochMs: Long? = null,
)

/** How a write session handles an existing file. */
enum class WriteMode { Replace, CreateNew, Append }

/** Structured failure categories shared by every platform adapter. */
sealed interface FileSystemError {
    data class NotFound(val path: FilePath) : FileSystemError
    data class AlreadyExists(val path: FilePath) : FileSystemError
    data class InvalidPath(val input: String, val reason: String) : FileSystemError
    data class PermissionDenied(val path: FilePath) : FileSystemError
    data class Conflict(val path: FilePath, val reason: String) : FileSystemError
    data class Unsupported(val operation: String) : FileSystemError
    data class IoFailure(val operation: String, val causeMessage: String) : FileSystemError
}

/** Throwable wrapper used when a [Result] needs to carry a structured filesystem failure. */
class FileSystemException(
    val error: FileSystemError,
) : IllegalStateException(error.toString())

/** Reads bounded chunks; `null` means end-of-file. */
interface ByteReadSession : AutoCloseable {
    suspend fun readChunk(maxBytes: Int = DEFAULT_CHUNK_SIZE): ByteArray?
}

/** Collects chunks and makes them visible only after [commit]. */
interface ByteWriteSession : AutoCloseable {
    suspend fun writeChunk(bytes: ByteArray)
    suspend fun commit(): Result<Unit>
    suspend fun abort()
}

/** One logical change in a committed filesystem batch. */
sealed interface FileChange {
    data class Created(val entry: FileEntry) : FileChange
    data class Modified(val entry: FileEntry) : FileChange
    data class Deleted(val path: FilePath) : FileChange
    data class Moved(val from: FilePath, val to: FilePath) : FileChange
}

/** Ordered changes emitted after one successful logical commit. */
data class FileChangeBatch(val changes: List<FileChange>)

/** Handle returned by [FileSystem.watch]. */
fun interface FileWatch : AutoCloseable {
    override fun close()
}

/** Operations allowed inside an all-or-nothing transaction. */
interface FileTransaction {
    suspend fun read(path: FilePath): Result<ByteArray>
    suspend fun write(path: FilePath, bytes: ByteArray, mode: WriteMode = WriteMode.Replace): Result<Unit>
    suspend fun createDirectories(path: FilePath): Result<Unit>
    suspend fun delete(path: FilePath, recursive: Boolean = false): Result<Unit>
    suspend fun move(from: FilePath, to: FilePath, replace: Boolean = false): Result<Unit>
}

/** Rooted asynchronous filesystem contract used by Core and adapted by Studio. */
interface FileSystem {
    suspend fun read(path: FilePath): Result<ByteArray> =
        openRead(path).fold(
            onSuccess = { session ->
                runCatching {
                    val chunks = mutableListOf<ByteArray>()
                    while (true) {
                        val chunk = session.readChunk() ?: break
                        chunks += chunk
                    }
                    chunks.flattenToByteArray()
                }.also { session.close() }
            },
            onFailure = { Result.failure(it) },
        )

    suspend fun openRead(path: FilePath): Result<ByteReadSession>

    suspend fun write(path: FilePath, bytes: ByteArray, mode: WriteMode = WriteMode.Replace): Result<Unit> =
        openWrite(path, mode).fold(
            onSuccess = { session ->
                runCatching {
                    session.writeChunk(bytes)
                    session.commit().getOrThrow()
                }.also { if (it.isFailure) session.abort() }
            },
            onFailure = { Result.failure(it) },
        )

    suspend fun openWrite(path: FilePath, mode: WriteMode = WriteMode.Replace): Result<ByteWriteSession>

    suspend fun list(path: FilePath = FilePath.Root, recursive: Boolean = false): Result<List<FileEntry>>

    suspend fun stat(path: FilePath): Result<FileEntry?>

    suspend fun createDirectories(path: FilePath): Result<Unit>

    suspend fun delete(path: FilePath, recursive: Boolean = false): Result<Unit>

    suspend fun move(from: FilePath, to: FilePath, replace: Boolean = false): Result<Unit>

    suspend fun <T> transaction(block: suspend FileTransaction.() -> T): Result<T>

    fun watch(
        path: FilePath = FilePath.Root,
        recursive: Boolean = false,
        listener: (FileChangeBatch) -> Unit,
    ): FileWatch
}

/** A Core-owned stream size that keeps allocations bounded on large assets. */
const val DEFAULT_CHUNK_SIZE: Int = 64 * 1024

internal fun List<ByteArray>.flattenToByteArray(): ByteArray {
    val total = sumOf { it.size }
    val output = ByteArray(total)
    var offset = 0
    forEach { chunk ->
        chunk.copyInto(output, offset)
        offset += chunk.size
    }
    return output
}
