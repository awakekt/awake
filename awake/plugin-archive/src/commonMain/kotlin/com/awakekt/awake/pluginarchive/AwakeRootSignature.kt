/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Outcome of checking a document the Awake root key signs ([SignedPublisherKeyList.verify],
 * [SignedPluginRevocationList.verify]).
 *
 * @param T The document type.
 */
public sealed class RootSignedDocument<out T> {
    /**
     * The signature checks out under one of the root keys and the document parsed.
     *
     * @param T The document type.
     * @property value The parsed document.
     */
    public data class Verified<out T>(val value: T) : RootSignedDocument<T>()

    /**
     * The document must not be used: a bad or missing signature, an unknown key, or malformed JSON.
     *
     * @property reason Human-readable explanation, for logs and host UI.
     */
    public data class Rejected(val reason: String) : RootSignedDocument<Nothing>()
}

/**
 * The Awake root key's signature domains, and verification of the documents it signs.
 *
 * A root-signed document travels as `{"document": "...", "signature": "..."}`
 * ([SignedPublisherKeyList], [SignedPluginRevocationList]): `document` is the exact JSON string
 * that was signed, kept as a string so no re-encoding can change its bytes, and `signature` is the
 * base64 RSASSA-PKCS1-v1_5 / SHA-256 (`SHA256withRSA`) signature over [signingPayload], the
 * document's [Domain.tag], a newline, and the document's UTF-8 bytes. The domain tag stops a
 * signature made for one kind of document from verifying as another, the way
 * [PluginArchiveDigest.signingPayload] already prefixes an archive digest.
 *
 * Verification needs only public keys. Hosts pass the root public keys they trust (PEM or base64
 * X.509 `SubjectPublicKeyInfo`, 2048 bits or more), more than one while a key is being rotated; the
 * private key never leaves the signing service. [verify] suspends for the reason given on
 * [PluginArchiveVerifier].
 */
public object AwakeRootSignature {
    /**
     * What a signature covers. Each tag is versioned: a change to a document's meaning takes a new tag.
     *
     * @property tag The line that opens the signed payload.
     */
    public enum class Domain(public val tag: String) {
        /** A publisher's signature over a `.awakeplugin` archive digest ([PluginArchiveDigest.signingPayload]). */
        PluginArchive("awake-plugin-archive-v1"),

        /** The root key's signature over a [PublisherKeyList]. */
        PublisherKeyList("awake-publisher-key-list-v1"),

        /** The root key's signature over a [PluginRevocationList]. */
        PluginRevocationList("awake-plugin-revocation-list-v1"),
    }

    /** The bytes a root signature over [document] in [domain] covers. */
    public fun signingPayload(domain: Domain, document: String): ByteArray =
        (domain.tag + "\n" + document).encodeToByteArray()

    /**
     * Whether [signature] (base64) is a valid root signature over [document] in [domain] under any of
     * [rootPublicKeys].
     */
    public suspend fun isValid(domain: Domain, document: String, signature: String, rootPublicKeys: List<String>): Boolean =
        firstFailure(domain, document, signature, rootPublicKeys) == null

    internal suspend fun <T> verify(
        domain: Domain,
        document: String,
        signature: String,
        rootPublicKeys: List<String>,
        serializer: KSerializer<T>,
    ): RootSignedDocument<T> {
        firstFailure(domain, document, signature, rootPublicKeys)?.let { return RootSignedDocument.Rejected(it) }
        return try {
            RootSignedDocument.Verified(documentJson.decodeFromString(serializer, document))
        } catch (error: IllegalArgumentException) {
            RootSignedDocument.Rejected("The signed document is not a valid ${domain.tag} document: ${error.message}")
        }
    }

    /** Null when some key accepts the signature, otherwise why none did. */
    private suspend fun firstFailure(domain: Domain, document: String, signature: String, rootPublicKeys: List<String>): String? {
        val signatureBytes = decodeBase64OrNull(signature)
        return when {
            rootPublicKeys.isEmpty() -> "No root public key is trusted."
            signatureBytes == null -> "The signature is not base64."
            else -> {
                val payload = signingPayload(domain, document)
                val checks = rootPublicKeys.map { checkRsaSha256(payload, signatureBytes, it) }
                if (RsaCheck.Valid in checks) {
                    null
                } else {
                    checks.filterIsInstance<RsaCheck.Invalid>().map { it.reason }.distinct().joinToString(" ")
                }
            }
        }
    }
}

/** Reads signed documents: tolerant of fields a newer signer adds. */
internal val documentJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
