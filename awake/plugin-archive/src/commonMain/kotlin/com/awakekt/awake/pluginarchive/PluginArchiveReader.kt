/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

/**
 * Unpacks a `.awakeplugin` zip from its bytes, on every Awake target.
 *
 * The zip structure (central directory, local headers, CRC-32) is read here in common code, so the
 * limits apply identically everywhere: at most [PluginArchive.MAX_ENTRIES] entries and
 * [PluginArchive.MAX_UNPACKED_BYTES] unpacked, checked against the declared sizes before anything
 * is inflated and again while inflating, safe and unique entry paths, stored or deflated entries
 * only, and no encryption, multi-disk or Zip64 archive. Deflate itself is each platform's own:
 * `java.util.zip.Inflater` on desktop and Android, zlib on iOS, and the browser's
 * `DecompressionStream` on wasmJs, which is asynchronous, so [read] suspends.
 *
 * Reading bytes from disk, a download or a project is the host's job; this only decodes them.
 */
public object PluginArchiveReader {
    /**
     * Unpacks [bytes] and checks them with [PluginArchive.fromEntries].
     *
     * @throws PluginArchiveException when the zip is malformed, over a limit, or unsafe.
     */
    public suspend fun read(bytes: ByteArray): PluginArchive =
        ZipDirectory.read(bytes).let { contents ->
            PluginArchive.fromEntries(
                contents.records.map { PluginArchiveEntry(it.name, unpack(bytes, it, contents.directoryOffset)) },
            )
        }

    private suspend fun unpack(archive: ByteArray, record: ZipRecord, dataEnd: Long): ByteArray {
        val stored = record.storedBytes(archive, dataEnd)
        val content = if (record.method == ZipRecord.STORED) {
            stored
        } else {
            catchingNonCancellation { inflateRaw(stored, record.uncompressedSize.toInt()) }.getOrElse {
                throw PluginArchiveException("Entry '${record.name}' does not inflate: ${it.message}", it)
            }
        }
        ensureArchive(crc32(content) == record.crc32) { "Entry '${record.name}' fails its CRC-32 check." }
        return content
    }
}

/**
 * Inflates raw DEFLATE [input] (no zlib or gzip header) to exactly [expectedSize] bytes, failing
 * when the stream is corrupt, ends early, or would produce more.
 */
internal expect suspend fun inflateRaw(input: ByteArray, expectedSize: Int): ByteArray
