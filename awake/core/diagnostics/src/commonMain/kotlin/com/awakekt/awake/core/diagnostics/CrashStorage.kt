/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

/**
 * Persistent key-value string store used by [CrashRecorder] to retain the active session and any
 * unreported crash across abrupt process or tab termination (for example `localStorage` on the web
 * or a small preferences file on desktop/mobile).
 */
interface CrashStorage {
    /**
     * Reads the persisted string value stored under [key], or `null` when absent.
     *
     * @param key Storage key to read.
     * @return Stored text payload, or `null` if not present.
     */
    fun read(key: String): String?

    /**
     * Writes [value] under [key], replacing any existing value.
     *
     * @param key Storage key to write.
     * @param value Serialized payload to persist.
     */
    fun write(key: String, value: String)

    /**
     * Removes any value stored under [key].
     *
     * @param key Storage key to clear.
     */
    fun remove(key: String)
}

/**
 * Map-backed [CrashStorage] kept in memory for headless tests, previews, and sandboxes without
 * durable host storage.
 */
class InMemoryCrashStorage : CrashStorage {
    private val entries = LinkedHashMap<String, String>()

    override fun read(key: String): String? = entries[key]

    override fun write(key: String, value: String) {
        entries[key] = value
    }

    override fun remove(key: String) {
        entries.remove(key)
    }

    /** Clears all stored keys from memory. */
    fun clear() {
        entries.clear()
    }
}
