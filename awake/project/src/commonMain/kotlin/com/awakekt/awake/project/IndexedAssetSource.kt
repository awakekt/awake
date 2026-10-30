/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
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
 * in a browser. [platformContentCache] answers the platform's default.
 *
 * Keys are a file's SHA-256, or for an encrypted index, [ProjectIndex.cacheKeyOf] its entry, under
 * which the sealed bytes are kept: 64 lowercase hex digits either way.
 *
 * [get] must return exactly the bytes [put] stored under that key, or null: [IndexedAssetSource]
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
     * this one does not. A file is listed under [ProjectIndex.cacheKeyOf] its entry.
     *
     * @param index The project index whose files should be retained.
     * @return The number of deleted files; 0 when the cache does not prune or storage is unavailable.
     */
    suspend fun retainOnly(index: ProjectIndex): Int = 0
}

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
 * An encrypted index ([ProjectIndex.ENCRYPTED_FORMAT_VERSION]) needs [keys], which is asked once for
 * the index's key id. Each member is authenticated and opened as it is read; a wrong key or a damaged
 * byte fails the read. [cache] then holds the sealed bytes, never the plaintext, under
 * [ProjectIndex.cacheKeyOf], and every hit is opened again; a hit that doesn't open is fetched again.
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
 * @param keys Answers the master key of an encrypted index; required for one, unused otherwise.
 */
class IndexedAssetSource(
    private val index: ProjectIndex,
    private val fetch: suspend (url: String) -> ByteArray,
    private val cache: ContentCache? = null,
    private val rangeFetch: (suspend (url: String, offset: Long, length: Long) -> ByteArray)? = null,
    keys: BundleKeyProvider? = null,
) : PrefetchableAssetSource {
    private val entries: Map<String, ProjectIndexEntry>
    private val seal: MemberSeal?
    private val members: BundleMembers

    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        index: ProjectIndex,
        fetch: suspend (url: String) -> ByteArray,
        cache: ContentCache? = null,
        rangeFetch: (suspend (url: String, offset: Long, length: Long) -> ByteArray)? = null,
    ) : this(index, fetch, cache, rangeFetch, null)

    init {
        val issues = ProjectContentValidator.indexIssueDetails(index)
        require(issues.isEmpty()) { "The project index is invalid: ${issues.joinToString("; ") { it.message }}" }
        require(index.encryption == null || keys != null) {
            "The project index is encrypted with key ${index.encryption?.keyId}; pass a BundleKeyProvider"
        }
        entries = index.files.associateBy { it.key }
        seal = index.encryption?.let { MemberSeal(it, keys!!) }
        members = BundleMembers(index, fetch, cache, rangeFetch, seal)
    }

    /**
     * Whether this source keeps checked files in a [ContentCache]. [prefetch] only pays off then: with
     * none, a loose file it fetched is dropped and fetched again by the read that follows.
     */
    val hasCache: Boolean get() = cache != null

    /** Whether [prefetch] pays off, which it does with a [ContentCache]: the same as [hasCache]. */
    override val canPrefetch: Boolean get() = hasCache

    /** [prefetch], for the [PrefetchableAssetSource] contract, which keeps this class's own method as released. */
    override suspend fun prefetchWithProgress(paths: List<String>, onProgress: (done: Long, total: Long) -> Unit) = prefetch(paths, onProgress)

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
            cache == null || download.files.any { storedInCache(it.entry) == null } -> members.load(first.entry.bundle!!)
        }
    }

    private fun entryFor(path: String): ProjectIndexEntry? {
        val relative = path.removePrefix("/")
        return entries[index.pathIds?.idOf(relative) ?: relative]
    }

    // Hashing every cached file again on every visit is what the cache exists to avoid.
    private suspend fun readChecked(entry: ProjectIndexEntry, label: String): ByteArray =
        cached(entry) ?: if (entry.bundle != null) members.read(entry, label) else readLoose(entry, label)

    private suspend fun readLoose(entry: ProjectIndexEntry, label: String): ByteArray =
        checked(entry, label, fetch(entry.url)).also { cache?.put(entry.sha256, it) }

    /** [entry]'s bytes from [cache]: as stored, of the right size, and, when sealed, opened. */
    private suspend fun cached(entry: ProjectIndexEntry): ByteArray? {
        val stored = storedInCache(entry)
        val seal = seal
        return if (stored == null || seal == null) {
            stored
        } else {
            runCatching { seal.open(entry, stored, entry.key) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        }
    }

    private suspend fun storedInCache(entry: ProjectIndexEntry): ByteArray? =
        cache?.get(index.cacheKeyOf(entry))?.takeIf { it.size.toLong() == index.storedSizeOf(entry) }

    /** One path [prefetch] was asked for: its entry, and the path as asked, for messages. */
    private class Requested(val entry: ProjectIndexEntry, val label: String)

    /** Files [prefetch] gets in one download: one loose content, or one bundle's chosen members. */
    private class Download(val files: List<Requested>, val isBundle: Boolean)

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
         * @param keys Answers the master key of an encrypted index; required for one.
         * @return A validated [IndexedAssetSource] backed by the fetched index and [cache].
         */
        suspend fun open(
            indexPath: AssetPath = AssetPath(DEFAULT_INDEX_PATH),
            fetch: suspend (AssetPath) -> ByteArray,
            cache: ContentCache? = null,
            retainCachedOnly: Boolean = true,
            rangeFetch: (suspend (AssetPath, offset: Long, length: Long) -> ByteArray)? = null,
            keys: BundleKeyProvider? = null,
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
                keys = keys,
            )
        }

        /** The [open] that predates `keys`, kept for binary compatibility. */
        @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
        suspend fun open(
            indexPath: AssetPath = AssetPath(DEFAULT_INDEX_PATH),
            fetch: suspend (AssetPath) -> ByteArray,
            cache: ContentCache? = null,
            retainCachedOnly: Boolean = true,
            rangeFetch: (suspend (AssetPath, offset: Long, length: Long) -> ByteArray)? = null,
        ): IndexedAssetSource = open(indexPath, fetch, cache, retainCachedOnly, rangeFetch, keys = null)

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
         * @param keys Answers the master key of an encrypted index; required for one.
         * @return A validated [IndexedAssetSource] backed by the fetched index and [cache].
         * @throws ContentFetchException when the index can't be read; see [TokenAuthorizedFetch.fetch].
         */
        suspend fun open(
            indexPath: AssetPath = AssetPath(DEFAULT_INDEX_PATH),
            fetch: TokenAuthorizedFetch,
            cache: ContentCache? = null,
            retainCachedOnly: Boolean = true,
            rangeReads: Boolean = false,
            keys: BundleKeyProvider? = null,
        ): IndexedAssetSource {
            val ranges: suspend (AssetPath, Long, Long) -> ByteArray = { path, offset, length -> fetch.fetchRange(path.value, offset, length) }
            return open(
                indexPath = indexPath,
                fetch = { path -> fetch.fetch(path.value) },
                cache = cache,
                retainCachedOnly = retainCachedOnly,
                rangeFetch = ranges.takeIf { rangeReads },
                keys = keys,
            )
        }

        /** The [open] that predates `keys`, kept for binary compatibility. */
        @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
        suspend fun open(
            indexPath: AssetPath = AssetPath(DEFAULT_INDEX_PATH),
            fetch: TokenAuthorizedFetch,
            cache: ContentCache? = null,
            retainCachedOnly: Boolean = true,
            rangeReads: Boolean = false,
        ): IndexedAssetSource = open(indexPath, fetch, cache, retainCachedOnly, rangeReads, keys = null)
    }
}
