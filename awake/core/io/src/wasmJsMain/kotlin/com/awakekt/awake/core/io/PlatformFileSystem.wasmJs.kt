/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class, kotlin.io.encoding.ExperimentalEncodingApi::class)
@file:Suppress("TooManyFunctions", "LongMethod")

package com.awakekt.awake.core.io

import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * Browser-local filesystem backed by versioned IndexedDB records.
 *
 * Metadata and content are stored separately. The metadata index is loaded when the filesystem is
 * first used; file bytes are fetched from IndexedDB only for [openRead]. URL projects should
 * compose this writable layer with [OverlayFileSystem] instead of writing into their remote source.
 */
actual fun createPlatformFileSystem(root: String?): FileSystem =
    IndexedDbFileSystem("awake.core.io.v2:${root ?: "default"}")

private class IndexedDbFileSystem(
    private val namespace: String,
) : FileSystem {
    private val metadata = linkedMapOf<FilePath, StoredMetadata>()
    private val watchers = mutableListOf<Watcher>()
    private var loaded = false
    private var clock = 0L

    override suspend fun openRead(path: FilePath): Result<ByteReadSession> = runCatching {
        ensureLoaded()
        val entry = metadata[path] ?: throw FileSystemException(FileSystemError.NotFound(path))
        if (entry.kind != FileKind.File) {
            throw FileSystemException(FileSystemError.Conflict(path, "A directory cannot be read as a file."))
        }
        val encoded = jsIndexedDbRead(namespace, path.value).await<JsAny?>()?.toString()
            ?: throw FileSystemException(FileSystemError.NotFound(path))
        val bytes = Base64.decode(encoded)
        check(bytes.size.toLong() == entry.sizeBytes) { "IndexedDB content size mismatch for '${path.value}'." }
        ByteArrayReadSession(bytes)
    }

    override suspend fun openWrite(path: FilePath, mode: WriteMode): Result<ByteWriteSession> = runCatching {
        ensureLoaded()
        if (metadata[path]?.kind == FileKind.Directory) {
            throw FileSystemException(FileSystemError.Conflict(path, "A directory cannot be written as a file."))
        }
        if (mode == WriteMode.CreateNew && metadata.containsKey(path)) {
            throw FileSystemException(FileSystemError.AlreadyExists(path))
        }
        val initial = if (mode == WriteMode.Append) read(path).getOrThrow() else ByteArray(0)
        IndexedDbWriteSession(initial) { bytes ->
            val existed = metadata[path]?.kind == FileKind.File
            ensureParentDirectories(path)
            val stored = StoredMetadata(FileKind.File, bytes.size.toLong(), nextTimestamp())
            metadata[path] = stored
            jsIndexedDbPut(
                namespace,
                path.value,
                stored.kind.name,
                stored.sizeBytes,
                stored.modifiedAtEpochMs,
                Base64.encode(bytes),
            ).await<JsAny?>()
            notify(
                FileChangeBatch(
                    listOf(if (existed) FileChange.Modified(fileEntry(path, stored)) else FileChange.Created(fileEntry(path, stored))),
                ),
            )
        }
    }

    override suspend fun list(path: FilePath, recursive: Boolean): Result<List<FileEntry>> = runCatching {
        ensureLoaded()
        if (path != FilePath.Root && !metadata.containsKey(path)) {
            throw FileSystemException(FileSystemError.NotFound(path))
        }
        metadata.keys
            .filter { it != path && isChild(path, it, recursive) }
            .map { fileEntry(it, metadata.getValue(it)) }
            .sortedBy { it.path.value }
    }

    override suspend fun stat(path: FilePath): Result<FileEntry?> = runCatching {
        ensureLoaded()
        metadata[path]?.let { fileEntry(path, it) }
    }

    override suspend fun createDirectories(path: FilePath): Result<Unit> = runCatching {
        ensureLoaded()
        ensureParentDirectories(path)
        if (metadata[path] == null) {
            val stored = StoredMetadata(FileKind.Directory, null, nextTimestamp())
            metadata[path] = stored
            jsIndexedDbPut(namespace, path.value, stored.kind.name, null, stored.modifiedAtEpochMs, null).await<JsAny?>()
        }
    }

    override suspend fun delete(path: FilePath, recursive: Boolean): Result<Unit> = runCatching {
        ensureLoaded()
        if (path == FilePath.Root) {
            throw FileSystemException(FileSystemError.InvalidPath(path.value, "The root cannot be deleted."))
        }
        val entry = metadata[path] ?: throw FileSystemException(FileSystemError.NotFound(path))
        val children = if (entry.kind == FileKind.Directory) metadata.keys.filter { it.value.startsWith("${path.value}/") } else emptyList()
        if (children.isNotEmpty() && !recursive) {
            throw FileSystemException(FileSystemError.Conflict(path, "Directory is not empty."))
        }
        val targets = children + path
        targets.forEach {
            metadata.remove(it)
            jsIndexedDbDelete(namespace, it.value).await<JsAny?>()
        }
        notify(FileChangeBatch(targets.map(FileChange::Deleted)))
    }

    override suspend fun move(from: FilePath, to: FilePath, replace: Boolean): Result<Unit> = runCatching {
        ensureLoaded()
        val source = metadata[from] ?: throw FileSystemException(FileSystemError.NotFound(from))
        if (!replace && metadata.containsKey(to)) {
            throw FileSystemException(FileSystemError.AlreadyExists(to))
        }
        ensureParentDirectories(to)
        val targets = if (source.kind == FileKind.Directory) {
            metadata.keys.filter { it == from || it.value.startsWith("${from.value}/") }
        } else {
            listOf(from)
        }
        targets.sortedBy { it.value.length }.forEach { oldPath ->
            val newPath = remap(from, to, oldPath)
            val oldMetadata = metadata.getValue(oldPath)
            val content = if (oldMetadata.kind == FileKind.File) {
                jsIndexedDbRead(namespace, oldPath.value).await<JsAny?>()?.toString()
            } else null
            metadata.remove(oldPath)
            metadata[newPath] = oldMetadata
            jsIndexedDbMove(namespace, oldPath.value, newPath.value, content).await<JsAny?>()
        }
        notify(FileChangeBatch(listOf(FileChange.Moved(from, to))))
    }

    override suspend fun <T> transaction(block: suspend FileTransaction.() -> T): Result<T> = runCatching {
        ensureLoaded()
        val snapshot = metadata.keys
            .filter { metadata.getValue(it).kind == FileKind.File }
            .associateWith { read(it).getOrThrow() }
        val staged = InMemoryFileSystem(snapshot)
        val value = staged.transaction(block).getOrThrow()
        val next = staged.list(FilePath.Root, recursive = true).getOrThrow()
            .filter { it.kind == FileKind.File }
            .associate { it.path to staged.read(it.path).getOrThrow() }
        metadata.keys.toList().forEach { delete(it, recursive = true).getOrThrow() }
        next.forEach { (path, bytes) -> write(path, bytes).getOrThrow() }
        value
    }

    override fun watch(path: FilePath, recursive: Boolean, listener: (FileChangeBatch) -> Unit): FileWatch {
        val watcher = Watcher(path, recursive, listener)
        watchers += watcher
        return FileWatch { watchers.remove(watcher) }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        val encoded = jsIndexedDbList(namespace).await<JsAny?>()?.toString().orEmpty()
        Json.decodeFromString<List<StoredMetadataRecord>>(encoded).forEach { record ->
            val path = FilePath.of(record.path)
            val stored = StoredMetadata(FileKind.valueOf(record.kind), record.sizeBytes, record.modifiedAtEpochMs)
            metadata[path] = stored
            clock = maxOf(clock, stored.modifiedAtEpochMs ?: 0L)
        }
        loaded = true
    }

    private suspend fun ensureParentDirectories(path: FilePath) {
        val parts = path.value.split('/').dropLast(1)
        var current = ""
        for (part in parts) {
            current = listOf(current, part).filter(String::isNotEmpty).joinToString("/")
            val parent = FilePath.of(current)
            if (metadata[parent] == null) {
                val stored = StoredMetadata(FileKind.Directory, null, nextTimestamp())
                metadata[parent] = stored
                jsIndexedDbPut(namespace, parent.value, stored.kind.name, null, stored.modifiedAtEpochMs, null).await<JsAny?>()
            }
        }
    }

    private fun nextTimestamp(): Long = ++clock

    private fun fileEntry(path: FilePath, value: StoredMetadata): FileEntry =
        FileEntry(path, value.kind, value.sizeBytes, value.modifiedAtEpochMs)

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

    private data class StoredMetadata(val kind: FileKind, val sizeBytes: Long?, val modifiedAtEpochMs: Long?)

    @Serializable
    private data class StoredMetadataRecord(
        val path: String,
        val kind: String,
        val sizeBytes: Long? = null,
        val modifiedAtEpochMs: Long? = null,
    )

    private class ByteArrayReadSession(private val bytes: ByteArray) : ByteReadSession {
        private var offset = 0
        override suspend fun readChunk(maxBytes: Int): ByteArray? {
            require(maxBytes > 0) { "maxBytes must be positive" }
            if (offset >= bytes.size) return null
            val end = (offset + maxBytes).coerceAtMost(bytes.size)
            return bytes.copyOfRange(offset, end).also { offset = end }
        }
        override fun close() = Unit
    }

    private class IndexedDbWriteSession(
        initial: ByteArray,
        private val commitBytes: suspend (ByteArray) -> Unit,
    ) : ByteWriteSession {
        private val chunks = mutableListOf(initial)
        private var closed = false
        override suspend fun writeChunk(bytes: ByteArray) {
            check(!closed) { "Write session is closed" }
            chunks += bytes.copyOf()
        }
        override suspend fun commit(): Result<Unit> = runCatching {
            check(!closed) { "Write session is closed" }
            commitBytes(chunks.flattenToByteArray())
            closed = true
        }
        override suspend fun abort() {
            closed = true
            chunks.clear()
        }
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
}

private fun isChild(parent: FilePath, candidate: FilePath, recursive: Boolean): Boolean {
    val prefix = if (parent == FilePath.Root) "" else "${parent.value}/"
    if (!candidate.value.startsWith(prefix)) return false
    return recursive || !candidate.value.removePrefix(prefix).contains('/')
}

private fun remap(from: FilePath, to: FilePath, path: FilePath): FilePath {
    val suffix = path.value.removePrefix(from.value).removePrefix("/")
    return FilePath.of(listOf(to.value, suffix).filter(String::isNotEmpty).joinToString("/"))
}

@JsFun("""
(namespace) => new Promise((resolve, reject) => {
  if (!globalThis.indexedDB) { resolve('[]'); return; }
  const request = indexedDB.open('awake-core-io-v2', 1);
  request.onupgradeneeded = () => {
    const db = request.result;
    if (!db.objectStoreNames.contains('metadata')) db.createObjectStore('metadata');
    if (!db.objectStoreNames.contains('content')) db.createObjectStore('content');
  };
  request.onerror = () => reject(request.error);
  request.onsuccess = () => {
    const db = request.result;
    const cursor = db.transaction('metadata', 'readonly').objectStore('metadata').openCursor();
    const records = [];
    cursor.onerror = () => reject(cursor.error);
    cursor.onsuccess = () => {
      const item = cursor.result;
      if (item) {
        if (item.value.namespace === namespace) records.push(item.value);
        item.continue();
      } else resolve(JSON.stringify(records.map(({path, kind, sizeBytes, modifiedAtEpochMs}) => ({path, kind, sizeBytes, modifiedAtEpochMs}))));
    };
  };
})
""")
private external fun jsIndexedDbList(namespace: String): Promise<JsAny?>

@JsFun("""
(namespace, path) => new Promise((resolve, reject) => {
  if (!globalThis.indexedDB) { resolve(null); return; }
  const request = indexedDB.open('awake-core-io-v2', 1);
  request.onerror = () => reject(request.error);
  request.onsuccess = () => {
    const db = request.result;
    const result = db.transaction('content', 'readonly').objectStore('content').get(namespace + ':' + path);
    result.onerror = () => reject(result.error);
    result.onsuccess = () => resolve(result.result ?? null);
  };
})
""")
private external fun jsIndexedDbRead(namespace: String, path: String): Promise<JsAny?>

@JsFun("""
(namespace, path, kind, sizeBytes, modifiedAtEpochMs, content) => new Promise((resolve, reject) => {
  if (!globalThis.indexedDB) { resolve(null); return; }
  const request = indexedDB.open('awake-core-io-v2', 1);
  request.onerror = () => reject(request.error);
  request.onsuccess = () => {
    const db = request.result;
    const tx = db.transaction(['metadata', 'content'], 'readwrite');
    const key = namespace + ':' + path;
    tx.objectStore('metadata').put({namespace, path, kind, sizeBytes, modifiedAtEpochMs}, key);
    if (content == null) tx.objectStore('content').delete(key); else tx.objectStore('content').put(content, key);
    tx.oncomplete = () => resolve(null);
    tx.onerror = () => reject(tx.error);
  };
})
""")
private external fun jsIndexedDbPut(
    namespace: String,
    path: String,
    kind: String,
    sizeBytes: Long?,
    modifiedAtEpochMs: Long?,
    content: String?,
): Promise<JsAny?>

@JsFun("""
(namespace, path) => new Promise((resolve, reject) => {
  if (!globalThis.indexedDB) { resolve(null); return; }
  const request = indexedDB.open('awake-core-io-v2', 1);
  request.onerror = () => reject(request.error);
  request.onsuccess = () => {
    const db = request.result;
    const tx = db.transaction(['metadata', 'content'], 'readwrite');
    const key = namespace + ':' + path;
    tx.objectStore('metadata').delete(key); tx.objectStore('content').delete(key);
    tx.oncomplete = () => resolve(null);
    tx.onerror = () => reject(tx.error);
  };
})
""")
private external fun jsIndexedDbDelete(namespace: String, path: String): Promise<JsAny?>

@JsFun("""
(namespace, from, to, content) => new Promise((resolve, reject) => {
  if (!globalThis.indexedDB) { resolve(null); return; }
  const request = indexedDB.open('awake-core-io-v2', 1);
  request.onerror = () => reject(request.error);
  request.onsuccess = () => {
    const db = request.result;
    const tx = db.transaction(['metadata', 'content'], 'readwrite');
    const fromKey = namespace + ':' + from, toKey = namespace + ':' + to;
    const metadataStore = tx.objectStore('metadata'), contentStore = tx.objectStore('content');
    const source = metadataStore.get(fromKey);
    source.onerror = () => reject(source.error);
    source.onsuccess = () => {
      const value = source.result;
      metadataStore.delete(fromKey); contentStore.delete(fromKey);
      if (value) { value.path = to; metadataStore.put(value, toKey); if (content != null) contentStore.put(content, toKey); }
    };
    tx.oncomplete = () => resolve(null);
    tx.onerror = () => reject(tx.error);
  };
})
""")
private external fun jsIndexedDbMove(namespace: String, from: String, to: String, content: String?): Promise<JsAny?>
