/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * How many bytes of bundle members [IndexedAssetSource] keeps in memory, with no [ContentCache], for
 * reads still to come. Its KDoc spells the number out, so change both together.
 */
internal const val STAGED_MEMBER_BUDGET_BYTES: Long = 64L * 1024 * 1024

/**
 * Reads a published project's files as [index] lists them: each through [fetch], checked against the
 * entry's size and SHA-256 before a parser sees it, and kept in [cache].
 *
 * A loose file is fetched at its entry's `url`. A member of a content bundle (an index of
 * [ProjectIndex.BUNDLED_FORMAT_VERSION], which [ContentBundleWriter] writes) is served from its
 * bundle: the first read of any member fetches the whole bundle once, checks the bundle's size and
 * SHA-256 and every member's, and puts each member in [cache] by its own SHA-256, so the bundle's
 * other members then read with no request. Reads of one bundle running at once share one fetch. A
 * bundle that doesn't match fails every read waiting on it, and its message names each damaged
 * member's path (or opaque id). With no [cache], the members not yet read are kept in memory, up to
 * 64 MiB, and each is dropped once read. With [rangeFetch], a [read] fetches just its member's bytes
 * instead of the whole bundle, while [prefetch] still downloads whole bundles. In an index with
 * [ProjectIndex.pathIds], a path is looked up by its opaque id.
 *
 * [fetch] is the host's: it resolves a relative `url` against wherever the index came from, sends any
 * sign-in, retries and resumes, so Core carries no HTTP library. For content that needs a short-lived
 * read token, pass a [TokenAuthorizedFetch]'s `fetch`, which attaches, refreshes and retries the token;
 * the token never reaches [cache], which stays keyed by SHA-256. [prefetch] calls it for several files
 * at once. A file from the network that doesn't match its entry fails the read and names the path. A
 * copy from [cache] is one this source checked before storing it, so only its size is checked again;
 * a copy of the wrong size is fetched again. A loose index (version 2) reads exactly as it always has.
 *
 * Throws [IllegalArgumentException] for an index [ProjectContentValidator] finds problems in.
 *
 * @param index The published project's index, of either version.
 * @param fetch Answers the bytes at a URL, for loose files and whole bundles.
 * @param cache Optional content-addressed cache for verified files and bundle members.
 * @param rangeFetch Optional: answers `length` bytes from `offset` of the file at a URL, such as with
 * an HTTP `Range` request, for reading one bundle member without its whole bundle; see
 * [TokenAuthorizedFetch.fetchRange].
 */
class IndexedAssetSource(
    private val index: ProjectIndex,
    private val fetch: suspend (url: String) -> ByteArray,
    private val cache: ContentCache? = null,
    private val rangeFetch: (suspend (url: String, offset: Long, length: Long) -> ByteArray)? = null,
) : AssetSource {
    private val entries: Map<String, ProjectIndexEntry>
    private val bundles: Map<String, ProjectIndexBundle>
    private val membersOf: Map<String, List<ProjectIndexEntry>>

    private val lock = Mutex()
    private val bundlesInFlight = mutableMapOf<String, CompletableDeferred<Map<String, ByteArray>>>()
    private val staged = LinkedHashMap<String, ByteArray>()
    private var stagedBytes = 0L

    init {
        val issues = ProjectContentValidator.indexIssueDetails(index)
        require(issues.isEmpty()) { "The project index is invalid: ${issues.joinToString("; ") { it.message }}" }
        entries = index.files.associateBy { it.key }
        bundles = index.bundles.associateBy { it.sha256 }
        membersOf = index.files.filter { it.bundle != null }.groupBy { it.bundle!! }
    }

    override suspend fun read(path: AssetPath): Result<ByteArray> = runCatching {
        val entry = entryFor(path.value) ?: throw NoSuchElementException("${path.value} is not in the project index")
        readChecked(entry, path.value.removePrefix("/"))
    }.onFailure { if (it is CancellationException) throw it }

    /** Whether [index] lists [path]: looked up, with nothing fetched. */
    override suspend fun exists(path: AssetPath): Boolean = entryFor(path.value) != null

    /**
     * Reads every one of [paths] ahead of play, so the files are cached before a scene asks for them,
     * fetching and verifying up to six files or bundles at once. Paths whose files share a SHA-256
     * download once, and bundle members download as whole bundles, once per bundle, skipping a bundle
     * whose chosen members are all cached already. As each download finishes, [onProgress] gets the
     * bytes done of the bytes [paths] add up to. Throws before fetching anything if a path isn't in
     * the index. Otherwise throws for the first file or bundle to fail its check -- first to fail, not
     * necessarily first in [paths] -- and cancels the fetches still in flight.
     */
    suspend fun prefetch(paths: List<String>, onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }) {
        val chosen = paths.map { path ->
            val entry = entryFor(path) ?: throw NoSuchElementException("$path is not in the project index")
            Requested(entry, path.removePrefix("/"))
        }
        val total = chosen.sumOf { it.entry.sizeBytes }
        val (bundled, loose) = chosen.partition { it.entry.bundle != null }
        // One download per SHA-256, through the first path that has it, and one per bundle.
        val downloads = loose.groupBy { it.entry.sha256 }.values.map { Download(it, isBundle = false) } +
            bundled.groupBy { it.entry.bundle }.values.map { Download(it, isBundle = true) }
        val queue = Channel<Download>(Channel.UNLIMITED)
        downloads.forEach { queue.trySend(it) }
        queue.close()
        coroutineScope {
            val finished = Channel<Long>(Channel.UNLIMITED)
            repeat(minOf(MAX_CONCURRENT_PREFETCHES, downloads.size)) {
                launch {
                    for (download in queue) {
                        download(download)
                        finished.send(download.files.sumOf { it.entry.sizeBytes })
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

    private suspend fun download(download: Download) {
        val first = download.files.first()
        when {
            !download.isBundle -> readChecked(first.entry, first.label)
            cache == null || download.files.any { cached(it.entry) == null } -> loadBundle(bundles.getValue(first.entry.bundle!!))
        }
    }

    private fun entryFor(path: String): ProjectIndexEntry? {
        val relative = path.removePrefix("/")
        return entries[index.pathIds?.idOf(relative) ?: relative]
    }

    private suspend fun readChecked(entry: ProjectIndexEntry, label: String): ByteArray {
        // Hashing every cached file again on every visit is what the cache exists to avoid.
        cached(entry)?.let { return it }
        if (entry.bundle != null) return readMember(entry, label)
        val bytes = fetch(entry.url)
        check(matches(bytes, entry)) { mismatch(label, entry) }
        cache?.put(entry.sha256, bytes)
        return bytes
    }

    private suspend fun cached(entry: ProjectIndexEntry): ByteArray? =
        cache?.get(entry.sha256)?.takeIf { it.size.toLong() == entry.sizeBytes }

    private suspend fun readMember(entry: ProjectIndexEntry, label: String): ByteArray {
        takeStaged(entry)?.let { return it }
        val bundle = bundles.getValue(entry.bundle!!)
        val range = rangeFetch
        if (range != null) {
            val bytes = range(bundle.url, entry.offset!!, entry.sizeBytes)
            check(matches(bytes, entry)) { mismatch(label, entry) }
            cache?.put(entry.sha256, bytes)
            return bytes
        }
        val members = loadBundle(bundle)
        // This read takes its member; the staged copy is for reads still to come.
        takeStaged(entry)
        return members.getValue(entry.sha256)
    }

    /** [bundle]'s checked members by SHA-256, fetched once however many reads ask at the same time. */
    private suspend fun loadBundle(bundle: ProjectIndexBundle): Map<String, ByteArray> {
        while (true) {
            var leader = false
            val pending = lock.withLock {
                bundlesInFlight.getOrPut(bundle.sha256) { CompletableDeferred<Map<String, ByteArray>>().also { leader = true } }
            }
            if (!leader) {
                try {
                    return pending.await()
                } catch (_: LeaderCancelled) {
                    // The read fetching it was cancelled, not this one: fetch it afresh.
                    continue
                }
            }
            try {
                val members = fetchBundle(bundle)
                keep(members)
                pending.complete(members)
                return members
            } catch (failure: Throwable) {
                pending.completeExceptionally(if (failure is CancellationException) LeaderCancelled() else failure)
                throw failure
            } finally {
                withContext(NonCancellable) { lock.withLock { bundlesInFlight.remove(bundle.sha256) } }
            }
        }
    }

    private suspend fun fetchBundle(bundle: ProjectIndexBundle): Map<String, ByteArray> {
        val bytes = fetch(bundle.url)
        val verified = HashMap<String, ByteArray>()
        val damaged = membersOf.getValue(bundle.sha256).mapNotNull { member ->
            val start = member.offset!!
            val end = start + member.sizeBytes
            val slice = if (end <= bytes.size) bytes.copyOfRange(start.toInt(), end.toInt()) else null
            if (slice != null && Sha256.digestHex(slice) == member.sha256) {
                verified[member.sha256] = slice
                null
            } else {
                member.key
            }
        }
        val whole = bytes.size.toLong() == bundle.sizeBytes && Sha256.digestHex(bytes) == bundle.sha256
        check(whole && damaged.isEmpty()) {
            val what = if (damaged.isEmpty()) "its header or table of contents" else "damaged members: ${damaged.joinToString(", ")}"
            "${bundle.url} doesn't match the project index (expected ${bundle.sizeBytes} bytes with SHA-256 ${bundle.sha256}): $what"
        }
        return verified
    }

    /** Puts [members] in [cache], or with none, keeps them in memory for the reads still to come. */
    private suspend fun keep(members: Map<String, ByteArray>) {
        val cache = cache
        if (cache != null) {
            members.forEach { (sha256, bytes) -> cache.put(sha256, bytes) }
            return
        }
        lock.withLock {
            members.forEach { (sha256, bytes) ->
                if (sha256 !in staged) {
                    staged[sha256] = bytes
                    stagedBytes += bytes.size
                }
            }
            while (stagedBytes > STAGED_MEMBER_BUDGET_BYTES && staged.isNotEmpty()) {
                val oldest = staged.keys.first()
                stagedBytes -= staged.remove(oldest)!!.size
            }
        }
    }

    private suspend fun takeStaged(entry: ProjectIndexEntry): ByteArray? {
        if (cache != null) return null
        return lock.withLock { staged.remove(entry.sha256)?.also { stagedBytes -= it.size } }
    }

    private fun matches(bytes: ByteArray, entry: ProjectIndexEntry): Boolean =
        bytes.size.toLong() == entry.sizeBytes && Sha256.digestHex(bytes) == entry.sha256

    private fun mismatch(label: String, entry: ProjectIndexEntry): String =
        "$label doesn't match the project index: expected ${entry.sizeBytes} bytes with SHA-256 ${entry.sha256}"

    /** One path [prefetch] was asked for: its entry, and the path as asked, for messages. */
    private class Requested(val entry: ProjectIndexEntry, val label: String)

    /** Files [prefetch] gets in one download: one loose content, or one bundle's chosen members. */
    private class Download(val files: List<Requested>, val isBundle: Boolean)

    /** The read fetching a bundle was cancelled; the reads waiting on it fetch it again. */
    private class LeaderCancelled : Exception("The read fetching this bundle was cancelled")

    /** Factory and default path constants for [IndexedAssetSource]. */
    companion object {
        /** Default project-relative path for the published transport index JSON. */
        const val DEFAULT_INDEX_PATH = "project.index.json"

        private val indexJson = Json { ignoreUnknownKeys = true }

        /**
         * Fetches and decodes the [ProjectIndex] at [indexPath] via [fetch], optionally prunes
         * stale entries from [cache] when [retainCachedOnly] is `true`, and returns a ready
         * [IndexedAssetSource]. A loose index and a bundled one open alike.
         *
         * @param indexPath Project-relative path to the transport index JSON.
         * @param fetch Transport loader resolving an [AssetPath] (or entry URL) to its raw bytes.
         * @param cache Optional content-addressed cache for verified files.
         * @param retainCachedOnly Whether to prune cached entries not listed in the loaded index.
         * @param rangeFetch Optional loader of `length` bytes from `offset` of an entry URL, for
         * reading one bundle member without its whole bundle.
         * @return A validated [IndexedAssetSource] backed by the fetched index and [cache].
         */
        suspend fun open(
            indexPath: AssetPath = AssetPath(DEFAULT_INDEX_PATH),
            fetch: suspend (AssetPath) -> ByteArray,
            cache: ContentCache? = null,
            retainCachedOnly: Boolean = true,
            rangeFetch: (suspend (AssetPath, offset: Long, length: Long) -> ByteArray)? = null,
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
                rangeFetch = rangeFetch?.let { range -> { url, offset, length -> range(AssetPath(url), offset, length) } },
            )
        }

        /**
         * Like the other [open], reading the index at [indexPath] and then every file and bundle
         * through [fetch], so the index request carries the read token too.
         *
         * @param indexPath Project-relative path to the transport index JSON, which [fetch]'s
         * transport resolves like any entry URL.
         * @param fetch The token-attaching fetch for the index and every file.
         * @param cache Optional content-addressed cache for verified files; it never sees the token.
         * @param retainCachedOnly Whether to prune cached entries not listed in the loaded index.
         * @param rangeReads Whether a read of one bundle member asks for just its bytes, with
         * [TokenAuthorizedFetch.fetchRange], instead of fetching its whole bundle.
         * @return A validated [IndexedAssetSource] backed by the fetched index and [cache].
         * @throws ContentFetchException when the index can't be read; see [TokenAuthorizedFetch.fetch].
         */
        suspend fun open(
            indexPath: AssetPath = AssetPath(DEFAULT_INDEX_PATH),
            fetch: TokenAuthorizedFetch,
            cache: ContentCache? = null,
            retainCachedOnly: Boolean = true,
            rangeReads: Boolean = false,
        ): IndexedAssetSource {
            val ranges: suspend (AssetPath, Long, Long) -> ByteArray = { path, offset, length -> fetch.fetchRange(path.value, offset, length) }
            return open(
                indexPath = indexPath,
                fetch = { path -> fetch.fetch(path.value) },
                cache = cache,
                retainCachedOnly = retainCachedOnly,
                rangeFetch = ranges.takeIf { rangeReads },
            )
        }
    }
}
