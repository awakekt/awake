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
    private val seal: MemberSeal?,
) {
    private val bundles = index.bundles.associateBy { it.sha256 }
    private val membersOf = index.files.filter { it.bundle != null }.groupBy { it.bundle!! }

    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Map<String, ByteArray>>>()
    private val staged = LinkedHashMap<String, ByteArray>()
    private var stagedBytes = 0L

    /** [entry]'s bytes, checked: kept from an earlier fetch, by range, or from its whole bundle. */
    suspend fun read(entry: ProjectIndexEntry, label: String): ByteArray {
        val bundle = bundles.getValue(entry.bundle!!)
        val range = rangeFetch
        return takeStaged(entry)
            ?: range?.let {
                val stored = it(bundle.url, entry.offset!!, index.storedSizeOf(entry))
                opened(entry, label, stored).also { cache?.put(index.cacheKeyOf(entry), stored) }
            }
            ?: load(bundle.sha256).getValue(entry.sha256).also {
                // This read takes its member; the staged copy is for reads still to come.
                takeStaged(entry)
            }
    }

    /** The bundle [sha256]'s checked members by SHA-256, fetched once however many reads ask at the same time. */
    suspend fun load(sha256: String): Map<String, ByteArray> {
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
                val outcome = runCatching {
                    val stored = fetchChecked(bundle)
                    stored.mapValues { it.value.read }.also { keep(bundle, it, stored) }
                }
                outcome.fold(
                    onSuccess = { pending.complete(it) },
                    onFailure = { pending.completeExceptionally(if (it is CancellationException) LeaderCancelled() else it) },
                )
                return outcome.getOrThrow()
            } finally {
                withContext(NonCancellable) { lock.withLock { inFlight.remove(sha256) } }
            }
        }
    }

    /** [stored], [entry]'s bytes as fetched, checked and, in an encrypted index, opened; otherwise fails naming [label]. */
    private suspend fun opened(entry: ProjectIndexEntry, label: String, stored: ByteArray): ByteArray =
        seal?.open(entry, stored, label) ?: checked(entry, label, stored)

    /** The bundle's members by SHA-256, each as stored and as read. */
    private suspend fun fetchChecked(bundle: ProjectIndexBundle): Map<String, Member> {
        val bytes = fetch(bundle.url)
        // The key first, so a refused key fails as itself rather than as damaged members.
        seal?.cipher()
        val verified = HashMap<String, Member>()
        val damaged = membersOf.getValue(bundle.sha256).mapNotNull { member ->
            val start = member.offset!!
            val end = start + index.storedSizeOf(member)
            val slice = if (end <= bytes.size) bytes.copyOfRange(start.toInt(), end.toInt()) else null
            val read = verified[member.sha256]?.read ?: slice?.let { stored ->
                runCatching { opened(member, member.key, stored) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            }
            if (slice != null && read != null) {
                verified[member.sha256] = Member(slice, read)
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

    /** Puts [members] in the cache as stored, or with none, keeps each of [bundle]'s files in memory for the read still to come. */
    private suspend fun keep(bundle: ProjectIndexBundle, members: Map<String, ByteArray>, stored: Map<String, Member>) {
        val cache = cache
        if (cache != null) {
            membersOf.getValue(bundle.sha256).distinctBy { it.sha256 }.forEach { cache.put(index.cacheKeyOf(it), stored.getValue(it.sha256).stored) }
            return
        }
        lock.withLock {
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

    /** One distinct member: its bytes as the bundle stores them, and as a read answers them. */
    private class Member(val stored: ByteArray, val read: ByteArray)

    /** The read fetching a bundle was cancelled; the reads waiting on it fetch it again. */
    private class LeaderCancelled : Exception("The read fetching this bundle was cancelled")
}

/**
 * Opens an encrypted index's members, with the master key its [BundleKeyProvider] answers, asked
 * once and kept for the source's life; a failed ask is not kept, so the next read asks again.
 */
internal class MemberSeal(private val encryption: ProjectBundleEncryption, private val keys: BundleKeyProvider) {
    private val lock = Mutex()
    private var cipher: BundleCipher? = null

    /** [stored], [entry]'s sealed bytes, opened; fails naming [label] when they don't authenticate. */
    suspend fun open(entry: ProjectIndexEntry, stored: ByteArray, label: String): ByteArray = cipher().open(stored, entry.sha256, label)

    /** The cipher, asking for the key the first time; a failure to get it is the provider's own. */
    suspend fun cipher(): BundleCipher =
        lock.withLock { cipher ?: BundleCipher(keys.key(encryption.keyId), encryption.keyId).also { cipher = it } }
}

/** [bytes], once they match [entry]'s size and SHA-256; otherwise fails naming [label]. */
internal fun checked(entry: ProjectIndexEntry, label: String, bytes: ByteArray): ByteArray {
    check(bytes.size.toLong() == entry.sizeBytes && Sha256.digestHex(bytes) == entry.sha256) {
        "$label doesn't match the project index: expected ${entry.sizeBytes} bytes with SHA-256 ${entry.sha256}"
    }
    return bytes
}
