/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class, kotlin.io.encoding.ExperimentalEncodingApi::class)
@file:Suppress("TooManyFunctions")

package com.awakekt.awake.core.io

import kotlin.io.encoding.Base64

/**
 * Browser filesystem adapter.
 *
 * A browser cannot access a project directory without a user-selected File System Access handle.
 * The default Core adapter therefore uses the same transactional reference implementation as
 * desktop, and persists its file bytes in localStorage when that capability exists. Studio can
 * provide a user-selected handle later without changing the Core [FileSystem] contract.
 */
actual fun createPlatformFileSystem(root: String?): FileSystem =
    WasmVirtualFileSystem(storageKey(root ?: "default"))

private class WasmVirtualFileSystem(private val key: String) : FileSystem {
    private val delegate = InMemoryFileSystem(loadFiles(key))

    override suspend fun openRead(path: FilePath): Result<ByteReadSession> = delegate.openRead(path)

    override suspend fun openWrite(path: FilePath, mode: WriteMode): Result<ByteWriteSession> =
        delegate.openWrite(path, mode).map { session ->
            PersistingWriteSession(session) { persist() }
        }

    override suspend fun read(path: FilePath): Result<ByteArray> = delegate.read(path)

    override suspend fun write(path: FilePath, bytes: ByteArray, mode: WriteMode): Result<Unit> =
        delegate.write(path, bytes, mode).persistIfSuccessful()

    override suspend fun list(path: FilePath, recursive: Boolean): Result<List<FileEntry>> = delegate.list(path, recursive)

    override suspend fun stat(path: FilePath): Result<FileEntry?> = delegate.stat(path)

    override suspend fun createDirectories(path: FilePath): Result<Unit> =
        delegate.createDirectories(path).persistIfSuccessful()

    override suspend fun delete(path: FilePath, recursive: Boolean): Result<Unit> =
        delegate.delete(path, recursive).persistIfSuccessful()

    override suspend fun move(from: FilePath, to: FilePath, replace: Boolean): Result<Unit> =
        delegate.move(from, to, replace).persistIfSuccessful()

    override suspend fun <T> transaction(block: suspend FileTransaction.() -> T): Result<T> =
        delegate.transaction(block).persistIfSuccessful()

    override fun watch(path: FilePath, recursive: Boolean, listener: (FileChangeBatch) -> Unit): FileWatch =
        delegate.watch(path, recursive, listener)

    private suspend fun persist(): Result<Unit> = runCatching {
        val entries = delegate.list(FilePath.Root, recursive = true).getOrThrow()
            .filter { it.kind == FileKind.File }
            .sortedBy { it.path.value }
        val files = entries.map { entry ->
            "${Base64.encode(entry.path.value.encodeToByteArray())}:${Base64.encode(delegate.read(entry.path).getOrThrow())}"
        }.joinToString("\n")
        jsStorageSet(key, files)
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(it.toStorageException()) },
    )

    private suspend fun <T> Result<T>.persistIfSuccessful(): Result<T> {
        if (isFailure) return this
        val persistence = persist()
        return persistence.fold(
            onSuccess = { this },
            onFailure = { Result.failure(it) },
        )
    }
}

private class PersistingWriteSession(
    private val delegate: ByteWriteSession,
    private val persist: suspend () -> Result<Unit>,
) : ByteWriteSession {
    override suspend fun writeChunk(bytes: ByteArray) = delegate.writeChunk(bytes)

    override suspend fun commit(): Result<Unit> = delegate.commit().fold(
        onSuccess = { persist() },
        onFailure = { Result.failure(it) },
    )

    override suspend fun abort() = delegate.abort()

    override fun close() = delegate.close()
}

private fun loadFiles(key: String): Map<FilePath, ByteArray> {
    val encoded = runCatching { jsStorageGet(key) }.getOrNull() ?: return emptyMap()
    return encoded.lineSequence().filter { it.isNotEmpty() }.mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) return@mapNotNull null
        runCatching {
            FilePath.of(Base64.decode(line.substring(0, separator)).decodeToString()) to
                Base64.decode(line.substring(separator + 1))
        }.getOrNull()
    }.toMap()
}

private fun storageKey(root: String): String = "awake.core.io.v1:$root"

private fun Throwable.toStorageException(): FileSystemException =
    this as? FileSystemException
        ?: FileSystemException(FileSystemError.IoFailure("persist", message ?: "Browser storage failed."))

@JsFun("(key) => globalThis.localStorage ? globalThis.localStorage.getItem(key) : null")
private external fun jsStorageGet(key: String): String?

@JsFun("(key, value) => { if (globalThis.localStorage) globalThis.localStorage.setItem(key, value); }")
private external fun jsStorageSet(key: String, value: String)
