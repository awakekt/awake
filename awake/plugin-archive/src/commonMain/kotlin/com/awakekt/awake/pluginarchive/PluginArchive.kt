/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

/**
 * A `.awakeplugin` archive that passed every structural check: a zip holding [PLUGIN_ARCHIVE_MANIFEST_ENTRY_NAME]
 * (the [manifest]), the plugin's [payload] (classes under `lib/`, resources), and, when the
 * publisher signed it, [PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME].
 *
 * Holding one says nothing about trust: check it with [PluginTrust] (publisher keys and revocations)
 * or [PluginArchiveVerifier] (one key) before loading any of its code.
 *
 * @property manifest The parsed `plugin.json`.
 * @property entries Every file entry, in archive order; directory entries are not kept.
 */
public class PluginArchive private constructor(
    public val manifest: PluginManifest,
    public val entries: List<PluginArchiveEntry>,
) {
    /** The publisher's signature bytes, or null when the archive was packaged unsigned. */
    public val signature: ByteArray?
        get() = entries.firstOrNull { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }?.content

    /** Every entry other than the manifest and the signature. */
    public val payload: List<PluginArchiveEntry>
        get() = entries.filter { it.name != PLUGIN_ARCHIVE_MANIFEST_ENTRY_NAME && it.name != PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }

    /** The entry named [name], or null. */
    public fun entry(name: String): PluginArchiveEntry? = entries.firstOrNull { it.name == name }

    /** Limits, path rules, and construction from already-unpacked entries. */
    public companion object {
        /** The most entries (files and directories) an archive may hold. */
        public const val MAX_ENTRIES: Int = 4096

        /** The most bytes an archive's entries may add up to once unpacked: 64 MiB. */
        public const val MAX_UNPACKED_BYTES: Long = 64L * 1024 * 1024

        /** The longest entry path accepted, in characters. */
        public const val MAX_ENTRY_PATH_LENGTH: Int = 512

        /**
         * Whether [name] is a safe entry path: relative, `/`-separated, with no empty, `.` or `..`
         * segment, no backslash, colon or control character, and at most [MAX_ENTRY_PATH_LENGTH]
         * characters. A host can extract such a path under a directory without it escaping.
         */
        public fun isSafeEntryPath(name: String): Boolean =
            name.isNotEmpty() &&
                name.length <= MAX_ENTRY_PATH_LENGTH &&
                name.none { it == '\\' || it == ':' || it.code < 0x20 || it.code == 0x7F } &&
                name.split('/').none { it.isEmpty() || it == "." || it == ".." }

        /**
         * Builds an archive from unpacked [entries], applying the same checks [PluginArchiveReader]
         * does: at most [MAX_ENTRIES] entries and [MAX_UNPACKED_BYTES] bytes, safe and unique paths,
         * and a `plugin.json` that parses.
         *
         * @throws PluginArchiveException when any check fails.
         */
        public fun fromEntries(entries: List<PluginArchiveEntry>): PluginArchive {
            ensureArchive(entries.size <= MAX_ENTRIES) {
                "Archive holds ${entries.size} entries; at most $MAX_ENTRIES are allowed."
            }
            val unpacked = entries.sumOf { it.content.size.toLong() }
            ensureArchive(unpacked <= MAX_UNPACKED_BYTES) {
                "Archive unpacks to $unpacked bytes; at most $MAX_UNPACKED_BYTES are allowed."
            }
            val unsafe = entries.firstOrNull { !isSafeEntryPath(it.name) }
            ensureArchive(unsafe == null) { "Archive entry path '${unsafe?.name}' is not a safe relative path." }
            val duplicates = entries.groupBy { it.name }.filterValues { it.size > 1 }.keys
            ensureArchive(duplicates.isEmpty()) { "Archive entries appear more than once: ${duplicates.sorted()}." }
            val manifestEntry = entries.firstOrNull { it.name == PLUGIN_ARCHIVE_MANIFEST_ENTRY_NAME }
                ?: throw PluginArchiveException("Archive carries no $PLUGIN_ARCHIVE_MANIFEST_ENTRY_NAME entry.")
            val manifest = catchingNonCancellation {
                PluginManifest.fromJson(manifestEntry.content.decodeToString(throwOnInvalidSequence = true))
            }.getOrElse {
                throw PluginArchiveException("$PLUGIN_ARCHIVE_MANIFEST_ENTRY_NAME is not a valid manifest: ${it.message}", it)
            }
            return PluginArchive(manifest, entries)
        }
    }
}

/** A `.awakeplugin` archive is malformed, over a limit, or unsafe to unpack. */
public class PluginArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)
