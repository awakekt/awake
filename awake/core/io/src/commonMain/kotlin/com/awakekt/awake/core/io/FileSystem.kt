/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions")

package com.awakekt.awake.core.io

/**
 * Whether a filesystem entry is a regular file or directory.
 */
enum class FileKind {
    /** Regular file containing binary or text data. */
    File,

    /** Directory containing child filesystem entries. */
    Directory,
}

/**
 * Metadata returned by filesystem listing and stat operations.
 *
 * @property path The root-relative path of the filesystem entry.
 * @property kind Whether the entry is a regular file or directory.
 * @property sizeBytes Size of the file in bytes, or null if unknown or if the entry is a directory.
 * @property modifiedAtEpochMs Last modification timestamp in milliseconds since Unix epoch, or null if unknown.
 */
data class FileEntry(
    val path: FilePath,
    val kind: FileKind,
    val sizeBytes: Long? = null,
    val modifiedAtEpochMs: Long? = null,
)

/**
 * How a write session handles an existing file.
 */
enum class WriteMode {
    /** Overwrites any existing file or creates a new one. */
    Replace,

    /** Creates a new file, failing if the target path already exists. */
    CreateNew,

    /** Appends bytes to an existing file, creating it if it does not exist. */
    Append,
}

/**
 * Structured failure categories shared by every platform adapter.
 */
sealed interface FileSystemError {
    /**
     * Error indicating that a target file or directory does not exist.
     *
     * @property path The path that was not found.
     */
    data class NotFound(val path: FilePath) : FileSystemError

    /**
     * Error indicating that a target file or directory already exists.
     *
     * @property path The path that unexpectedly exists.
     */
    data class AlreadyExists(val path: FilePath) : FileSystemError

    /**
     * Error indicating an invalid or illegal path string.
     *
     * @property input The raw path string that failed validation.
     * @property reason Description of why the path is invalid.
     */
    data class InvalidPath(val input: String, val reason: String) : FileSystemError

    /**
     * Error indicating insufficient permissions to access the target path.
     *
     * @property path The path for which access was denied.
     */
    data class PermissionDenied(val path: FilePath) : FileSystemError

    /**
     * Error indicating a conflict preventing the operation (e.g. non-empty directory or locking conflict).
     *
     * @property path The path where the conflict occurred.
     * @property reason Description of the conflict condition.
     */
    data class Conflict(val path: FilePath, val reason: String) : FileSystemError

    /**
     * Error indicating that the requested filesystem operation is not supported by this backend.
     *
     * @property operation Name of the unsupported operation.
     */
    data class Unsupported(val operation: String) : FileSystemError

    /**
     * Error representing a general I/O subsystem failure.
     *
     * @property operation The operation during which the failure occurred.
     * @property causeMessage Diagnostic error message from the underlying platform.
     */
    data class IoFailure(val operation: String, val causeMessage: String) : FileSystemError
}

/**
 * Throwable wrapper used when a [Result] needs to carry a structured filesystem failure.
 *
 * @property error The structured filesystem error that caused this exception.
 */
class FileSystemException(
    val error: FileSystemError,
) : IllegalStateException(error.toString())

/**
 * Reads bounded chunks; `null` means end-of-file.
 */
interface ByteReadSession : AutoCloseable {
    /**
     * Reads the next chunk of bytes up to [maxBytes] in length.
     *
     * @param maxBytes The maximum number of bytes to read into the chunk.
     * @return The read byte chunk, or null if end of stream is reached.
     */
    suspend fun readChunk(maxBytes: Int = DEFAULT_CHUNK_SIZE): ByteArray?
}

/**
 * Collects chunks and makes them visible only after [commit].
 */
interface ByteWriteSession : AutoCloseable {
    /**
     * Writes a chunk of bytes to the pending write buffer.
     *
     * @param bytes The byte array to write.
     */
    suspend fun writeChunk(bytes: ByteArray)

    /**
     * Commits all buffered chunks atomically to persistent storage.
     *
     * @return Success result or failure with [FileSystemException].
     */
    suspend fun commit(): Result<Unit>

    /**
     * Discards any uncommitted written chunks and closes the session.
     */
    suspend fun abort()
}

/**
 * One logical change in a committed filesystem batch.
 */
sealed interface FileChange {
    /**
     * Indicates that a new file or directory was created.
     *
     * @property entry The metadata for the created entry.
     */
    data class Created(val entry: FileEntry) : FileChange

    /**
     * Indicates that an existing file was modified.
     *
     * @property entry The updated metadata for the modified entry.
     */
    data class Modified(val entry: FileEntry) : FileChange

    /**
     * Indicates that a file or directory was deleted.
     *
     * @property path The path of the deleted entry.
     */
    data class Deleted(val path: FilePath) : FileChange

    /**
     * Indicates that a file or directory was moved or renamed.
     *
     * @property from The previous path of the entry.
     * @property to The new path of the entry.
     */
    data class Moved(val from: FilePath, val to: FilePath) : FileChange
}

/**
 * Ordered changes emitted after one successful logical commit.
 *
 * @property changes The list of individual file mutations comprising this batch.
 */
data class FileChangeBatch(val changes: List<FileChange>)

/**
 * Handle returned by [FileSystem.watch].
 */
fun interface FileWatch : AutoCloseable {
    override fun close()
}

/**
 * Operations allowed inside an all-or-nothing transaction.
 */
interface FileTransaction {
    /**
     * Reads all bytes from [path].
     *
     * @param path The path to read.
     * @return The entire file contents as a byte array.
     */
    suspend fun read(path: FilePath): Result<ByteArray>

    /**
     * Writes all [bytes] to [path] using [mode].
     *
     * @param path The target file path.
     * @param bytes The raw byte content to write.
     * @param mode How existing files should be handled.
     * @return Success result or failure.
     */
    suspend fun write(path: FilePath, bytes: ByteArray, mode: WriteMode = WriteMode.Replace): Result<Unit>

    /**
     * Ensures all directories in [path] exist.
     *
     * @param path The directory path to create.
     * @return Success result or failure.
     */
    suspend fun createDirectories(path: FilePath): Result<Unit>

    /**
     * Deletes the entry at [path].
     *
     * @param path The target path to delete.
     * @param recursive Whether to recursively delete children if [path] is a directory.
     * @return Success result or failure.
     */
    suspend fun delete(path: FilePath, recursive: Boolean = false): Result<Unit>

    /**
     * Moves or renames an entry from [from] to [to].
     *
     * @param from The source path.
     * @param to The destination path.
     * @param replace Whether to replace an existing target at [to].
     * @return Success result or failure.
     */
    suspend fun move(from: FilePath, to: FilePath, replace: Boolean = false): Result<Unit>
}

/**
 * Rooted asynchronous filesystem contract used by Core and adapted by Studio.
 */
interface FileSystem {
    /**
     * Reads the entire contents of [path] into a byte array.
     *
     * @param path The relative file path to read.
     * @return Success result containing the byte array, or failure.
     */
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

    /**
     * Opens a chunked read session for [path].
     *
     * @param path The relative file path to open.
     * @return Success result containing the read session, or failure.
     */
    suspend fun openRead(path: FilePath): Result<ByteReadSession>

    /**
     * Writes all [bytes] to [path] using [mode].
     *
     * @param path The target relative file path.
     * @param bytes The raw byte content to write.
     * @param mode How existing files should be handled.
     * @return Success result or failure.
     */
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

    /**
     * Opens a chunked write session for [path] using [mode].
     *
     * @param path The target relative file path.
     * @param mode How existing files should be handled.
     * @return Success result containing the write session, or failure.
     */
    suspend fun openWrite(path: FilePath, mode: WriteMode = WriteMode.Replace): Result<ByteWriteSession>

    /**
     * Lists directory entries at [path].
     *
     * @param path The directory path to list, defaulting to root.
     * @param recursive Whether to recursively traverse subdirectories.
     * @return Success result containing the list of child [FileEntry] items, or failure.
     */
    suspend fun list(path: FilePath = FilePath.Root, recursive: Boolean = false): Result<List<FileEntry>>

    /**
     * Queries metadata for the entry at [path].
     *
     * @param path The path to inspect.
     * @return Success result containing [FileEntry], or null if the path does not exist.
     */
    suspend fun stat(path: FilePath): Result<FileEntry?>

    /**
     * Creates all directories along [path] if they do not already exist.
     *
     * @param path The directory path to create.
     * @return Success result or failure.
     */
    suspend fun createDirectories(path: FilePath): Result<Unit>

    /**
     * Deletes the file or directory at [path].
     *
     * @param path The path to delete.
     * @param recursive Whether to recursively delete children if [path] is a directory.
     * @return Success result or failure.
     */
    suspend fun delete(path: FilePath, recursive: Boolean = false): Result<Unit>

    /**
     * Moves or renames an entry from [from] to [to].
     *
     * @param from The current path of the entry.
     * @param to The new destination path.
     * @param replace Whether to overwrite an existing entry at [to].
     * @return Success result or failure.
     */
    suspend fun move(from: FilePath, to: FilePath, replace: Boolean = false): Result<Unit>

    /**
     * Executes [block] atomically within a transactional scope.
     *
     * @param T The return type of the transaction block.
     * @param block The transactional operations to execute.
     * @return Success result containing the return value of [block], or failure.
     */
    suspend fun <T> transaction(block: suspend FileTransaction.() -> T): Result<T>

    /**
     * Registers a file watcher callback for mutations under [path].
     *
     * @param path The root-relative path to watch, defaulting to root.
     * @param recursive Whether to watch child subdirectories recursively.
     * @param listener Callback invoked whenever batches of changes occur.
     * @return A closeable [FileWatch] handle to terminate watching.
     */
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
