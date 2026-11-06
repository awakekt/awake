/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.core.io.suspendRunCatching
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/** Platform key backing inside a [BundleKeyHandle]: software bytes or a non-extractable WebCrypto `CryptoKey`. */
internal interface PlatformBundleKey : AutoCloseable {
    /**
     * [sealed] opened, once its tag proves it is the member whose plaintext SHA-256 is [sha256] under
     * this key; otherwise fails with [BundleAuthenticationException] naming [label].
     */
    suspend fun open(sealed: ByteArray, sha256: String, label: String): ByteArray

    override fun close()
}

/**
 * Software-backed [PlatformBundleKey] holding its own copy of the 32-byte master key, zeroed on [close].
 */
internal class SoftwareBundleKey(master: ByteArray, private val keyId: String) : PlatformBundleKey {
    private val master: ByteArray = master.copyOf()

    @Volatile
    private var closed: Boolean = false

    private var scrambleKey: AES.GCM.Key? = null

    init {
        require(master.size == BundleEncryptionKey.KEY_BYTES) {
            "The bundle key $keyId is ${BundleEncryptionKey.KEY_BYTES} bytes; got ${master.size}"
        }
    }

    internal fun masterCopyForTest(): ByteArray = master.copyOf()

    internal fun copyActiveMasterBytes(): ByteArray {
        check(!closed) { "BundleKeyHandle($keyId) is closed" }
        return master.copyOf()
    }

    /** [plaintext], whose SHA-256 is [sha256], sealed: [ProjectBundleEncryption.TAG_BYTES] longer. */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun seal(plaintext: ByteArray, sha256: String): ByteArray {
        check(!closed) { "BundleKeyHandle($keyId) is closed" }
        // The key and nonce are this plaintext's alone only if sha256 is truly its hash: checked, so no
        // caller can seal two plaintexts under one nonce.
        require(Sha256.digestHex(plaintext) == sha256) { "seal needs the plaintext's own SHA-256" }
        val (key, nonce) = derive(sha256)
        // Sealing the same member twice is deterministic by design, but the JDK refuses an encryption
        // whose key and nonce match the last one its pooled Cipher ran. One throwaway encryption under
        // a random key after each seal leaves the pooled Cipher on other values, and a refusal that a
        // concurrent seal still causes is retried once.
        val sealed = suspendRunCatching { key.cipher().encryptWithIv(nonce, plaintext) }.getOrElse {
            scramble()
            key.cipher().encryptWithIv(nonce, plaintext)
        }
        scramble()
        return sealed
    }

    /** One encryption of nothing under a random key and nonce, so no pooled cipher keeps a derived key last. */
    private suspend fun scramble() {
        val throwaway = scrambleKey ?: CryptographyProvider.Default.get(AES.GCM).keyGenerator(AES.Key.Size.B256).generateKey().also { scrambleKey = it }
        throwaway.cipher().encrypt(ByteArray(0))
    }

    @OptIn(DelicateCryptographyApi::class)
    override suspend fun open(sealed: ByteArray, sha256: String, label: String): ByteArray {
        check(!closed) { "BundleKeyHandle($keyId) is closed" }
        val (key, nonce) = derive(sha256)
        // The key is this member's alone, so a tag that checks proves the plaintext too.
        return try {
            key.cipher().decryptWithIv(nonce, sealed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            // Each provider throws its own type for a bad tag, and WebCrypto's rejection reaches wasm as
            // a JsException, which is a Throwable but not an Exception; all mean the same thing here.
            throw BundleAuthenticationException("$label failed authentication: the wrong key ($keyId), or damaged bytes", failure)
        }
    }

    private suspend fun derive(sha256: String): Pair<AES.GCM.Key, ByteArray> {
        val provider = CryptographyProvider.Default
        val okm = provider.get(HKDF)
            .secretDerivation(SHA256, (AES_KEY_BYTES + NONCE_BYTES).bytes, keyId.encodeToByteArray(), INFO_PREFIX + sha256.encodeToByteArray())
            .deriveSecretToByteArray(master)
        val rawKey = okm.copyOfRange(0, AES_KEY_BYTES)
        return try {
            val key = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, rawKey)
            val nonce = okm.copyOfRange(AES_KEY_BYTES, okm.size)
            key to nonce
        } finally {
            // The derived bytes are imported into the provider's key; neither intermediate copy stays live.
            rawKey.fill(0)
            okm.fill(0)
        }
    }

    override fun close() {
        closed = true
        master.fill(0)
        scrambleKey = null
    }

    private companion object {
        const val AES_KEY_BYTES = 32
        const val NONCE_BYTES = 12
        val INFO_PREFIX = "awake-awkb-v2".encodeToByteArray() + byteArrayOf(0)
    }
}

/**
 * Seals and opens bundle members under one master key ([BundleKeyHandle]), as
 * [ProjectBundleEncryption] describes, through the platform's own provider. Every member gets its own
 * key and nonce, derived from its plaintext SHA-256, so the same member and key always seal to the
 * same bytes.
 */
internal class BundleCipher(private val handle: BundleKeyHandle) : AutoCloseable {
    constructor(master: ByteArray, keyId: String) : this(BundleKeyHandle.fromRawBytes(keyId, master))

    /** [plaintext], whose SHA-256 is [sha256], sealed: [ProjectBundleEncryption.TAG_BYTES] longer. */
    suspend fun seal(plaintext: ByteArray, sha256: String): ByteArray {
        val software = handle.activePlatformKey() as? SoftwareBundleKey
            ?: error("Non-extractable bundle key ${handle.keyId} is for opening bundles, not sealing")
        return software.seal(plaintext, sha256)
    }

    /**
     * [sealed] opened, once its tag proves it is the member whose plaintext SHA-256 is [sha256] under
     * this key; otherwise fails with [BundleAuthenticationException] naming [label].
     */
    suspend fun open(sealed: ByteArray, sha256: String, label: String): ByteArray =
        handle.activePlatformKey().open(sealed, sha256, label)

    override fun close() {
        handle.close()
    }

    override fun toString(): String = "BundleCipher(keyId=${handle.keyId})"
}

/** A sealed member that didn't authenticate under the key: the wrong key, or damaged bytes. */
internal class BundleAuthenticationException(message: String, cause: Throwable) : IllegalStateException(message, cause)
