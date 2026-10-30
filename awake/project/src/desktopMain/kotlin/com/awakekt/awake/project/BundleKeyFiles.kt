/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import java.io.File

/**
 * Reads a build's [BundleEncryptionKey] from where a publish step keeps secrets: a key file, or an
 * environment variable such as a CI secret, so the key is never in a build script or the repository.
 * `awake bundle` and the `generateContentBundles` Gradle task read keys through it.
 *
 * A key is [BundleEncryptionKey.KEY_BYTES] raw bytes (`openssl rand 32 > bundle.key`), or the same
 * bytes as 64 hex digits (`openssl rand -hex 32`), with any surrounding whitespace ignored. An
 * environment variable holds the hex form. No message ever shows the key.
 */
object BundleKeyFiles {
    private const val HEX_DIGITS = BundleEncryptionKey.KEY_BYTES * 2
    private const val HEX_RADIX = 16

    /**
     * The key [keyId] in [file].
     *
     * @param keyId What the index names the key by.
     * @param file The key file, outside the repository.
     * @throws IllegalArgumentException when [file] is missing or holds neither form of a key.
     */
    fun read(keyId: String, file: File): BundleEncryptionKey {
        require(file.isFile) { "The bundle key file ${file.path} doesn't exist" }
        return decode(keyId, file.readBytes()) ?: throw IllegalArgumentException(
            "${file.path} isn't a bundle key: it needs ${BundleEncryptionKey.KEY_BYTES} raw bytes or $HEX_DIGITS hex digits",
        )
    }

    /**
     * The key [keyId] in the environment variable [name], as 64 hex digits.
     *
     * @param keyId What the index names the key by.
     * @param name The variable, such as a CI secret's.
     * @param environment Looks a variable up; the process environment by default.
     * @throws IllegalArgumentException when the variable is unset or isn't 64 hex digits.
     */
    fun fromEnvironment(keyId: String, name: String, environment: (String) -> String? = System::getenv): BundleEncryptionKey {
        val value = environment(name) ?: throw IllegalArgumentException("The environment variable $name, which should hold the bundle key, isn't set")
        return hex(value.trim())?.let { BundleEncryptionKey(keyId, it) }
            ?: throw IllegalArgumentException("The environment variable $name isn't a bundle key: it needs $HEX_DIGITS hex digits")
    }

    private fun decode(keyId: String, bytes: ByteArray): BundleEncryptionKey? {
        if (bytes.size == BundleEncryptionKey.KEY_BYTES) return BundleEncryptionKey(keyId, bytes)
        return hex(bytes.decodeToString().trim())?.let { BundleEncryptionKey(keyId, it) }
    }

    private fun hex(text: String): ByteArray? {
        if (text.length != HEX_DIGITS || text.any { Character.digit(it, HEX_RADIX) < 0 }) return null
        return ByteArray(BundleEncryptionKey.KEY_BYTES) { text.substring(it * 2, it * 2 + 2).toInt(HEX_RADIX).toByte() }
    }
}
