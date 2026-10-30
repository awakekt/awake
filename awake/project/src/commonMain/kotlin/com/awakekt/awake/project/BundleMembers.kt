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
    index: ProjectIndex,
    private val fetch: suspend (url: String) -> ByteArray,
    private val cache: ContentCache?,
    private val rangeFetch: (suspend (url: String, offset: Long, length: Long) -> ByteArray)?,
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
            ?: range?.let { checked(entry, label, it(bundle.url, entry.offset!!, entry.sizeBytes)).also { bytes -> cache?.put(entry.sha256, bytes) } }
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
                val outcome = runCatching { fetchChecked(bundle).also { keep(bundle, it) } }
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

    private suspend fun fetchChecked(bundle: ProjectIndexBundle): Map<String, ByteArray> {
        val bytes = fetch(bundle.url)
        val members = membersOf.getValue(bundle.sha256)
        val whole = bytes.size.toLong() == bundle.sizeBytes && Sha256.digestHex(bytes) == bundle.sha256
        val trusted = if (whole) sliceTrustedMembers(bytes, members) else null
        if (trusted != null) return trusted
        val damaged = damagedMembers(bytes, members)
        val what = if (damaged.isEmpty()) "its header or table of contents" else "damaged members: ${damaged.joinToString(", ")}"
        error(
            "${bundle.url} doesn't match the project index (expected ${bundle.sizeBytes} bytes with SHA-256 ${bundle.sha256}): $what",
        )
    }

    /** Puts [members] in the cache, or with none, keeps each of [bundle]'s files in memory for the read still to come. */
    private suspend fun keep(bundle: ProjectIndexBundle, members: Map<String, ByteArray>) {
        val cache = cache
        if (cache != null) {
            members.forEach { (sha256, bytes) -> cache.put(sha256, bytes) }
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

    /** The read fetching a bundle was cancelled; the reads waiting on it fetch it again. */
    private class LeaderCancelled : Exception("The read fetching this bundle was cancelled")
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
 * member must still match its record in the bundle's table of contents.
 */
private fun sliceTrustedMembers(bytes: ByteArray, members: List<ProjectIndexEntry>): Map<String, ByteArray>? {
    val toc = readTocByKey(bytes)
    if (toc == null || members.any { !matchesTocAndBounds(it, toc[it.key], bytes.size) }) return null
    val verified = HashMap<String, ByteArray>(members.size)
    for (member in members) {
        if (member.sha256 !in verified) {
            val start = member.offset!!.toInt()
            verified[member.sha256] = bytes.copyOfRange(start, (start + member.sizeBytes).toInt())
        }
    }
    return verified
}

private fun damagedMembers(bytes: ByteArray, members: List<ProjectIndexEntry>): List<String> {
    val toc = readTocByKey(bytes)
    return members.mapNotNull { member ->
        val start = member.offset!!
        val end = start + member.sizeBytes
        val slice = if (end <= bytes.size) bytes.copyOfRange(start.toInt(), end.toInt()) else null
        val tocMatches = toc == null || matchesTocAndBounds(member, toc[member.key], bytes.size)
        if (slice != null && tocMatches && Sha256.digestHex(slice) == member.sha256) null else member.key
    }
}

private fun readTocByKey(bytes: ByteArray): Map<String, ContentBundleTocEntry>? =
    runCatching { ContentBundleFormat.readTableOfContents(bytes).associateBy { it.key } }.getOrNull()

private fun matchesTocAndBounds(member: ProjectIndexEntry, record: ContentBundleTocEntry?, bundleSize: Int): Boolean {
    val start = member.offset ?: return false
    val end = start + member.sizeBytes
    return record != null &&
        record.offset == start &&
        record.sizeBytes == member.sizeBytes &&
        record.sha256 == member.sha256 &&
        end <= bundleSize
}
