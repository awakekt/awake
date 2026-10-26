/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("DEPRECATION")

package com.awakekt.awake.editor.core.plugin

import com.awakekt.awake.core.io.Sha256

/** Moved to `com.awakekt.awake:plugin-archive`; this forward is removed in the next release. */
@Deprecated(
    "Moved to com.awakekt.awake.pluginarchive (com.awakekt.awake:plugin-archive).",
    ReplaceWith("PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME", "com.awakekt.awake.pluginarchive.PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME"),
)
const val PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME: String = com.awakekt.awake.pluginarchive.PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME

/** Moved to `com.awakekt.awake:plugin-archive`; this alias is removed in the next release. */
@Deprecated(
    "Moved to com.awakekt.awake.pluginarchive (com.awakekt.awake:plugin-archive).",
    ReplaceWith("PluginArchiveEntry", "com.awakekt.awake.pluginarchive.PluginArchiveEntry"),
)
typealias PluginArchiveEntry = com.awakekt.awake.pluginarchive.PluginArchiveEntry

/**
 * The blocking digest this module used to own, kept for one release so existing callers compile.
 * `com.awakekt.awake.pluginarchive.PluginArchiveDigest` replaces it; its `canonicalDigest` suspends,
 * because the browser's SHA-256 is asynchronous. Both produce the same bytes.
 */
@Deprecated(
    "Moved to com.awakekt.awake.pluginarchive (com.awakekt.awake:plugin-archive), where canonicalDigest suspends.",
    ReplaceWith("PluginArchiveDigest", "com.awakekt.awake.pluginarchive.PluginArchiveDigest"),
)
object PluginArchiveDigest {
    /** The SHA-256 digest [entries] sign; see `com.awakekt.awake.pluginarchive.PluginArchiveDigest`. */
    fun canonicalDigest(entries: List<com.awakekt.awake.pluginarchive.PluginArchiveEntry>): ByteArray {
        val stream = Sha256.Stream()
        entries
            .filterNot { it.name == com.awakekt.awake.pluginarchive.PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }
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
        com.awakekt.awake.pluginarchive.PluginArchiveDigest.signingPayload(digest)
}

private fun Int.toBigEndianBytes(): ByteArray = byteArrayOf(
    (this ushr 24).toByte(),
    (this ushr 16).toByte(),
    (this ushr 8).toByte(),
    this.toByte(),
)
