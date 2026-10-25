/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.Sha256

/**
 * The content bundle format: many files in one archive, which is itself stored and fetched by its
 * SHA-256, so a published project loads in one request per bundle rather than one per file.
 *
 * A bundle is a header, a table of contents, then the members' bytes. Every integer is big-endian.
 *
 * * Header, [HEADER_SIZE] bytes: the magic `AWKB`, the format version as 2 bytes ([VERSION]), 2
 *   reserved bytes (zero), the member count as 4 bytes, and the table of contents' size in bytes as
 *   4 bytes.
 * * Table of contents, one record per member, sorted by key: the key's UTF-8 length as 2 bytes, the
 *   key (a project path, or an opaque id), the member's offset from the bundle's first byte as 8
 *   bytes, its size as 8 bytes, and its SHA-256 as 32 raw bytes.
 * * Data: each distinct content once, in table order, with no padding. Members with the same bytes
 *   share one offset.
 *
 * The header and table come first, so a reader with range requests reads the first [HEADER_SIZE]
 * bytes, then the table, without the members. The format has no timestamps and a fixed order, so
 * [encode] writes the same bytes for the same members, which keeps an unchanged bundle's SHA-256, its
 * name, unchanged too. A [ProjectIndex] repeats each member's offset, size and SHA-256, so
 * [IndexedAssetSource] reads a member without parsing the table at all.
 */
object ContentBundleFormat {
    /** The format version this engine writes and reads. */
    const val VERSION: Int = 1

    /** The header's size in bytes: magic, version, reserved, member count and table size. */
    const val HEADER_SIZE: Int = 16

    /** The file extension [ContentBundleWriter] names bundles with. */
    const val FILE_EXTENSION: String = "awkb"

    /** The four bytes every bundle starts with: `AWKB` in ASCII. */
    const val MAGIC: String = "AWKB"

    private const val SHA256_BYTES = 32
    private const val RECORD_FIXED_BYTES = 2 + 8 + 8 + SHA256_BYTES
    private const val MAX_KEY_BYTES = 0xFFFF

    /**
     * Writes [members] as one bundle, sorted by key, each distinct content stored once.
     *
     * @param members The files to store; their keys must be distinct, non-empty, and at most 65535
     * bytes of UTF-8.
     * @return The bundle's bytes, the same for the same members in any order.
     * @throws IllegalArgumentException for a repeated, empty or overlong key.
     */
    fun encode(members: List<ContentBundleMember>): ByteArray {
        val sorted = members.sortedBy { it.key }
        sorted.zipWithNext().firstOrNull { (a, b) -> a.key == b.key }?.let { (a, _) ->
            throw IllegalArgumentException("A bundle can't hold two members with the key ${a.key}")
        }
        val keys = sorted.map { member ->
            member.key.encodeToByteArray().also { require(it.isNotEmpty() && it.size <= MAX_KEY_BYTES) { "A bundle key must be 1 to $MAX_KEY_BYTES bytes: ${member.key}" } }
        }
        val digests = sorted.map { Sha256.digest(it.bytes) }
        val tableSize = keys.sumOf { RECORD_FIXED_BYTES + it.size }
        // Each distinct content once, at the offset of the first member that has it.
        val offsets = LinkedHashMap<String, Long>()
        var end = (HEADER_SIZE + tableSize).toLong()
        val placed = sorted.mapIndexed { position, member ->
            offsets.getOrPut(digests[position].toHex()) { end.also { end += member.bytes.size } }
        }
        require(end <= Int.MAX_VALUE) { "A bundle must be smaller than 2 GiB: $end bytes" }
        val out = ByteWriter(end.toInt())
        out.ascii(MAGIC)
        out.u16(VERSION)
        out.u16(0)
        out.u32(sorted.size.toLong())
        out.u32(tableSize.toLong())
        sorted.forEachIndexed { position, member ->
            out.u16(keys[position].size)
            out.bytes(keys[position])
            out.u64(placed[position])
            out.u64(member.bytes.size.toLong())
            out.bytes(digests[position])
        }
        val written = HashSet<Long>()
        sorted.forEachIndexed { position, member -> if (written.add(placed[position])) out.bytes(member.bytes) }
        return out.finish()
    }

    /**
     * Reads a bundle's header from its first [HEADER_SIZE] bytes, or more.
     *
     * @param bytes The bundle, or at least its first [HEADER_SIZE] bytes.
     * @return The header, whose [ContentBundleHeader.dataOffset] is how many bytes [readTableOfContents] needs.
     * @throws IllegalArgumentException when [bytes] is too short, isn't a bundle, or is a version this engine can't read.
     */
    fun readHeader(bytes: ByteArray): ContentBundleHeader {
        require(bytes.size >= HEADER_SIZE) { "A content bundle header is $HEADER_SIZE bytes; got ${bytes.size}" }
        val reader = ByteReader(bytes)
        require(reader.ascii(MAGIC.length) == MAGIC) { "Not a content bundle: it doesn't start with $MAGIC" }
        val version = reader.u16()
        require(version == VERSION) { "Content bundle format $version isn't one this engine reads (it reads $VERSION)" }
        reader.u16()
        val count = reader.u32()
        val tableSize = reader.u32()
        require(count <= Int.MAX_VALUE && tableSize <= Int.MAX_VALUE - HEADER_SIZE) { "The content bundle header is damaged" }
        return ContentBundleHeader(version, count.toInt(), tableSize.toInt())
    }

    /**
     * Reads a bundle's table of contents from its first [ContentBundleHeader.dataOffset] bytes, or more.
     *
     * @param bytes The bundle, or at least its header and table of contents.
     * @return One entry per member, in table order.
     * @throws IllegalArgumentException when [bytes] is too short or the table is damaged.
     */
    fun readTableOfContents(bytes: ByteArray): List<ContentBundleTocEntry> {
        val header = readHeader(bytes)
        require(bytes.size >= header.dataOffset) {
            "The table of contents ends at byte ${header.dataOffset}; got ${bytes.size} bytes"
        }
        val reader = ByteReader(bytes, HEADER_SIZE, header.dataOffset)
        val entries = List(header.memberCount) {
            val key = reader.utf8(reader.u16())
            ContentBundleTocEntry(key = key, offset = reader.u64(), sizeBytes = reader.u64(), sha256 = reader.raw(SHA256_BYTES).toHex())
        }
        require(reader.atEnd()) { "The table of contents is damaged: it doesn't fill its declared size" }
        return entries
    }
}

/**
 * One file to store in a bundle with [ContentBundleFormat.encode].
 *
 * @property key What the bundle's table of contents names the file by: its project path, or an opaque id.
 * @property bytes The file's contents.
 */
class ContentBundleMember(
    val key: String,
    val bytes: ByteArray,
)

/**
 * A bundle's header, as [ContentBundleFormat.readHeader] reads it.
 *
 * @property formatVersion The bundle format version, [ContentBundleFormat.VERSION].
 * @property memberCount How many records the table of contents holds.
 * @property tableOfContentsSize The table of contents' size in bytes.
 */
data class ContentBundleHeader(
    val formatVersion: Int,
    val memberCount: Int,
    val tableOfContentsSize: Int,
) {
    /** Where the members' bytes start: the header and table of contents' combined size. */
    val dataOffset: Int get() = ContentBundleFormat.HEADER_SIZE + tableOfContentsSize
}

/**
 * One member in a bundle's table of contents.
 *
 * @property key The member's project path, or its opaque id.
 * @property offset Where the member's bytes start, from the bundle's first byte.
 * @property sizeBytes The member's size in bytes.
 * @property sha256 Lowercase hex-encoded SHA-256 digest of the member's bytes.
 */
data class ContentBundleTocEntry(
    val key: String,
    val offset: Long,
    val sizeBytes: Long,
    val sha256: String,
)

private class ByteWriter(size: Int) {
    private val buffer = ByteArray(size)
    private var position = 0

    fun ascii(text: String) = text.forEach { buffer[position++] = it.code.toByte() }

    fun u16(value: Int) = bigEndian(value.toLong(), 2)

    fun u32(value: Long) = bigEndian(value, 4)

    fun u64(value: Long) = bigEndian(value, 8)

    fun bytes(value: ByteArray) {
        value.copyInto(buffer, position)
        position += value.size
    }

    fun finish(): ByteArray {
        check(position == buffer.size) { "Wrote $position of ${buffer.size} bytes" }
        return buffer
    }

    private fun bigEndian(value: Long, width: Int) {
        for (shift in (width - 1) downTo 0) buffer[position++] = (value ushr (shift * Byte.SIZE_BITS)).toByte()
    }
}

private class ByteReader(private val bytes: ByteArray, private var position: Int = 0, private val end: Int = bytes.size) {
    fun ascii(length: Int): String = raw(length).joinToString("") { (it.toInt() and BYTE_MASK).toChar().toString() }

    fun u16(): Int = bigEndian(2).toInt()

    fun u32(): Long = bigEndian(4)

    fun u64(): Long = bigEndian(8).also { require(it >= 0) { "The table of contents is damaged: a negative offset or size" } }

    fun utf8(length: Int): String = raw(length).decodeToString()

    fun raw(length: Int): ByteArray {
        require(length <= end - position) { "The table of contents is damaged: it runs past its declared size" }
        return bytes.copyOfRange(position, position + length).also { position += length }
    }

    fun atEnd(): Boolean = position == end

    private fun bigEndian(width: Int): Long = raw(width).fold(0L) { value, byte -> (value shl Byte.SIZE_BITS) or (byte.toLong() and BYTE_MASK.toLong()) }
}

private const val BYTE_MASK = 0xFF

private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }

private const val HEX_RADIX = 16
