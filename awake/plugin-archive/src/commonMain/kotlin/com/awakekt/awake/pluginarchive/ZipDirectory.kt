/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

/** One file entry from a zip's central directory, located but not yet unpacked. */
internal class ZipRecord(
    val name: String,
    val method: Int,
    val crc32: Int,
    val compressedSize: Long,
    val uncompressedSize: Long,
    private val localHeaderOffset: Long,
) {
    /**
     * The entry's bytes as stored (compressed or not), bounds-checked against [archive]; entry data
     * must end at or before [dataEnd], where the central directory starts.
     */
    fun storedBytes(archive: ByteArray, dataEnd: Long): ByteArray {
        val header = localHeaderOffset.toInt()
        ensureArchive(localHeaderOffset + LOCAL_HEADER_SIZE <= dataEnd && archive.u32(header) == LOCAL_HEADER_SIGNATURE) {
            "Entry '$name' has no local header where the directory says."
        }
        val start = localHeaderOffset + LOCAL_HEADER_SIZE + archive.u16(header + 26) + archive.u16(header + 28)
        val end = start + compressedSize
        ensureArchive(end <= dataEnd) { "Entry '$name' runs past the end of the archive's data." }
        return archive.copyOfRange(start.toInt(), end.toInt())
    }

    companion object {
        const val STORED = 0
        const val DEFLATED = 8
        const val LOCAL_HEADER_SIGNATURE = 0x04034b50L
        const val LOCAL_HEADER_SIZE = 30
    }
}

/** A zip's file entries and where its central directory starts (the end of entry data). */
internal class ZipContents(val records: List<ZipRecord>, val directoryOffset: Long)

/** Reads a zip's end-of-central-directory record and central directory; see [PluginArchiveReader]. */
internal object ZipDirectory {
    private const val END_SIGNATURE = 0x06054b50L
    private const val END_SIZE = 22
    private const val CENTRAL_SIGNATURE = 0x02014b50L
    private const val CENTRAL_SIZE = 46
    private const val MAX_COMMENT = 0xFFFF
    private const val FLAG_ENCRYPTED = 0x1
    private const val ZIP64_MARKER = 0xFFFFFFFFL

    private class End(val count: Int, val directoryOffset: Long, val offset: Int)

    private class CentralEntry(val record: ZipRecord?, val next: Int)

    fun read(archive: ByteArray): ZipContents {
        val end = readEnd(archive)
        val records = ArrayList<ZipRecord>(end.count)
        var position = end.directoryOffset.toInt()
        var declaredUnpacked = 0L
        repeat(end.count) {
            val entry = readCentralEntry(archive, position, end.offset)
            position = entry.next
            entry.record?.let { record ->
                declaredUnpacked += record.uncompressedSize
                ensureArchive(declaredUnpacked <= PluginArchive.MAX_UNPACKED_BYTES) {
                    "Archive declares more than ${PluginArchive.MAX_UNPACKED_BYTES} unpacked bytes."
                }
                records += record
            }
        }
        return ZipContents(records, end.directoryOffset)
    }

    private fun readEnd(archive: ByteArray): End {
        val end = findEnd(archive)
        val count = archive.u16(end + 10)
        val directorySize = archive.u32(end + 12)
        val directoryOffset = archive.u32(end + 16)
        ensureArchive(archive.u16(end + 4) == 0 && archive.u16(end + 6) == 0 && archive.u16(end + 8) == count) {
            "Multi-disk archives are not supported."
        }
        ensureArchive(count != 0xFFFF && directoryOffset != ZIP64_MARKER && directorySize != ZIP64_MARKER) {
            "Zip64 archives are not supported."
        }
        ensureArchive(count <= PluginArchive.MAX_ENTRIES) {
            "Archive holds $count entries; at most ${PluginArchive.MAX_ENTRIES} are allowed."
        }
        ensureArchive(directoryOffset + directorySize <= end) { "The central directory runs past its end record." }
        return End(count, directoryOffset, end)
    }

    private fun readCentralEntry(archive: ByteArray, position: Int, limit: Int): CentralEntry {
        ensureArchive(position + CENTRAL_SIZE <= limit && archive.u32(position) == CENTRAL_SIGNATURE) {
            "The central directory is truncated or corrupt."
        }
        val method = archive.u16(position + 10)
        val compressed = archive.u32(position + 20)
        val uncompressed = archive.u32(position + 24)
        val nameLength = archive.u16(position + 28)
        val nameStart = position + CENTRAL_SIZE
        ensureArchive(nameStart + nameLength <= limit) { "The central directory is truncated or corrupt." }
        val name = decodeName(archive, nameStart, nameLength)
        checkEntry(name, archive.u16(position + 8), method, compressed, uncompressed)
        val next = nameStart + nameLength + archive.u16(position + 30) + archive.u16(position + 32)
        val record = if (name.endsWith('/')) {
            null
        } else {
            ZipRecord(name, method, archive.u32(position + 16).toInt(), compressed, uncompressed, archive.u32(position + 42))
        }
        return CentralEntry(record, next)
    }

    private fun decodeName(archive: ByteArray, start: Int, length: Int): String =
        catchingNonCancellation { archive.decodeToString(start, start + length, throwOnInvalidSequence = true) }
            .getOrElse { throw PluginArchiveException("An entry name is not UTF-8.", it) }

    private fun checkEntry(name: String, flags: Int, method: Int, compressed: Long, uncompressed: Long) {
        ensureArchive(flags and FLAG_ENCRYPTED == 0) { "Entry '$name' is encrypted." }
        ensureArchive(method == ZipRecord.STORED || method == ZipRecord.DEFLATED) {
            "Entry '$name' uses compression method $method; only stored and deflated are supported."
        }
        ensureArchive(method != ZipRecord.STORED || compressed == uncompressed) {
            "Stored entry '$name' declares different packed and unpacked sizes."
        }
        if (name.endsWith('/')) {
            ensureArchive(uncompressed == 0L) { "Directory entry '$name' has content." }
            ensureArchive(PluginArchive.isSafeEntryPath(name.dropLast(1))) {
                "Archive entry path '$name' is not a safe relative path."
            }
        }
    }

    /** The offset of the end-of-central-directory record, searching back over a possible comment. */
    private fun findEnd(archive: ByteArray): Int {
        val lowest = maxOf(0, archive.size - END_SIZE - MAX_COMMENT)
        val found = (archive.size - END_SIZE downTo lowest).firstOrNull { offset ->
            archive.u32(offset) == END_SIGNATURE && offset + END_SIZE + archive.u16(offset + 20) == archive.size
        }
        ensureArchive(found != null) { "Not a zip archive: no end-of-central-directory record." }
        return found!!
    }
}

private fun ByteArray.u16(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.u32(offset: Int): Long =
    (u16(offset).toLong()) or (u16(offset + 2).toLong() shl 16)

private val crcTable = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
    c
}

/** The zip CRC-32 (IEEE 802.3) of [bytes]. */
internal fun crc32(bytes: ByteArray): Int {
    var crc = -1
    for (byte in bytes) crc = crcTable[(crc xor byte.toInt()) and 0xFF] xor (crc ushr 8)
    return crc.inv()
}
