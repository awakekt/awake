/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.project

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * Ephemeral RSA-2048-OAEP-SHA256 key exchange in SubtleCrypto: the private key is generated with
 * `extractable = false` (`usages = ["unwrapKey"]`) and unwraps bundle keys directly into
 * non-extractable `HKDF` `CryptoKey` handles (`extractable = false`, `usages = ["deriveBits"]`).
 */
internal actual suspend fun createPlatformSessionKeyExchange(): BundleSessionKeyExchange {
    val keyPair = jsGenerateRsaOaepKeyPair().await<JsAny>()
    val spki = jsExportPublicKeySpki(keyPair).await<ArrayBuffer>().copyToByteArray()
    return BundleSessionKeyExchange(spki, WebCryptoSessionKeyUnwrapper(jsPrivateKeyOf(keyPair)))
}

/** Browser default [BundleKeyStore]: structured-cloned non-extractable `CryptoKey`s in IndexedDB. */
internal actual fun createPlatformBundleKeyStore(namespace: String): BundleKeyStore? =
    IndexedDbBundleKeyStore(namespace)

internal class WebCryptoSessionKeyUnwrapper(
    private var privateKey: JsAny?,
) : PlatformSessionKeyUnwrapper {
    internal val privateCryptoKeyForTest: JsAny?
        get() = privateKey

    override suspend fun unwrap(keyId: String, wrappedKey: ByteArray): PlatformBundleKey {
        val key = checkNotNull(privateKey) { "BundleSessionKeyExchange is closed" }
        val wrappedBuffer = wrappedKey.toArrayBuffer()
        val hkdfKey = try {
            jsUnwrapBundleKey(key, wrappedBuffer).await<JsAny>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            throw IllegalArgumentException(
                "Could not unwrap bundle key $keyId: invalid or tampered RSA-OAEP ciphertext",
                failure,
            )
        } finally {
            jsZeroArrayBuffer(wrappedBuffer)
        }
        return WebCryptoBundleKey(keyId, hkdfKey)
    }

    override fun close() {
        privateKey = null
    }
}

/**
 * Non-extractable WebCrypto `HKDF` `CryptoKey` backing: derives each member's AES-256-GCM key and
 * 96-bit IV, decrypts the member, and zeroes the derived bits entirely inside JS/SubtleCrypto so
 * neither the 32-byte master key nor any derived AES key ever enters `WebAssembly.Memory.buffer`.
 */
internal class WebCryptoBundleKey(
    private val keyId: String,
    private var cryptoKey: JsAny?,
) : PlatformBundleKey {
    internal val hkdfCryptoKeyForTest: JsAny?
        get() = cryptoKey

    internal fun activeCryptoKey(): JsAny = checkNotNull(cryptoKey) { "BundleKeyHandle($keyId) is closed" }

    override suspend fun open(sealed: ByteArray, sha256: String, label: String): ByteArray {
        val hkdfKey = activeCryptoKey()
        val sealedBuffer = sealed.toArrayBuffer()
        return try {
            jsOpenSealedMember(hkdfKey, keyId, sha256, sealedBuffer).await<ArrayBuffer>().copyToByteArray()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            throw BundleAuthenticationException(
                "$label failed authentication: the wrong key ($keyId), or damaged bytes",
                failure,
            )
        }
    }

    override fun close() {
        cryptoKey = null
    }
}

@JsFun(
    """
() => globalThis.crypto.subtle.generateKey(
  {
    name: 'RSA-OAEP',
    modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]),
    hash: 'SHA-256',
  },
  false,
  ['wrapKey', 'unwrapKey']
)
    """,
)
private external fun jsGenerateRsaOaepKeyPair(): Promise<JsAny>

@JsFun("(keyPair) => globalThis.crypto.subtle.exportKey('spki', keyPair.publicKey)")
private external fun jsExportPublicKeySpki(keyPair: JsAny): Promise<ArrayBuffer>

@JsFun("(keyPair) => keyPair.privateKey")
private external fun jsPrivateKeyOf(keyPair: JsAny): JsAny

@JsFun(
    """
(privateKey, wrappedBuffer) => globalThis.crypto.subtle.unwrapKey(
  'raw',
  wrappedBuffer,
  privateKey,
  { name: 'RSA-OAEP' },
  { name: 'HKDF' },
  false,
  ['deriveBits']
)
    """,
)
private external fun jsUnwrapBundleKey(privateKey: JsAny, wrappedBuffer: ArrayBuffer): Promise<JsAny>

@JsFun(
    """
(hkdfKey, keyId, sha256, sealedBuffer) => (async () => {
  const encoder = new TextEncoder();
  const salt = encoder.encode(keyId);
  const info = encoder.encode('awake-awkb-v2\x00' + sha256);
  const derived = await globalThis.crypto.subtle.deriveBits(
    { name: 'HKDF', hash: 'SHA-256', salt, info },
    hkdfKey,
    352
  );
  const bits = new Uint8Array(derived);
  try {
    const aesKey = await globalThis.crypto.subtle.importKey(
      'raw',
      bits.subarray(0, 32),
      { name: 'AES-GCM' },
      false,
      ['decrypt']
    );
    return await globalThis.crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: bits.subarray(32, 44), tagLength: 128 },
      aesKey,
      sealedBuffer
    );
  } finally {
    bits.fill(0);
  }
})()
    """,
)
private external fun jsOpenSealedMember(
    hkdfKey: JsAny,
    keyId: String,
    sha256: String,
    sealedBuffer: ArrayBuffer,
): Promise<ArrayBuffer>

@JsFun("(buffer) => { new Uint8Array(buffer).fill(0); }")
internal external fun jsZeroArrayBuffer(buffer: ArrayBuffer)
