/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

/** 2048 bits: shorter RSA keys are factorable, and a signature they make proves nothing. */
internal const val MIN_RSA_MODULUS_BITS: Int = 2048

/** SHA-256 of [bytes] through the platform provider (JDK, Apple, or WebCrypto). */
internal suspend fun sha256(bytes: ByteArray): ByteArray =
    CryptographyProvider.Default.get(SHA256).hasher().hash(bytes)

/** Why a signature check failed, or null when it passed. */
internal sealed class RsaCheck {
    data object Valid : RsaCheck()

    data class Invalid(val reason: String) : RsaCheck()
}

/**
 * Checks an RSASSA-PKCS1-v1_5 / SHA-256 signature (Java's `SHA256withRSA`) over [payload] against
 * [publicKey], an X.509 `SubjectPublicKeyInfo` in PEM or bare base64 DER (what `openssl rsa -pubout`
 * writes). Never throws: a malformed key or signature is an [RsaCheck.Invalid].
 */
internal suspend fun checkRsaSha256(payload: ByteArray, signature: ByteArray, publicKey: String): RsaCheck =
    catchingNonCancellation {
        val key = CryptographyProvider.Default.get(RSA.PKCS1)
            .publicKeyDecoder(SHA256)
            .decodeFromByteArray(RSA.PublicKey.Format.DER, publicKeyDer(publicKey))
        when {
            modulusBits(key) < MIN_RSA_MODULUS_BITS -> RsaCheck.Invalid("RSA key is shorter than $MIN_RSA_MODULUS_BITS bits.")
            key.signatureVerifier().tryVerifySignature(payload, signature) -> RsaCheck.Valid
            else -> RsaCheck.Invalid("Signature does not match the signed bytes and public key.")
        }
    }.getOrElse { RsaCheck.Invalid("Could not verify the signature: ${it.message}") }

/** Strips PEM armour, if any, and decodes the base64 body to DER. */
internal fun publicKeyDer(publicKey: String): ByteArray {
    val body = publicKey.lineSequence()
        .filterNot { it.isBlank() || it.trimStart().startsWith("-----") }
        .joinToString("") { it.trim() }
    require(body.isNotEmpty()) { "The public key is empty." }
    return Base64.decode(body)
}

/**
 * The key's modulus length in bits, read from its JWK export: every provider, WebCrypto included,
 * exports a public key as JWK, which saves parsing ASN.1 here.
 */
private suspend fun modulusBits(key: RSA.PKCS1.PublicKey): Int {
    val jwk = Json.parseToJsonElement(key.encodeToByteArray(RSA.PublicKey.Format.JWK).decodeToString())
    val modulus = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        .decode(jwk.jsonObject.getValue("n").jsonPrimitive.content)
    val leadingZeros = modulus.indexOfFirst { it != 0.toByte() }.let { if (it < 0) modulus.size else it }
    val significant = modulus.size - leadingZeros
    if (significant == 0) return 0
    return (significant - 1) * 8 + (32 - (modulus[leadingZeros].toInt() and 0xFF).countLeadingZeroBits())
}

/** Decodes a base64 signature, as signed documents carry it; null when it is not base64. */
internal fun decodeBase64OrNull(value: String): ByteArray? =
    runCatching { Base64.decode(value.trim()) }.getOrNull()
