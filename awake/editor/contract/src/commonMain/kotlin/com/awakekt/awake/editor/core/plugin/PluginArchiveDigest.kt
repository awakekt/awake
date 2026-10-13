/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import com.awakekt.awake.core.io.Sha256

/** The archive entry name a signing task writes; excluded from the digest it covers. */
const val PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME = "signature.sig"

/**
 * One entry of a `.awakeplugin` zip archive.
 *
 * @property name The entry's path within the archive (e.g. "plugin.json" or "lib/plugin.jar").
 * @property content The entry's raw, uncompressed bytes.
 */
data class PluginArchiveEntry(val name: String, val content: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PluginArchiveEntry && name == other.name && content.contentEquals(other.content))

    override fun hashCode(): Int = 31 * name.hashCode() + content.contentHashCode()
}

/**
 * Computes the deterministic digest a publisher signs and [PluginArchiveVerifier] checks.
 *
 * Entries are sorted by name and [PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME] is excluded, so the digest
 * does not depend on zip entry order and is unaffected by the signature it is about to cover. Each
 * entry's name and content are framed with their own 4-byte big-endian length before the bytes are
 * hashed, so "ab" + "c" cannot collide with "a" + "bc". `templates/plugin`'s packaging task mirrors
 * this algorithm in plain JVM code (it builds the archive before this module is on its classpath);
 * `PluginArchiveVerifierTest`'s fixture pins the exact bytes so the two cannot silently drift apart.
 */
object PluginArchiveDigest {
    /** The SHA-256 digest [entries] sign and [PluginArchiveVerifier] checks; see the class doc. */
    fun canonicalDigest(entries: List<PluginArchiveEntry>): ByteArray {
        val stream = Sha256.Stream()
        entries
            .filterNot { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }
            .sortedBy { it.name }
            .forEach { entry ->
                val nameBytes = entry.name.encodeToByteArray()
                stream.update(nameBytes.size.toBigEndianBytes())
                stream.update(nameBytes)
                stream.update(entry.content.size.toBigEndianBytes())
                stream.update(entry.content)
            }
        return stream.finish()
    }

    /** The bytes an RSA/SHA-256 signature covers for an archive whose canonical digest is [digest]. */
    fun signingPayload(digest: ByteArray): ByteArray =
        ("awake-plugin-archive-v1\n" + digest.toHex()).encodeToByteArray()
}

private fun Int.toBigEndianBytes(): ByteArray = byteArrayOf(
    (this ushr 24).toByte(),
    (this ushr 16).toByte(),
    (this ushr 8).toByte(),
    this.toByte(),
)

private fun ByteArray.toHex(): String = joinToString("") {
    val hex = (it.toInt() and 0xFF).toString(16)
    if (hex.length == 1) "0$hex" else hex
}
