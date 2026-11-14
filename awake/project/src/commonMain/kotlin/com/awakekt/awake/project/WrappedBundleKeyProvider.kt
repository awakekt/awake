/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

/**
 * [BundleKeyProvider.wrapped]'s implementation: generates one ephemeral [BundleSessionKeyExchange] on
 * first cache miss, unwraps each requested `keyId` into a [BundleKeyHandle], and optionally persists
 * handles in [store].
 */
internal class WrappedBundleKeyProvider(
    private val store: BundleKeyStore?,
    private val fetchWrappedKey: suspend (keyId: String, publicKeySpki: ByteArray) -> ByteArray,
) : BundleKeyProvider,
    AutoCloseable {
    private val exchangeLock = Mutex()

    @Volatile
    private var exchange: BundleSessionKeyExchange? = null

    private val issuedHandles = MutableStateFlow<Set<BundleKeyHandle>>(emptySet())

    @Volatile
    private var closed = false

    override suspend fun key(keyId: String): ByteArray =
        throw UnsupportedOperationException("Wrapped bundle keys are non-extractable; use keyHandle")

    override suspend fun keyHandle(keyId: String): BundleKeyHandle {
        require(keyId.isNotBlank()) { "A bundle key needs a non-blank keyId" }
        check(!closed) { "WrappedBundleKeyProvider is closed" }
        val cached = store?.get(keyId)?.takeIf { !it.isClosed }
        if (cached != null) return trackHandle(cached)
        val session = sessionExchange()
        val wrapped = fetchWrappedKey(keyId, session.publicKeySpki)
        val handle = session.unwrap(keyId, wrapped)
        if (closed) {
            handle.close()
            error("WrappedBundleKeyProvider is closed")
        }
        store?.put(handle)
        return trackHandle(handle)
    }

    internal suspend fun forgetStored(keyId: String) {
        store?.delete(keyId)
    }

    override fun close() {
        closed = true
        val current = exchange
        exchange = null
        current?.close()
        closeIssuedHandles()
    }

    private fun trackHandle(handle: BundleKeyHandle): BundleKeyHandle {
        while (true) {
            val current = issuedHandles.value
            if (issuedHandles.compareAndSet(current, current + handle)) break
        }
        if (closed) {
            closeIssuedHandles()
            handle.close()
            error("WrappedBundleKeyProvider is closed")
        }
        return handle
    }

    private fun closeIssuedHandles() {
        while (true) {
            val current = issuedHandles.value
            if (current.isEmpty()) return
            if (issuedHandles.compareAndSet(current, emptySet())) {
                current.forEach { it.close() }
                return
            }
        }
    }

    private suspend fun sessionExchange(): BundleSessionKeyExchange = exchangeLock.withLock {
        check(!closed) { "WrappedBundleKeyProvider is closed" }
        exchange?.let { return@withLock it }
        val created = BundleSessionKeyExchange.generate()
        exchange = created
        if (closed) {
            exchange = null
            created.close()
            error("WrappedBundleKeyProvider is closed")
        }
        created
    }
}

/**
 * Ephemeral RSA-OAEP-SHA256 keypair generation and unwrapping through [CryptographyProvider.Default],
 * shared by desktop, Android, and iOS.
 */
internal suspend fun createDefaultProviderSessionKeyExchange(): BundleSessionKeyExchange {
    val rsa = CryptographyProvider.Default.get(RSA.OAEP)
    val keyPair = rsa.keyPairGenerator(BundleSessionKeyExchange.DEFAULT_MODULUS_BITS.bits, SHA256).generateKey()
    val spki = keyPair.publicKey.encodeToByteArray(RSA.PublicKey.Format.DER)
    return BundleSessionKeyExchange(spki, DefaultProviderSessionKeyUnwrapper(keyPair.privateKey))
}

private class DefaultProviderSessionKeyUnwrapper(
    @Volatile private var privateKey: RSA.OAEP.PrivateKey?,
) : PlatformSessionKeyUnwrapper {
    override suspend fun unwrap(keyId: String, wrappedKey: ByteArray): PlatformBundleKey {
        val key = checkNotNull(privateKey) { "BundleSessionKeyExchange is closed" }
        val decrypted = try {
            // Empty OAEP label (associatedData = null): matches BundleEncryptionKey.wrapFor and Apple SecRsaOaep.
            key.decryptor().decrypt(wrappedKey)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            throw IllegalArgumentException(
                "Could not unwrap bundle key $keyId: invalid or tampered RSA-OAEP ciphertext",
                failure,
            )
        }
        return try {
            require(decrypted.size == BundleEncryptionKey.KEY_BYTES) {
                "Unwrapped bundle key $keyId must be ${BundleEncryptionKey.KEY_BYTES} bytes; got ${decrypted.size}"
            }
            SoftwareBundleKey(decrypted, keyId)
        } finally {
            decrypted.fill(0)
        }
    }

    override fun close() {
        privateKey = null
    }
}

/**
 * Validates [publicKeySpki] as an X.509 `SubjectPublicKeyInfo` DER RSA public key of at least
 * [BundleSessionKeyExchange.MIN_MODULUS_BITS] bits and encrypts [keyBytes] with RSA-OAEP-SHA256
 * (`MGF1-SHA256`, empty OAEP label).
 */
internal suspend fun wrapKeyBytesFor(keyBytes: ByteArray, publicKeySpki: ByteArray): ByteArray {
    require(publicKeySpki.isNotEmpty()) { "RSA public key SPKI DER is empty" }
    val publicKey = try {
        CryptographyProvider.Default.get(RSA.OAEP)
            .publicKeyDecoder(SHA256)
            .decodeFromByteArray(RSA.PublicKey.Format.DER, publicKeySpki)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
        throw IllegalArgumentException("Invalid X.509 SubjectPublicKeyInfo RSA public key DER", failure)
    }
    val bits = rsaModulusBits(publicKey)
    require(bits >= BundleSessionKeyExchange.MIN_MODULUS_BITS) {
        "RSA wrapping key must be at least ${BundleSessionKeyExchange.MIN_MODULUS_BITS} bits; got $bits"
    }
    // Empty OAEP label (associatedData = null): required for Apple SecRsaOaep and WebCrypto default RSA-OAEP unwrap.
    return publicKey.encryptor().encrypt(keyBytes)
}

private suspend fun rsaModulusBits(key: RSA.OAEP.PublicKey): Int {
    val jwk = Json.parseToJsonElement(key.encodeToByteArray(RSA.PublicKey.Format.JWK).decodeToString())
    val modulus = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        .decode(jwk.jsonObject.getValue("n").jsonPrimitive.content)
    val firstNonZero = modulus.indexOfFirst { it != 0.toByte() }
    return if (firstNonZero < 0) {
        0
    } else {
        val significant = modulus.size - firstNonZero
        (significant - 1) * 8 + (32 - (modulus[firstNonZero].toInt() and 0xFF).countLeadingZeroBits())
    }
}
