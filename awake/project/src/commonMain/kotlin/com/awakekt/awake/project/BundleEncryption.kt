/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalSerializationApi::class)

package com.awakekt.awake.project

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Answers the master key that opens an encrypted [ProjectIndex]'s bundles, by its
 * [ProjectBundleEncryption.keyId] or a bundle's [ProjectIndexBundle.keyId]. The app owns where the
 * key comes from: its own authenticated endpoint after sign-in, a store on the device after a first
 * online sign-in, or, for a fully offline build, a key shipped in the client, which is deterrence
 * only. Core never stores what it answers unless given a [BundleKeyStore] via [wrapped].
 *
 * [IndexedAssetSource] calls [keyHandle] once per key id per source, when a bundle under that key id
 * is read; a failure is not remembered, so the next read asks again. Existing SAM implementations
 * (`BundleKeyProvider { keyId -> bytes }`) continue to work unchanged, while [wrapped] keeps session
 * keys non-extractable on platforms such as WebCrypto.
 */
fun interface BundleKeyProvider {
    /**
     * The raw master key named [keyId], for providers that supply raw key bytes.
     *
     * Providers built with [wrapped] keep keys non-extractable and throw
     * [UnsupportedOperationException] here; [IndexedAssetSource] calls [keyHandle] instead.
     *
     * @param keyId Which master key to answer.
     * @return [BundleEncryptionKey.KEY_BYTES] bytes.
     * @throws Exception when the key can't be had, such as a refused sign-in; the read that asked fails with it.
     */
    suspend fun key(keyId: String): ByteArray

    /**
     * An opaque [BundleKeyHandle] for [keyId]. By default wraps [key] with
     * [BundleKeyHandle.fromRawBytes]; [wrapped] overrides this to unwrap directly into a
     * platform-backed handle without exposing raw bytes.
     *
     * @param keyId Which master key to answer.
     * @return An open [BundleKeyHandle] for [keyId].
     * @throws Exception when the key can't be had or unwrapped; the read that asked fails with it.
     */
    suspend fun keyHandle(keyId: String): BundleKeyHandle = BundleKeyHandle.fromRawBytes(keyId, key(keyId))

    /** Factory methods for session-wrapped [BundleKeyProvider]s. */
    companion object {
        /**
         * Builds a [BundleKeyProvider] that lazily generates one ephemeral [BundleSessionKeyExchange],
         * passes its X.509 `SubjectPublicKeyInfo` DER public key to [fetchWrappedKey], and unwraps each
         * RSA-OAEP-SHA256 response into a [BundleKeyHandle] (non-extractable in WebCrypto on `wasmJs`).
         *
         * Calling [key] on the returned provider throws [UnsupportedOperationException].
         *
         * @param fetchWrappedKey Asks the app's authenticated key endpoint to wrap `keyId` for
         * `publicKeySpki` (with [BundleEncryptionKey.wrapFor]).
         * @return A [BundleKeyProvider] backed by session-wrapped handles.
         */
        fun wrapped(
            fetchWrappedKey: suspend (keyId: String, publicKeySpki: ByteArray) -> ByteArray,
        ): BundleKeyProvider = wrapped(store = null, fetchWrappedKey = fetchWrappedKey)

        /**
         * Like the single-argument [wrapped], checking [store] before calling [fetchWrappedKey] and
         * saving newly unwrapped handles to [store] when non-null (for example,
         * [platformBundleKeyStore] in a browser).
         *
         * @param store Optional persistence for unwrapped handles across visits; null keeps handles in
         * memory for this provider's life only.
         * @param fetchWrappedKey Asks the app's authenticated key endpoint to wrap `keyId` for
         * `publicKeySpki` (with [BundleEncryptionKey.wrapFor]).
         * @return A [BundleKeyProvider] backed by [store] and session-wrapped handles.
         */
        fun wrapped(
            store: BundleKeyStore?,
            fetchWrappedKey: suspend (keyId: String, publicKeySpki: ByteArray) -> ByteArray,
        ): BundleKeyProvider = WrappedBundleKeyProvider(store, fetchWrappedKey)
    }
}

/**
 * A master key a build seals bundle members with, for [ContentBundleWriter] and
 * [ContentBundleFormat.encodeEncrypted], or wraps for a signed-in client session with [wrapFor]. Read
 * it from a file or the environment at build or server startup time, never from a build script or the
 * repository. [toString] never shows the key.
 *
 * @property keyId What the index names the key by, so a [BundleKeyProvider] can find it; not secret, not blank.
 * @param bytes The key: [KEY_BYTES] random bytes.
 */
class BundleEncryptionKey(
    val keyId: String,
    bytes: ByteArray,
) {
    init {
        require(keyId.isNotBlank()) { "A bundle key needs a non-blank keyId" }
        require(bytes.size == KEY_BYTES) { "A bundle key is $KEY_BYTES bytes; got ${bytes.size}" }
    }

    internal val bytes: ByteArray = bytes.copyOf()

    /**
     * Encrypts this 32-byte master key for a client's ephemeral [BundleSessionKeyExchange.publicKeySpki]
     * using RSA-OAEP with SHA-256 digest (`MGF1-SHA256`) and an empty OAEP label (`associatedData = null`),
     * matching WebCrypto's `RSA-OAEP`, JCA's `OAEPParameterSpec`, and Apple Security's `SecRsaOaep`.
     *
     * @param publicKeySpki The client's X.509 `SubjectPublicKeyInfo` DER RSA public key (at least
     * [BundleSessionKeyExchange.MIN_MODULUS_BITS] bits).
     * @return The RSA-OAEP-SHA256 wrapped key bytes for [BundleSessionKeyExchange.unwrap].
     * @throws IllegalArgumentException when [publicKeySpki] is not a valid RSA SPKI DER key of at
     * least [BundleSessionKeyExchange.MIN_MODULUS_BITS] bits.
     */
    suspend fun wrapFor(publicKeySpki: ByteArray): ByteArray = wrapKeyBytesFor(bytes, publicKeySpki)

    override fun toString(): String = "BundleEncryptionKey(keyId=$keyId, bytes=<redacted>)"

    /** The key's size. */
    companion object {
        /** A master key is 32 bytes: an AES-256 key's size. */
        const val KEY_BYTES: Int = 32
    }
}

/**
 * How an index of [ProjectIndex.ENCRYPTED_FORMAT_VERSION] sealed its bundle members; the key itself
 * is never in the index.
 *
 * Each distinct member is sealed with AES-256-GCM; its key and nonce are the 44 bytes of
 * `HKDF-SHA256(ikm = master key, salt = keyId, info = "awake-awkb-v2" 0x00 plaintext SHA-256 hex)`.
 * Decision record: `docs/architecture/decisions/D42-content-bundle-encryption.md`.
 *
 * @property keyId Which master key opens the bundles, for the [BundleKeyProvider].
 * @property scheme The sealing scheme; [SCHEME] is the only one.
 */
@Serializable
data class ProjectBundleEncryption(
    val keyId: String,
    // Written out, so a reader never takes a missing scheme for this one.
    @EncodeDefault val scheme: String = SCHEME,
) {
    /** The supported schemes. */
    companion object {
        /** AES-256-GCM with a key and nonce derived per member by HKDF-SHA256. */
        const val SCHEME: String = "aes-256-gcm-hkdf-sha256"

        /** How many bytes sealing adds to a member: the GCM tag. */
        const val TAG_BYTES: Int = 16
    }
}
