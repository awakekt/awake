/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

/** The archive entry name a signing task writes; excluded from the digest it covers. */
public const val PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME: String = "signature.sig"

/** The archive entry holding the plugin's [PluginManifest] as JSON. */
public const val PLUGIN_ARCHIVE_MANIFEST_ENTRY_NAME: String = "plugin.json"

/**
 * One entry of a `.awakeplugin` zip archive.
 *
 * @property name The entry's path within the archive (e.g. "plugin.json" or "lib/plugin.jar").
 * @property content The entry's raw, uncompressed bytes.
 */
public data class PluginArchiveEntry(val name: String, val content: ByteArray) {
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
 * hashed, so "ab" + "c" cannot collide with "a" + "bc". [PluginArchiveSigner] signs this digest on
 * the JVM (`templates/plugin`'s packaging task runs it); `PluginArchiveVerifierTest`'s fixture and
 * pinned digest keep the bytes from silently drifting.
 *
 * [canonicalDigest] suspends because the browser's SHA-256 (WebCrypto) is asynchronous; see
 * [PluginArchiveVerifier] for how callers on each platform reach it.
 */
public object PluginArchiveDigest {
    /** The SHA-256 digest [entries] sign and [PluginArchiveVerifier] checks; see the class doc. */
    public suspend fun canonicalDigest(entries: List<PluginArchiveEntry>): ByteArray =
        sha256(canonicalBytes(entries))

    /** The bytes an RSA/SHA-256 signature covers for an archive whose canonical digest is [digest]. */
    public fun signingPayload(digest: ByteArray): ByteArray =
        (AwakeRootSignature.Domain.PluginArchive.tag + "\n" + digest.toHex()).encodeToByteArray()

    /** The framed byte stream [canonicalDigest] hashes. */
    internal fun canonicalBytes(entries: List<PluginArchiveEntry>): ByteArray {
        val signed = entries
            .filterNot { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }
            .sortedBy { it.name }
            .map { it.name.encodeToByteArray() to it.content }
        val out = ByteArray(signed.sumOf { (name, content) -> 8 + name.size + content.size })
        var offset = 0
        signed.forEach { (name, content) ->
            offset = out.putFramed(offset, name)
            offset = out.putFramed(offset, content)
        }
        return out
    }
}

private fun ByteArray.putFramed(offset: Int, bytes: ByteArray): Int {
    val size = bytes.size
    this[offset] = (size ushr 24).toByte()
    this[offset + 1] = (size ushr 16).toByte()
    this[offset + 2] = (size ushr 8).toByte()
    this[offset + 3] = size.toByte()
    bytes.copyInto(this, offset + 4)
    return offset + 4 + size
}

/** Lowercase hex, two digits per byte. */
internal fun ByteArray.toHex(): String = joinToString("") {
    val hex = (it.toInt() and 0xFF).toString(16)
    if (hex.length == 1) "0$hex" else hex
}
