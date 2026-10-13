/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import com.awakekt.awake.core.io.Sha256
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Result of checking a `.awakeplugin` archive's signature against a publisher's public key. */
sealed class PluginArchiveVerification {
    /** The archive's [PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME] matches its contents under the given key. */
    data object Verified : PluginArchiveVerification()

    /**
     * No [PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME] entry: the archive was packaged without a signing key.
     *
     * @property reason Human-readable explanation, for logs and host UI.
     */
    data class Unsigned(val reason: String) : PluginArchiveVerification()

    /**
     * A signature entry exists but does not check out: tampered contents, corrupt data, or the wrong key.
     *
     * @property reason Human-readable explanation, for logs and host UI.
     */
    data class Unverified(val reason: String) : PluginArchiveVerification()
}

/**
 * Checks a `.awakeplugin` archive's RSA/SHA-256 (PKCS#1 v1.5) signature in pure Kotlin, so it runs
 * identically on every Awake target (JVM, Android, wasmJs, iOS) with no platform crypto provider.
 * `templates/plugin`'s packaging task signs [PluginArchiveDigest]'s canonical digest the same way,
 * using `java.security`'s `SHA256withRSA` on the publisher's machine; the public key here is that
 * key pair's public half, an X.509 `SubjectPublicKeyInfo` in PEM or base64 DER (what
 * `openssl rsa -pubout` writes). A host maps a plugin's [PluginManifest.publisher] to this key.
 */
object PluginArchiveVerifier {
    /** Checks [archiveEntries] (one of which should be `signature.sig`) against [publicKeyPem]. */
    fun verify(archiveEntries: List<PluginArchiveEntry>, publicKeyPem: String): PluginArchiveVerification {
        val signatureEntry = archiveEntries.find { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }
            ?: return PluginArchiveVerification.Unsigned(
                "Archive carries no $PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME entry.",
            )

        return runCatching { checkRsaSignature(archiveEntries, signatureEntry.content, publicKeyPem) }
            .getOrElse { error ->
                PluginArchiveVerification.Unverified("Could not verify the signature: ${error.message}")
            }
    }
}

/** The actual PKCS#1 v1.5 check; separated so [PluginArchiveVerifier.verify] stays low-branching. */
private fun checkRsaSignature(
    archiveEntries: List<PluginArchiveEntry>,
    signatureBytes: ByteArray,
    publicKeyPem: String,
): PluginArchiveVerification {
    val publicKey = parseRsaPublicKey(publicKeyPem)
    val digest = PluginArchiveDigest.canonicalDigest(archiveEntries)
    val expected = pkcs1Sha256EncodedMessage(PluginArchiveDigest.signingPayload(digest), publicKey.byteLength)
    val signature = BigInteger.fromByteArray(signatureBytes, Sign.POSITIVE)

    return when {
        publicKey.byteLength < MIN_MODULUS_BYTES ->
            PluginArchiveVerification.Unverified("RSA key is shorter than ${MIN_MODULUS_BYTES * 8} bits.")
        publicKey.exponent < BigInteger.fromInt(3) || (publicKey.exponent mod BigInteger.TWO) == BigInteger.ZERO ->
            PluginArchiveVerification.Unverified("RSA public exponent must be odd and at least 3.")
        expected == null ->
            PluginArchiveVerification.Unverified("RSA modulus is too small for a SHA-256 signature.")
        signature.isZero() || signature >= publicKey.modulus ->
            PluginArchiveVerification.Unverified("Signature is out of range for the public modulus.")
        else -> {
            val actual = modPow(signature, publicKey.exponent, publicKey.modulus).toByteArray().leftPadTo(publicKey.byteLength)
            if (actual.contentEquals(expected)) {
                PluginArchiveVerification.Verified
            } else {
                PluginArchiveVerification.Unverified("Signature does not match the archive contents and public key.")
            }
        }
    }
}

/** 2048 bits: shorter RSA keys are factorable, and a signature they make proves nothing. */
private const val MIN_MODULUS_BYTES = 256

private class RsaPublicKey(val modulus: BigInteger, val exponent: BigInteger, val byteLength: Int)

@OptIn(ExperimentalEncodingApi::class)
private fun parseRsaPublicKey(publicKeyPem: String): RsaPublicKey {
    val base64Body = publicKeyPem.lineSequence()
        .filterNot { it.isBlank() || it.trimStart().startsWith("-----") }
        .joinToString("") { it.trim() }
    val der = Base64.decode(base64Body)

    val spki = DerReader(der).readTagged(DerTag.SEQUENCE)
    val spkiReader = DerReader(spki)
    spkiReader.readTagged(DerTag.SEQUENCE) // AlgorithmIdentifier: only rsaEncryption is supported, not checked.
    val bitString = spkiReader.readTagged(DerTag.BIT_STRING)
    require(bitString.isNotEmpty() && bitString[0] == 0.toByte()) { "Unsupported subjectPublicKey encoding." }

    val rsaKeyReader = DerReader(bitString.copyOfRange(1, bitString.size))
    val rsaKey = DerReader(rsaKeyReader.readTagged(DerTag.SEQUENCE))
    val modulusBytes = rsaKey.readTagged(DerTag.INTEGER).stripDerSignByte()
    val exponentBytes = rsaKey.readTagged(DerTag.INTEGER).stripDerSignByte()

    return RsaPublicKey(
        modulus = BigInteger.fromByteArray(modulusBytes, Sign.POSITIVE),
        exponent = BigInteger.fromByteArray(exponentBytes, Sign.POSITIVE),
        byteLength = modulusBytes.size,
    )
}

private enum class DerTag(val byte: Int) { SEQUENCE(0x30), INTEGER(0x02), BIT_STRING(0x03) }

/** Reads one DER tag-length-value triple at a time from a fixed byte buffer. */
private class DerReader(private val bytes: ByteArray) {
    private var position = 0

    fun readTagged(expected: DerTag): ByteArray {
        require(position < bytes.size) { "Unexpected end of DER data." }
        val tag = bytes[position++].toInt() and 0xFF
        require(tag == expected.byte) { "Expected DER tag ${expected.byte} but found $tag." }
        var length = bytes[position++].toInt() and 0xFF
        if (length and 0x80 != 0) {
            val lengthBytes = length and 0x7F
            length = 0
            repeat(lengthBytes) { length = (length shl 8) or (bytes[position++].toInt() and 0xFF) }
        }
        val value = bytes.copyOfRange(position, position + length)
        position += length
        return value
    }
}

/** Strips the single leading 0x00 a DER INTEGER adds so its top bit does not read as a sign. */
private fun ByteArray.stripDerSignByte(): ByteArray =
    if (size > 1 && this[0] == 0.toByte() && (this[1].toInt() and 0x80) != 0) copyOfRange(1, size) else this

private fun ByteArray.leftPadTo(length: Int): ByteArray {
    require(size <= length) { "Value is longer than the requested $length bytes." }
    if (size == length) return this
    val padded = ByteArray(length)
    copyInto(padded, length - size)
    return padded
}

// 30 31 30 0d 06 09 60 86 48 01 65 03 04 02 01 05 00 04 20 -- the fixed DER DigestInfo prefix every
// PKCS#1 v1.5 SHA-256 signature uses (RFC 8017 Appendix A.2.4 / Section 9.2), ahead of the 32-byte
// hash itself.
private val SHA256_DIGEST_INFO_PREFIX = byteArrayOf(
    0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60.toByte(), 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04,
    0x02, 0x01, 0x05, 0x00, 0x04, 0x20,
)

/** EMSA-PKCS1-v1_5 encoding of SHA-256([message]) into [modulusByteLength] bytes, or null if too small. */
private fun pkcs1Sha256EncodedMessage(message: ByteArray, modulusByteLength: Int): ByteArray? {
    val digestInfo = SHA256_DIGEST_INFO_PREFIX + Sha256.digest(message)
    val paddingLength = modulusByteLength - 3 - digestInfo.size
    if (paddingLength < 8) return null

    val encoded = ByteArray(modulusByteLength)
    encoded[1] = 0x01
    for (i in 2 until 2 + paddingLength) encoded[i] = 0xFF.toByte()
    digestInfo.copyInto(encoded, 3 + paddingLength)
    return encoded
}

/** Binary modular exponentiation (square-and-multiply), reducing modulo [modulus] at every step. */
private fun modPow(base: BigInteger, exponent: BigInteger, modulus: BigInteger): BigInteger {
    if (modulus == BigInteger.ONE) return BigInteger.ZERO
    var result = BigInteger.ONE
    var currentBase = base.mod(modulus)
    var remainingExponent = exponent
    while (remainingExponent > BigInteger.ZERO) {
        if ((remainingExponent mod BigInteger.TWO) == BigInteger.ONE) {
            result = (result * currentBase).mod(modulus)
        }
        remainingExponent /= BigInteger.TWO
        currentBase = (currentBase * currentBase).mod(modulus)
    }
    return result
}
