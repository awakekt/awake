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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * How many bytes of bundle members [IndexedAssetSource] keeps in memory, with no [ContentCache], for
 * reads still to come. Its KDoc spells the number out, so change both together.
 */
internal const val STAGED_MEMBER_BUDGET_BYTES: Long = 64L * 1024 * 1024

/**
 * [IndexedAssetSource]'s reads of content bundle members: one whole-bundle fetch however many reads
 * ask at once, each member checked, then kept in the cache or, with none, in memory until read.
 */
internal class BundleMembers(
    private val index: ProjectIndex,
    private val fetch: suspend (url: String) -> ByteArray,
    private val cache: ContentCache?,
    private val rangeFetch: (suspend (url: String, offset: Long, length: Long) -> ByteArray)?,
    private val seals: MemberSeals?,
) : AutoCloseable {
    private val bundles = index.bundles.associateBy { it.sha256 }
    private val membersOf = index.files.filter { it.bundle != null }.groupBy { it.bundle!! }

    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Map<String, ByteArray>>>()
    private val staged = LinkedHashMap<String, ByteArray>()
    private var stagedBytes = 0L

    @Volatile
    private var closed = false

    /** [entry]'s bytes, checked: kept from an earlier fetch, by range, or from its whole bundle. */
    suspend fun read(entry: ProjectIndexEntry, label: String): ByteArray {
        check(!closed) { "IndexedAssetSource is closed" }
        val bundle = bundles.getValue(entry.bundle!!)
        val range = rangeFetch
        return takeStaged(entry)
            ?: range?.let {
                val stored = it(bundle.url, entry.offset!!, index.storedSizeOf(entry))
                opened(bundle, entry, label, stored).also { plain ->
                    if (closed) {
                        if (plain !== stored) plain.fill(0)
                        error("IndexedAssetSource is closed")
                    }
                    cache?.put(index.cacheKeyOf(entry), stored)
                }
            }
            ?: load(bundle.sha256).getValue(entry.sha256).also {
                // This read takes its member; the staged copy is for reads still to come.
                takeStaged(entry)
            }
    }

    /** The bundle [sha256]'s checked members by SHA-256, fetched once however many reads ask at the same time. */
    // TooGenericExceptionCaught: whatever fails the fetch fails the reads waiting on it too, then propagates.
    @Suppress("TooGenericExceptionCaught")
    suspend fun load(sha256: String): Map<String, ByteArray> {
        check(!closed) { "IndexedAssetSource is closed" }
        while (true) {
            var leader = false
            val pending = lock.withLock {
                inFlight.getOrPut(sha256) { CompletableDeferred<Map<String, ByteArray>>().also { leader = true } }
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
                val stored = fetchChecked(bundle)
                if (closed) {
                    stored.values.forEach { it.read.fill(0) }
                    error("IndexedAssetSource is closed")
                }
                val members = stored.mapValues { it.value.read }
                keep(bundle, members, stored)
                if (closed) {
                    stored.values.forEach { it.read.fill(0) }
                    error("IndexedAssetSource is closed")
                }
                pending.complete(members)
                return members
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

    /** The bundle's members by SHA-256, each as stored and as read. */
    private suspend fun fetchChecked(bundle: ProjectIndexBundle): Map<String, Member> {
        val bytes = fetch(bundle.url)
        val members = membersOf.getValue(bundle.sha256)
        val whole = bytes.size.toLong() == bundle.sizeBytes && Sha256.digestHex(bytes) == bundle.sha256
        val seal = seals?.of(bundle)
        val trusted = when {
            seal != null -> openSealed(seal, bytes, members, whole)
            whole -> sliceTrustedMembers(bytes, members)?.mapValues { (_, slice) -> Member(slice, slice) }
            else -> null
        }
        if (trusted != null) return trusted
        val damaged = if (seal != null) unopened(seal, bytes, members) else damagedMembers(bytes, members)
        val what = if (damaged.isEmpty()) "its header or table of contents" else "damaged members: ${damaged.joinToString(", ")}"
        error(
            "${bundle.url} doesn't match the project index (expected ${bundle.sizeBytes} bytes with SHA-256 ${bundle.sha256}): $what",
        )
    }

    /**
     * A sealed bundle's members, opened, when the whole bundle and every member's table of contents
     * record match the index; otherwise null. Opening checks each member's tag, so the plaintext needs
     * no hashing. A member of a matching bundle that doesn't open can only be under the wrong key: that
     * fails as an authentication failure, and the key is dropped so the next read asks for it again.
     */
    private suspend fun openSealed(seal: MemberSeal, bytes: ByteArray, members: List<ProjectIndexEntry>, whole: Boolean): Map<String, Member>? {
        // The key first, so a refused key fails as itself rather than as damaged members.
        seal.cipher()
        val toc = readTocByKey(bytes)
        val matches = whole && toc != null && members.all { matchesTocAndBounds(it, toc[it.key], bytes.size, index.storedSizeOf(it)) }
        if (!matches) return null
        return members.distinctBy { it.sha256 }.associate { member ->
            // In bounds: the table of contents check covered it.
            val stored = sliceOf(bytes, member, index.storedSizeOf(member))!!
            val read = try {
                seal.open(member, stored, member.key)
            } catch (wrongKey: BundleAuthenticationException) {
                seal.forget()
                throw wrongKey
            }
            member.sha256 to Member(stored, read)
        }
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

    /** Puts [members] in the cache as stored, or with none, keeps each of [bundle]'s files in memory for the read still to come. */
    private suspend fun keep(bundle: ProjectIndexBundle, members: Map<String, ByteArray>, stored: Map<String, Member>) {
        val cache = cache
        if (cache != null) {
            // The cache outlives this index and serves a hit by size alone, so each plaintext member it
            // keeps is hashed here even though the whole bundle's SHA-256 has matched: a key in it always
            // names its bytes. A sealed member is checked by its tag on every read instead.
            for (member in membersOf.getValue(bundle.sha256).distinctBy { it.sha256 }) {
                if (closed) break
                val bytes = stored.getValue(member.sha256).stored
                cache.put(index.cacheKeyOf(member), if (seals != null) bytes else checked(member, "${member.key} in ${bundle.url}", bytes))
            }
            return
        }
        lock.withLock {
            if (closed) return
            // By key, not SHA-256: two files with the same bytes are two reads to come.
            membersOf.getValue(bundle.sha256).forEach { member ->
                if (member.key !in staged) {
                    val bytes = members.getValue(member.sha256)
                    staged[member.key] = bytes
                    stagedBytes += bytes.size
                }
            }
            while (stagedBytes > STAGED_MEMBER_BUDGET_BYTES && staged.isNotEmpty()) {
                val oldest = staged.keys.first()
                stagedBytes -= staged.remove(oldest)!!.size
            }
        }
    }

    private suspend fun takeStaged(entry: ProjectIndexEntry): ByteArray? =
        if (cache != null) null else lock.withLock { staged.remove(entry.key)?.also { stagedBytes -= it.size } }

    override fun close() {
        closed = true
        while (!lock.tryLock()) {
            // Other lock holders only run brief non-suspending map operations.
        }
        try {
            staged.values.forEach { it.fill(0) }
            staged.clear()
            stagedBytes = 0L
        } finally {
            lock.unlock()
        }
    }

    /** One distinct member: its bytes as the bundle stores them, and as a read answers them. */
    private class Member(val stored: ByteArray, val read: ByteArray)

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

/**
 * Slices [members] without rehashing their bytes once the whole bundle's SHA-256 has matched: each
 * member must still match its record in the bundle's table of contents. Members going into a
 * [ContentCache] are hashed one by one when they're kept.
 */
private fun sliceTrustedMembers(bytes: ByteArray, members: List<ProjectIndexEntry>): Map<String, ByteArray>? {
    val toc = readTocByKey(bytes)
    if (toc == null || members.any { !matchesTocAndBounds(it, toc[it.key], bytes.size, it.sizeBytes) }) return null
    val verified = HashMap<String, ByteArray>(members.size)
    for (member in members) {
        if (member.sha256 !in verified) verified[member.sha256] = sliceOf(bytes, member, member.sizeBytes)!!
    }
    return verified
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
