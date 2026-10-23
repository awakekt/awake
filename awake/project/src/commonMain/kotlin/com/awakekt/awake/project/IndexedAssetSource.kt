/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * How many of [IndexedAssetSource.prefetch]'s files are fetched and verified at once. Its KDoc
 * spells the number out, so change both together.
 */
internal const val MAX_CONCURRENT_PREFETCHES = 6

/**
 * Where [IndexedAssetSource] keeps files it has checked, by SHA-256, so a file read twice, or
 * unchanged between two releases of a project, downloads once and isn't hashed again. A host backs
 * it with what it has: a folder on desktop and mobile, the origin private file system or IndexedDB
 * in a browser.
 *
 * [get] must return exactly the bytes [put] stored under that SHA-256, or null: [IndexedAssetSource]
 * checks a hit's size but not its hash, so storage that can damage bytes in place has to check them
 * itself. [IndexedAssetSource.prefetch] calls both for several files at once.
 */
interface ContentCache {
    /** The bytes stored under [sha256], or null when there are none. */
    suspend fun get(sha256: String): ByteArray?

    /** Stores [bytes] under [sha256], which [IndexedAssetSource] has already checked them against. */
    suspend fun put(sha256: String, bytes: ByteArray)

    /**
     * Deletes cached files that [index] does not list, such as files an earlier release used and
     * this one does not.
     *
     * @param index The project index whose files should be retained.
     * @return The number of deleted files; 0 when the cache does not prune or storage is unavailable.
     */
    suspend fun retainOnly(index: ProjectIndex): Int = 0
}

/**
 * Reads a published project's files as [index] lists them: each through [fetch] at its entry's
 * `url`, checked against the entry's size and SHA-256 before a parser sees it, and kept in [cache].
 *
 * [fetch] is the host's: it resolves a relative `url` against wherever the index came from, sends any
 * sign-in, retries and resumes, so Core carries no HTTP library. [prefetch] calls it for several files
 * at once. A file from the network that doesn't match its entry fails the read and names the path. A
 * copy from [cache] is one this source checked before storing it, so only its size is checked again;
 * a copy of the wrong size is fetched again.
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

    /** Whether [index] lists [path]: looked up, with nothing fetched. */
    override suspend fun exists(path: AssetPath): Boolean = path.value.removePrefix("/") in entries

    /**
     * Reads every one of [paths] ahead of play, so the files are cached before a scene asks for them,
     * fetching and verifying up to six at once. Paths whose files share a SHA-256 download once. As
     * each download finishes, [onProgress] gets the bytes done of the bytes [paths] add up to. Throws
     * before fetching anything if a path isn't in the index. Otherwise throws for the first file to
     * fail its check -- first to fail, not necessarily first in [paths] -- and cancels the fetches
     * still in flight.
     */
    suspend fun prefetch(paths: List<String>, onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }) {
        val chosen = paths.map { path ->
            entries[path.removePrefix("/")] ?: throw NoSuchElementException("$path is not in the project index")
        }
        val total = chosen.sumOf { it.sizeBytes }
        // One download per SHA-256, through the first path that has it.
        val downloads = chosen.groupBy { it.sha256 }.values
        val queue = Channel<List<ProjectIndexEntry>>(Channel.UNLIMITED)
        downloads.forEach { queue.trySend(it) }
        queue.close()
        coroutineScope {
            val finished = Channel<Long>(Channel.UNLIMITED)
            repeat(minOf(MAX_CONCURRENT_PREFETCHES, downloads.size)) {
                launch {
                    for (sameContent in queue) {
                        readChecked(sameContent.first())
                        finished.send(sameContent.sumOf { it.sizeBytes })
                    }
                }
            }
            // Reported on the caller's coroutine, so onProgress never runs on two threads at once.
            var done = 0L
            repeat(downloads.size) {
                done += finished.receive()
                onProgress(done, total)
            }
        }
    }

    private suspend fun readChecked(entry: ProjectIndexEntry): ByteArray {
        val sha256 = entry.sha256.lowercase()
        // Hashing every cached file again on every visit is what the cache exists to avoid.
        cache?.get(sha256)?.takeIf { it.size.toLong() == entry.sizeBytes }?.let { return it }
        val bytes = fetch(entry.url)
        check(matches(bytes, entry)) {
            "${entry.path} doesn't match the project index: expected ${entry.sizeBytes} bytes with SHA-256 $sha256"
        }
        cache?.put(sha256, bytes)
        return bytes
    }

    private fun matches(bytes: ByteArray, entry: ProjectIndexEntry): Boolean =
        bytes.size.toLong() == entry.sizeBytes && Sha256.digestHex(bytes) == entry.sha256.lowercase()

    /** Factory and default path constants for [IndexedAssetSource]. */
    companion object {
        /** Default project-relative path for the published transport index JSON. */
        const val DEFAULT_INDEX_PATH = "project.index.json"

        private val indexJson = Json { ignoreUnknownKeys = true }

        /**
         * Fetches and decodes the [ProjectIndex] at [indexPath] via [fetch], optionally prunes
         * stale entries from [cache] when [retainCachedOnly] is `true`, and returns a ready
         * [IndexedAssetSource].
         *
         * @param indexPath Project-relative path to the transport index JSON.
         * @param fetch Transport loader resolving an [AssetPath] (or entry URL) to its raw bytes.
         * @param cache Optional content-addressed cache for verified files.
         * @param retainCachedOnly Whether to prune cached entries not listed in the loaded index.
         * @return A validated [IndexedAssetSource] backed by the fetched index and [cache].
         */
        suspend fun open(
            indexPath: AssetPath = AssetPath(DEFAULT_INDEX_PATH),
            fetch: suspend (AssetPath) -> ByteArray,
            cache: ContentCache? = null,
            retainCachedOnly: Boolean = true,
        ): IndexedAssetSource {
            val rawIndex = fetch(indexPath).decodeToString()
            val decodedIndex = indexJson.decodeFromString(ProjectIndex.serializer(), rawIndex)
            if (retainCachedOnly) {
                cache?.retainOnly(decodedIndex)
            }
            return IndexedAssetSource(
                index = decodedIndex,
                fetch = { url -> fetch(AssetPath(url)) },
                cache = cache,
            )
        }
    }
}
