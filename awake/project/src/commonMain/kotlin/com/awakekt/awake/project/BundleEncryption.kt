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
 * [ProjectBundleEncryption.keyId]. The app owns where the key comes from: its own authenticated
 * endpoint after sign-in, a store on the device after a first online sign-in, or, for a fully offline
 * build, a key shipped in the client, which is deterrence only. Core never stores what it answers.
 *
 * [IndexedAssetSource] asks once per source; a failure is not remembered, so the next read asks again.
 */
fun interface BundleKeyProvider {
    /**
     * The master key named [keyId].
     *
     * @return [BundleEncryptionKey.KEY_BYTES] bytes.
     * @throws Exception when the key can't be had, such as a refused sign-in; the read that asked fails with it.
     */
    suspend fun key(keyId: String): ByteArray
}

/**
 * A master key a build seals bundle members with, for [ContentBundleWriter] and
 * [ContentBundleFormat.encodeEncrypted]. Read it from a file or the environment at build time, never
 * from a build script or the repository. [toString] never shows the key.
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
