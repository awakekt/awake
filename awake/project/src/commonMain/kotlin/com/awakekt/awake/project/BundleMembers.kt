/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * How many bytes of bundle members [IndexedAssetSource] keeps in memory, with no [ContentCache], for
 * reads still to come. Its KDoc spells the number out, so change both together.
 */
internal const val STAGED_MEMBER_BUDGET_BYTES: Long = BundleMemoryLimits.DEFAULT_STAGED_MEMBER_BYTES

/**
 * [IndexedAssetSource]'s reads of content bundle members: one whole-bundle fetch however many reads
 * ask at once, each member checked, then kept in the cache or, with none, in memory until read. At
 * most [limits]' `concurrentBundleFetches` bundles are held at once, each body once (#1619).
 */
internal class BundleMembers(
    private val index: ProjectIndex,
    private val fetch: suspend (url: String) -> ByteArray,
    private val cache: ContentCache?,
    private val rangeFetch: (suspend (url: String, offset: Long, length: Long) -> ByteArray)?,
    private val seals: MemberSeals?,
    limits: BundleMemoryLimits = BundleMemoryLimits(),
) : AutoCloseable {
    private val bundles = index.bundles.associateBy { it.sha256 }
    private val membersOf = index.files.filter { it.bundle != null }.groupBy { it.bundle!! }
    private val rangedMemberAboveBytes: Long? = limits.rangedMemberAboveBytes

    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<FetchedBundle>>()
    private val staged = StagedMembers(limits.stagedMemberBytes)

    // A permit covers a bundle from its fetch until its members are kept, so at most this many bodies are held.
    private val fetches = Semaphore(limits.concurrentBundleFetches)

    @Volatile
    private var closed = false

    /** Whether [bundleSha256] should be fetched member by member via [rangeFetch] during `prefetch`. */
    fun prefersRangeFor(bundleSha256: String): Boolean =
        rangeFetch != null && rangedMemberAboveBytes != null && bundles.getValue(bundleSha256).sizeBytes > rangedMemberAboveBytes

    /** [entry]'s bytes, checked: kept from an earlier fetch, by range, or from its whole bundle. */
    suspend fun read(entry: ProjectIndexEntry, label: String): ByteArray {
        check(!closed) { "IndexedAssetSource is closed" }
        val bundle = bundles.getValue(entry.bundle!!)
        val useRange = rangeFetch != null && (rangedMemberAboveBytes == null || bundle.sizeBytes > rangedMemberAboveBytes)
        return takeStaged(entry)
            ?: if (useRange) {
                readRange(entry, label)
            } else {
                load(bundle.sha256).let { fetched ->
                    // This read takes its member; what is staged is for reads still to come.
                    takeStaged(entry) ?: fetched.read(entry)
                }
            }
    }

    /** Reads a single bundle member [entry] via [rangeFetch], verifies/opens it, and caches the stored bytes. */
    suspend fun readRange(entry: ProjectIndexEntry, label: String): ByteArray {
        check(!closed) { "IndexedAssetSource is closed" }
        val bundle = bundles.getValue(entry.bundle!!)
        val stored = checkNotNull(rangeFetch)(bundle.url, entry.offset!!, index.storedSizeOf(entry))
        return opened(bundle, entry, label, stored).also { plain ->
            if (closed) {
                if (plain !== stored) plain.fill(0)
                error("IndexedAssetSource is closed")
            }
            cache?.put(index.cacheKeyOf(entry), stored)
        }
    }

    /** The bundle [sha256], checked, fetched once however many reads ask at the same time. */
    // TooGenericExceptionCaught: whatever fails the fetch fails the reads waiting on it too, then propagates.
    @Suppress("TooGenericExceptionCaught")
    suspend fun load(sha256: String): FetchedBundle {
        check(!closed) { "IndexedAssetSource is closed" }
        while (true) {
            var leader = false
            val pending = lock.withLock {
                inFlight.getOrPut(sha256) { CompletableDeferred<FetchedBundle>().also { leader = true } }
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
                val bundle = bundles.getValue(sha256)
                val fetched = fetches.withPermit {
                    fetchChecked(bundle).also { fetched ->
                        if (!closed) keep(bundle, fetched)
                        if (closed) {
                            fetched.wipe()
                            error("IndexedAssetSource is closed")
                        }
                    }
                }
                pending.complete(fetched)
                return fetched
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                pending.completeExceptionally(failure)
                throw failure
            } finally {
                // Still pending only when this read was cancelled, not the bundle: the reads waiting fetch it afresh.
                if (!pending.isCompleted) pending.completeExceptionally(LeaderCancelled())
                withContext(NonCancellable) { lock.withLock { inFlight.remove(sha256) } }
            }
        }
    }

    /** [stored], [entry]'s bytes as fetched, checked and, in an encrypted index, opened; otherwise fails naming [label]. */
    private suspend fun opened(bundle: ProjectIndexBundle, entry: ProjectIndexEntry, label: String, stored: ByteArray): ByteArray =
        seals?.of(bundle)?.open(entry, stored, label) ?: checked(entry, label, stored)

    /**
     * The bundle, once its size, SHA-256 and table of contents match the index. A sealed bundle's key
     * is asked for first, so a refused key fails as itself, and one member is opened, so a wrong key
     * fails the fetch rather than a later read; the rest are opened as they're read.
     */
    private suspend fun fetchChecked(bundle: ProjectIndexBundle): FetchedBundle {
        val bytes = fetch(bundle.url)
        val members = membersOf.getValue(bundle.sha256)
        val seal = seals?.of(bundle)
        seal?.cipher()
        val whole = bytes.size.toLong() == bundle.sizeBytes && Sha256.digestHex(bytes) == bundle.sha256
        val toc = if (whole) readTocByKey(bytes) else null
        if (toc != null && members.all { matchesTocAndBounds(it, toc[it.key], bytes.size, index.storedSizeOf(it)) }) {
            val fetched = FetchedBundle(bytes, index, seal)
            if (seal != null) fetched.read(members.first()).fill(0)
            return fetched
        }
        val damaged = if (seal != null) unopened(seal, bytes, members) else damagedMembers(bytes, members)
        val what = if (damaged.isEmpty()) "its header or table of contents" else "damaged members: ${damaged.joinToString(", ")}"
        error(
            "${bundle.url} doesn't match the project index (expected ${bundle.sizeBytes} bytes with SHA-256 ${bundle.sha256}): $what",
        )
    }

    /** The keys of [members] whose sealed bytes don't open, for the message of a bundle that fails. */
    private suspend fun unopened(seal: MemberSeal, bytes: ByteArray, members: List<ProjectIndexEntry>): List<String> = members.mapNotNull { member ->
        val stored = sliceOf(bytes, member, index.storedSizeOf(member))
        if (stored != null && openOrNull(seal, member, stored) != null) null else member.key
    }

    private suspend fun openOrNull(seal: MemberSeal, member: ProjectIndexEntry, stored: ByteArray): ByteArray? = try {
        seal.open(member, stored, member.key)
    } catch (_: BundleAuthenticationException) {
        null
    }

    /** Puts [fetched]'s members in the cache as stored, one at a time, or with none, stages them for the reads still to come. */
    private suspend fun keep(bundle: ProjectIndexBundle, fetched: FetchedBundle) {
        val members = membersOf.getValue(bundle.sha256)
        val cache = cache
        if (cache != null) {
            // The cache outlives this index and serves a hit by size alone, so each plaintext member it
            // keeps is hashed here even though the whole bundle's SHA-256 has matched: a key in it always
            // names its bytes. A sealed member is checked by its tag on every read instead.
            for (member in members.distinctBy { it.sha256 }) {
                if (closed) break
                val stored = fetched.stored(member)
                cache.put(index.cacheKeyOf(member), if (seals != null) stored else checked(member, "${member.key} in ${bundle.url}", stored))
            }
            return
        }
        lock.withLock {
            // By key, not SHA-256: two files with the same bytes are two reads to come.
            if (!closed) staged.stage(fetched, members)
        }
    }

    private suspend fun takeStaged(entry: ProjectIndexEntry): ByteArray? =
        if (cache != null) null else lock.withLock { staged.take(entry) }?.bytes()

    override fun close() {
        closed = true
        while (!lock.tryLock()) {
            // Other lock holders only run brief non-suspending map operations.
        }
        try {
            staged.clear()
        } finally {
            lock.unlock()
        }
    }

    /** The read fetching a bundle was cancelled; the reads waiting on it fetch it again. */
    private class LeaderCancelled : Exception("The read fetching this bundle was cancelled")
}

/**
 * One [MemberSeal] per distinct key id in [index], so reading a bundle asks [keys] only for its own
 * key id and leaves untouched bundles' keys unrequested.
 */
internal class MemberSeals(private val index: ProjectIndex, keys: BundleKeyProvider) : AutoCloseable {
    private val byKeyId: Map<String, MemberSeal> = buildSet {
        add(checkNotNull(index.encryption).keyId)
        index.bundles.mapNotNullTo(this) { it.keyId }
    }.associateWith { MemberSeal(it, keys) }

    /** The seal for [bundle]'s effective key id. */
    fun of(bundle: ProjectIndexBundle): MemberSeal = byKeyId.getValue(checkNotNull(index.keyIdOf(bundle)))

    /** The seal for [entry]'s bundle's effective key id. */
    fun of(entry: ProjectIndexEntry): MemberSeal = byKeyId.getValue(checkNotNull(index.keyIdOf(entry)))

    override fun close() {
        byKeyId.values.forEach { it.close() }
    }
}

/**
 * Opens members sealed under [keyId], with the master key [keys] answers, asked once and kept until
 * the source is dropped or closed, or until a bundle that matches the index byte for byte fails to
 * open under it, which only the wrong key does; a failed ask is not kept either, so the next read
 * asks again.
 */
internal class MemberSeal(private val keyId: String, private val keys: BundleKeyProvider) : AutoCloseable {
    private val lock = Mutex()

    @Volatile
    private var cipher: BundleCipher? = null

    @Volatile
    private var closed = false

    /** [stored], [entry]'s sealed bytes, opened; fails naming [label] when they don't authenticate. */
    suspend fun open(entry: ProjectIndexEntry, stored: ByteArray, label: String): ByteArray = cipher().open(stored, entry.sha256, label)

    /** The cipher, asking for the key handle the first time; a failure to get it is the provider's own. */
    suspend fun cipher(): BundleCipher {
        check(!closed) { "IndexedAssetSource is closed" }
        return lock.withLock {
            check(!closed) { "IndexedAssetSource is closed" }
            cipher?.let { return@withLock it }
            val handle = keys.keyHandle(keyId)
            if (closed) {
                handle.close()
                error("IndexedAssetSource is closed")
            }
            val created = BundleCipher(handle)
            cipher = created
            if (closed) {
                cipher = null
                created.close()
                error("IndexedAssetSource is closed")
            }
            created
        }
    }

    /** Drops and closes the key handle, which a matching bundle has just proved wrong, so the next read asks again. */
    suspend fun forget() {
        lock.withLock {
            val current = cipher
            cipher = null
            current?.close()
            (keys as? WrappedBundleKeyProvider)?.forgetStored(keyId)
        }
    }

    override fun close() {
        closed = true
        val current = cipher
        cipher = null
        current?.close()
    }
}

/** [bytes], once they match [entry]'s size and SHA-256; otherwise fails naming [label]. */
internal fun checked(entry: ProjectIndexEntry, label: String, bytes: ByteArray): ByteArray {
    check(bytes.size.toLong() == entry.sizeBytes && Sha256.digestHex(bytes) == entry.sha256) {
        "$label doesn't match the project index: expected ${entry.sizeBytes} bytes with SHA-256 ${entry.sha256}"
    }
    return bytes
}

private fun damagedMembers(bytes: ByteArray, members: List<ProjectIndexEntry>): List<String> {
    val toc = readTocByKey(bytes)
    return members.mapNotNull { member ->
        val slice = sliceOf(bytes, member, member.sizeBytes)
        val tocMatches = toc == null || matchesTocAndBounds(member, toc[member.key], bytes.size, member.sizeBytes)
        if (slice != null && tocMatches && Sha256.digestHex(slice) == member.sha256) null else member.key
    }
}

/** The [storedSize] bytes at [member]'s offset in [bytes], or null when they run past its end. */
private fun sliceOf(bytes: ByteArray, member: ProjectIndexEntry, storedSize: Long): ByteArray? {
    val start = member.offset!!
    val end = start + storedSize
    return if (end <= bytes.size) bytes.copyOfRange(start.toInt(), end.toInt()) else null
}

private fun readTocByKey(bytes: ByteArray): Map<String, ContentBundleTocEntry>? =
    runCatching { ContentBundleFormat.readTableOfContents(bytes).associateBy { it.key } }.getOrNull()

/** Whether [record] is [member]'s: same offset, stored size [storedSize] and plaintext SHA-256, inside the bundle. */
private fun matchesTocAndBounds(member: ProjectIndexEntry, record: ContentBundleTocEntry?, bundleSize: Int, storedSize: Long): Boolean {
    val start = member.offset ?: return false
    return record != null &&
        record.offset == start &&
        record.sizeBytes == storedSize &&
        record.sha256 == member.sha256 &&
        start + storedSize <= bundleSize
}
