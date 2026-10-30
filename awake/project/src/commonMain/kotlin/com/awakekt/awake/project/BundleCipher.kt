/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.coroutines.cancellation.CancellationException

/**
 * Seals and opens bundle members under one master key, as [ProjectBundleEncryption] describes,
 * through the platform's own provider. Every member gets its own key and nonce, derived from its
 * plaintext SHA-256, so the same member and key always seal to the same bytes.
 */
internal class BundleCipher(master: ByteArray, private val keyId: String) {
    private val master = master.copyOf()

    init {
        require(master.size == BundleEncryptionKey.KEY_BYTES) {
            "The bundle key $keyId is ${BundleEncryptionKey.KEY_BYTES} bytes; got ${master.size}"
        }
    }

    /** [plaintext], whose SHA-256 is [sha256], sealed: [ProjectBundleEncryption.TAG_BYTES] longer. */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun seal(plaintext: ByteArray, sha256: String): ByteArray {
        val (key, nonce) = derive(sha256)
        // Sealing the same member twice is deterministic by design, but the JDK refuses an encryption
        // whose key and nonce match the last one its pooled Cipher ran. One throwaway encryption under
        // a random key after each seal leaves the pooled Cipher on other values, and a refusal that a
        // concurrent seal still causes is retried once.
        val sealed = runCatching { key.cipher().encryptWithIv(nonce, plaintext) }
            .recoverCatching { if (it is CancellationException) throw it else scramble().let { key.cipher().encryptWithIv(nonce, plaintext) } }
            .getOrThrow()
        scramble()
        return sealed
    }

    /** One encryption of nothing under a random key and nonce, so no pooled cipher keeps a derived key last. */
    private suspend fun scramble() {
        val throwaway = scrambleKey ?: CryptographyProvider.Default.get(AES.GCM).keyGenerator(AES.Key.Size.B256).generateKey().also { scrambleKey = it }
        throwaway.cipher().encrypt(ByteArray(0))
    }

    private var scrambleKey: AES.GCM.Key? = null

    /**
     * [sealed] opened, once its tag proves it is the member whose plaintext SHA-256 is [sha256] under
     * this key; otherwise fails naming [label].
     */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun open(sealed: ByteArray, sha256: String, label: String): ByteArray {
        val (key, nonce) = derive(sha256)
        // The key is this member's alone, so a tag that checks proves the plaintext too.
        return try {
            key.cipher().decryptWithIv(nonce, sealed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            // Each provider throws its own type for a bad tag; all mean the same thing here.
            throw IllegalStateException("$label failed authentication: the wrong key ($keyId), or damaged bytes", failure)
        }
    }

    private suspend fun derive(sha256: String): Pair<AES.GCM.Key, ByteArray> {
        val provider = CryptographyProvider.Default
        val okm = provider.get(HKDF)
            .secretDerivation(SHA256, (AES_KEY_BYTES + NONCE_BYTES).bytes, keyId.encodeToByteArray(), INFO_PREFIX + sha256.encodeToByteArray())
            .deriveSecretToByteArray(master)
        val key = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, okm.copyOfRange(0, AES_KEY_BYTES))
        return key to okm.copyOfRange(AES_KEY_BYTES, okm.size)
    }

    override fun toString(): String = "BundleCipher(keyId=$keyId)"

    private companion object {
        const val AES_KEY_BYTES = 32
        const val NONCE_BYTES = 12
        val INFO_PREFIX = "awake-awkb-v2".encodeToByteArray() + byteArrayOf(0)
    }
}
