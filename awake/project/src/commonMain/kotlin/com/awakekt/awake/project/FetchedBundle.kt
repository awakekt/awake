/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

/**
 * A bundle whose size, SHA-256 and table of contents matched the index: its body held once, and each
 * member copied out only when it is kept or read (#1619). Slicing every member up front held the
 * body twice, three times for a sealed bundle, which also held every member opened.
 */
internal class FetchedBundle(
    private val body: ByteArray,
    private val index: ProjectIndex,
    val seal: MemberSeal?,
) {
    /** The body's size: what holding this bundle costs. */
    val sizeBytes: Int get() = body.size

    /** [member]'s bytes as the bundle stores them, copied out. The table of contents check covered its bounds. */
    fun stored(member: ProjectIndexEntry): ByteArray {
        val start = member.offset!!.toInt()
        return body.copyOfRange(start, start + index.storedSizeOf(member).toInt())
    }

    /** [member]'s bytes as a read answers them: opened when sealed. */
    suspend fun read(member: ProjectIndexEntry): ByteArray = openMember(seal, member, stored(member))

    /** Zeroes the body, once nothing more is read from it. */
    fun wipe() = body.fill(0)
}

/**
 * [stored], [member]'s bytes as its bundle stores them, as a read answers them: opened when sealed. A
 * member of a matching bundle that doesn't open can only be under the wrong key, so the key is
 * dropped for the next read to ask again.
 */
internal suspend fun openMember(seal: MemberSeal?, member: ProjectIndexEntry, stored: ByteArray): ByteArray {
    if (seal == null) return stored
    return try {
        seal.open(member, stored, member.key)
    } catch (wrongKey: BundleAuthenticationException) {
        seal.forget()
        throw wrongKey
    }
}

/**
 * Members kept in memory, with no [ContentCache], for the reads still to come, under [budgetBytes].
 * A bundle that fits is kept whole and its members copied out as they're read; one that doesn't has
 * its members copied out, as stored, until the budget is spent, as every bundle once was. The oldest
 * go first. Nothing here suspends: [BundleMembers] calls it under its lock, and a sealed member is
 * opened by [Taken.bytes] after.
 */
internal class StagedMembers(private val budgetBytes: Long) {
    private class Staged(val bundle: FetchedBundle?, val stored: ByteArray?, val seal: MemberSeal?, val sizeBytes: Long)

    /** A member taken out of the store, read by [bytes] outside the lock. */
    class Taken internal constructor(
        private val bundle: FetchedBundle?,
        private val stored: ByteArray?,
        private val seal: MemberSeal?,
        private val entry: ProjectIndexEntry,
    ) {
        /** The member's bytes as a read answers them. */
        suspend fun bytes(): ByteArray = if (stored != null) openMember(seal, entry, stored) else bundle!!.read(entry)
    }

    private val byKey = LinkedHashMap<String, Staged>()
    private val keysOf = HashMap<FetchedBundle, Int>()

    /** The bytes held: each whole bundle once, and each copied member. */
    var heldBytes = 0L
        private set

    /** Keeps [members] of [bundle] that aren't kept already, as views of it when it fits. */
    fun stage(bundle: FetchedBundle, members: List<ProjectIndexEntry>) {
        val fresh = members.filter { it.key !in byKey }
        if (fresh.isEmpty()) return
        val size = bundle.sizeBytes.toLong()
        if (size <= budgetBytes) {
            evictUntil(budgetBytes - size)
            fresh.forEach { byKey[it.key] = Staged(bundle, null, null, it.sizeBytes) }
            keysOf[bundle] = fresh.size
            heldBytes += size
            return
        }
        for (member in fresh) {
            val stored = bundle.stored(member)
            if (stored.size > budgetBytes) continue
            evictUntil(budgetBytes - stored.size)
            byKey[member.key] = Staged(null, stored, bundle.seal, stored.size.toLong())
            heldBytes += stored.size
        }
    }

    /** [entry]'s kept member, taken out; null when none is kept. */
    fun take(entry: ProjectIndexEntry): Taken? {
        val staged = remove(entry.key) ?: return null
        return Taken(staged.bundle, staged.stored, staged.seal, entry)
    }

    /** Zeroes and drops everything kept. */
    fun clear() {
        byKey.values.forEach { it.stored?.fill(0) }
        keysOf.keys.forEach { it.wipe() }
        byKey.clear()
        keysOf.clear()
        heldBytes = 0L
    }

    private fun evictUntil(limit: Long) {
        while (heldBytes > limit && byKey.isNotEmpty()) remove(byKey.keys.first())
    }

    private fun remove(key: String): Staged? {
        val staged = byKey.remove(key) ?: return null
        val bundle = staged.bundle
        if (bundle == null) {
            heldBytes -= staged.sizeBytes
        } else {
            val left = keysOf.getValue(bundle) - 1
            if (left == 0) {
                keysOf.remove(bundle)
                heldBytes -= bundle.sizeBytes
            } else {
                keysOf[bundle] = left
            }
        }
        return staged
    }
}
