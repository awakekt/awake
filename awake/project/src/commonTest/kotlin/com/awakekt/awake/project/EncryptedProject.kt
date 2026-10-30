/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

/** [BundledProject.SAMPLE_FILES] written as encrypted bundles under [key]. */
internal class EncryptedProject private constructor(
    val files: Map<String, ByteArray>,
    val key: ByteArray,
    val index: ProjectIndex,
    val bundles: Map<String, WrittenContentBundle>,
) {
    /** A provider that answers [key] for [KEY_ID]. */
    fun keys(): BundleKeyProvider = BundleKeyProvider { keyId ->
        check(keyId == KEY_ID) { "unknown key $keyId" }
        key
    }

    /** Serves the bundles by URL, as a public CDN would, with [override] in place of some. */
    fun server(override: Map<String, ByteArray> = emptyMap()): suspend (String) -> ByteArray {
        val byUrl = bundles.mapValues { it.value.bytes } + override
        return { url -> byUrl[url] ?: error("404 $url") }
    }

    companion object {
        const val KEY_ID = "harbor-town-2026"

        val KEY: ByteArray = ByteArray(BundleEncryptionKey.KEY_BYTES) { (it * 11 + 3).toByte() }

        /** Writes [files] with [key], one bundle per folder, as a publish step would. */
        suspend fun write(files: Map<String, ByteArray> = BundledProject.SAMPLE_FILES, key: ByteArray = KEY): EncryptedProject {
            val bundles = LinkedHashMap<String, WrittenContentBundle>()
            val writer = ContentBundleWriter(encryption = BundleEncryptionKey(KEY_ID, key))
            val index = writer.write(looseIndexOf(files), { files.getValue(it.path) }) { bundles[it.url] = it }
            return EncryptedProject(files, key, index, bundles)
        }
    }
}

/** Whether [run] appears in this array as consecutive bytes. */
internal fun ByteArray.containsRun(run: ByteArray): Boolean =
    run.isEmpty() || (0..size - run.size).any { start -> run.indices.all { this[start + it] == run[it] } }

/** Lowercase hex of these bytes. */
internal fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
