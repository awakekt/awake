/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlin.concurrent.Volatile

/**
 * An opaque handle to a bundle master key for [ProjectIndex.ENCRYPTED_FORMAT_VERSION], opened via
 * [BundleSessionKeyExchange.unwrap], a [BundleKeyStore], or [fromRawBytes].
 *
 * On Web (`wasmJs`), handles produced by [BundleSessionKeyExchange.unwrap] or loaded from
 * [platformBundleKeyStore] hold a non-extractable WebCrypto `HKDF` `CryptoKey`: neither the 32-byte
 * master key nor any derived per-member AES key ever enters `WebAssembly.Memory`. On native and JVM
 * targets, or when built through [fromRawBytes], [close] zeroes the software key bytes held in
 * memory. [toString] never exposes key material.
 */
class BundleKeyHandle internal constructor(
    /** Which master key this handle opens bundles for; non-blank and not secret. */
    val keyId: String,
    internal val platformKey: PlatformBundleKey,
) : AutoCloseable {
    @Volatile
    private var closed: Boolean = false

    init {
        require(keyId.isNotBlank()) { "A bundle key needs a non-blank keyId" }
    }

    internal val isClosed: Boolean
        get() = closed

    internal fun activePlatformKey(): PlatformBundleKey {
        check(!closed) { "BundleKeyHandle($keyId) is closed" }
        return platformKey
    }

    /**
     * Releases this handle and zeroes any software key bytes it holds in memory. Calling [close] more
     * than once has no further effect; any later attempt to open a bundle member with this handle
     * fails with [IllegalStateException].
     */
    override fun close() {
        closed = true
        platformKey.close()
    }

    override fun toString(): String = "BundleKeyHandle(keyId=$keyId, key=<redacted>)"

    /** Factory methods for [BundleKeyHandle]. */
    companion object {
        /**
         * Copies [bytes] into a software-backed [BundleKeyHandle] for [keyId], so the caller may zero
         * [bytes] as soon as this call returns.
         *
         * @param keyId What the index names the key by; must not be blank.
         * @param bytes The raw master key: [BundleEncryptionKey.KEY_BYTES] bytes.
         * @return A handle owning its own copy of [bytes], zeroed on [close].
         * @throws IllegalArgumentException when [keyId] is blank or [bytes] is not
         * [BundleEncryptionKey.KEY_BYTES] bytes.
         */
        fun fromRawBytes(keyId: String, bytes: ByteArray): BundleKeyHandle {
            require(keyId.isNotBlank()) { "A bundle key needs a non-blank keyId" }
            require(bytes.size == BundleEncryptionKey.KEY_BYTES) {
                "A bundle key is ${BundleEncryptionKey.KEY_BYTES} bytes; got ${bytes.size}"
            }
            return BundleKeyHandle(keyId, SoftwareBundleKey(bytes, keyId))
        }
    }
}

/**
 * An ephemeral RSA-OAEP-SHA256 keypair generated on the client so a key endpoint can wrap a bundle
 * master key for this session ([BundleEncryptionKey.wrapFor]) instead of sending raw key bytes over
 * the wire or through script-visible buffers.
 *
 * Send [publicKeySpki] (X.509 `SubjectPublicKeyInfo` DER) to your authenticated key endpoint, receive
 * the RSA-OAEP-SHA256 ciphertext, and pass it to [unwrap]. On Web (`wasmJs`), the private unwrapping
 * key is generated with `extractable = false` and `unwrapKey` produces a non-extractable `HKDF`
 * `CryptoKey` directly inside SubtleCrypto.
 */
class BundleSessionKeyExchange internal constructor(
    private val spkiBytes: ByteArray,
    internal val unwrapper: PlatformSessionKeyUnwrapper,
) : AutoCloseable {
    @Volatile
    private var closed: Boolean = false

    /**
     * The ephemeral RSA-2048 public key in X.509 `SubjectPublicKeyInfo` DER encoding, suitable for
     * [BundleEncryptionKey.wrapFor]. Returns a fresh copy on each read.
     */
    val publicKeySpki: ByteArray
        get() = spkiBytes.copyOf()

    /**
     * Unwraps an RSA-OAEP-SHA256 [wrappedKey] (encrypted for [publicKeySpki] with SHA-256 digest,
     * MGF1-SHA256, and empty OAEP label) into a [BundleKeyHandle] for [keyId].
     *
     * @param keyId What the index names the key by; must not be blank.
     * @param wrappedKey The RSA-OAEP-SHA256 ciphertext produced by [BundleEncryptionKey.wrapFor].
     * @return An active [BundleKeyHandle] ready to open members sealed under [keyId].
     * @throws IllegalArgumentException when [keyId] is blank or [wrappedKey] fails RSA-OAEP unwrap or
     * size validation.
     * @throws IllegalStateException when this exchange has already been [close]d.
     */
    suspend fun unwrap(keyId: String, wrappedKey: ByteArray): BundleKeyHandle {
        require(keyId.isNotBlank()) { "A bundle key needs a non-blank keyId" }
        require(wrappedKey.isNotEmpty()) { "Wrapped bundle key $keyId is empty" }
        check(!closed) { "BundleSessionKeyExchange is closed" }
        val platformKey = unwrapper.unwrap(keyId, wrappedKey)
        if (closed) {
            platformKey.close()
            error("BundleSessionKeyExchange is closed")
        }
        return BundleKeyHandle(keyId, platformKey)
    }

    /**
     * Drops the ephemeral private unwrapping key so no further keys can be unwrapped with this
     * exchange. Calling [close] more than once has no further effect; handles already unwrapped remain
     * usable until they are closed themselves.
     */
    override fun close() {
        closed = true
        unwrapper.close()
    }

    override fun toString(): String = "BundleSessionKeyExchange(closed=$closed)"

    /** Key-size constants and generator for [BundleSessionKeyExchange]. */
    companion object {
        /** Modulus size in bits of the RSA-OAEP keypair [generate] creates. */
        const val DEFAULT_MODULUS_BITS: Int = 2048

        /** Shortest RSA modulus size in bits [BundleEncryptionKey.wrapFor] accepts. */
        const val MIN_MODULUS_BITS: Int = 2048

        /** Generates a fresh ephemeral RSA-2048-OAEP-SHA256 keypair for unwrapping bundle keys. */
        suspend fun generate(): BundleSessionKeyExchange = createPlatformSessionKeyExchange()
    }
}

internal interface PlatformSessionKeyUnwrapper : AutoCloseable {
    suspend fun unwrap(keyId: String, wrappedKey: ByteArray): PlatformBundleKey

    override fun close()
}

internal expect suspend fun createPlatformSessionKeyExchange(): BundleSessionKeyExchange
