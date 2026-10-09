/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.Sha256
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where [IndexedAssetSource] keeps files it has checked, by SHA-256, so a file read twice, or
 * unchanged between two releases of a project, downloads once. A host backs it with what it has: a
 * folder on desktop and mobile, the origin private file system or IndexedDB in a browser.
 */
interface ContentCache {
    /** The bytes stored under [sha256], or null when there are none. */
    suspend fun get(sha256: String): ByteArray?

    /** Stores [bytes] under [sha256], which [IndexedAssetSource] has already checked them against. */
    suspend fun put(sha256: String, bytes: ByteArray)
}

/**
 * Reads a published project's files as [index] lists them: each through [fetch] at its entry's
 * `url`, checked against the entry's size and SHA-256 before a parser sees it, and kept in [cache].
 *
 * [fetch] is the host's: it resolves a relative `url` against wherever the index came from, sends any
 * sign-in, retries and resumes, so Core carries no HTTP library. A file that doesn't match its entry,
 * from the network or from the cache, fails the read and names the path; a cached copy that doesn't
 * match is fetched again.
 *
 * Throws [IllegalArgumentException] for an index [ProjectContentValidator] finds problems in.
 */
class IndexedAssetSource(
    private val index: ProjectIndex,
    private val fetch: suspend (url: String) -> ByteArray,
    private val cache: ContentCache? = null,
) : AssetSource {
    private val entries: Map<String, ProjectIndexEntry>

    init {
        val issues = ProjectContentValidator.indexIssueDetails(index)
        require(issues.isEmpty()) { "The project index is invalid: ${issues.joinToString("; ") { it.message }}" }
        entries = index.files.associateBy { it.path }
    }

    override suspend fun read(path: AssetPath): Result<ByteArray> = runCatching {
        val entry = entries[path.value.removePrefix("/")]
            ?: throw NoSuchElementException("${path.value} is not in the project index")
        readChecked(entry)
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * Reads every one of [paths] ahead of play, so the files are cached before a scene asks for them,
     * reporting the bytes done of the bytes [paths] add up to after each file. Throws for the first
     * file that is missing from the index or fails its check.
     */
    suspend fun prefetch(paths: List<String>, onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }) {
        val chosen = paths.map { path ->
            entries[path.removePrefix("/")] ?: throw NoSuchElementException("$path is not in the project index")
        }
        val total = chosen.sumOf { it.sizeBytes }
        var done = 0L
        for (entry in chosen) {
            readChecked(entry)
            done += entry.sizeBytes
            onProgress(done, total)
        }
    }

    private suspend fun readChecked(entry: ProjectIndexEntry): ByteArray {
        val sha256 = entry.sha256.lowercase()
        cache?.get(sha256)?.takeIf { matches(it, entry) }?.let { return it }
        val bytes = fetch(entry.url)
        check(matches(bytes, entry)) {
            "${entry.path} doesn't match the project index: expected ${entry.sizeBytes} bytes with SHA-256 $sha256"
        }
        cache?.put(sha256, bytes)
        return bytes
    }

    private fun matches(bytes: ByteArray, entry: ProjectIndexEntry): Boolean =
        bytes.size.toLong() == entry.sizeBytes && Sha256.digestHex(bytes) == entry.sha256.lowercase()
}
