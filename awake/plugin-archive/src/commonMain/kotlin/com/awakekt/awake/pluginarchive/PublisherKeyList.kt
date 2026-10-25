/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlinx.serialization.Serializable

/**
 * One publisher's public signing keys.
 *
 * @property id Reverse-domain publisher id, matched against [PluginManifest.publisher].
 * @property keys RSA public keys (PEM or base64 X.509 `SubjectPublicKeyInfo`) any of which may sign
 *   the publisher's archives; more than one while a key is being rotated.
 */
@Serializable
public data class PublisherKeys(val id: String, val keys: List<String>) {
    init {
        require(isPublisherId(id)) { "Publisher id must be a reverse-domain id (e.g. 'com.example'), was '$id'." }
    }
}

/**
 * The versioned list of publishers' public keys a host trusts, as the Awake root key signs it.
 *
 * JSON: `{"version": 3, "publishers": [{"id": "com.example", "keys": ["-----BEGIN PUBLIC KEY-----..."]}]}`.
 *
 * @property version Increases with every list the signer publishes, so a host can refuse to go back
 *   to an older one ([isNewerThan]).
 * @property publishers Each publisher's keys; an id appears at most once.
 */
@Serializable
public data class PublisherKeyList(val version: Long, val publishers: List<PublisherKeys> = emptyList()) {
    init {
        require(version >= 0) { "Publisher key list version must not be negative, was $version." }
        val duplicates = publishers.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Publisher ids appear more than once: ${duplicates.sorted()}." }
    }

    /** The keys [publisherId] signs with, or an empty list when the publisher is not listed. */
    public fun keysFor(publisherId: String): List<String> =
        publishers.firstOrNull { it.id == publisherId }?.keys.orEmpty()

    /** Whether this list replaces [other]: a host keeps the highest version it has verified. */
    public fun isNewerThan(other: PublisherKeyList): Boolean = version > other.version

    /** This list as the JSON a signer signs. */
    public fun toJson(): String = documentJson.encodeToString(serializer(), this)

    /** Parsing for [PublisherKeyList]. */
    public companion object {
        /** Parses an unsigned [PublisherKeyList] document; use [SignedPublisherKeyList.verify] for one from the network. */
        public fun fromJson(jsonText: String): PublisherKeyList = documentJson.decodeFromString(serializer(), jsonText)
    }
}

/**
 * A [PublisherKeyList] with the root key's signature, as served: `{"document": "...", "signature": "..."}`.
 *
 * @property document The exact JSON string the root key signed; parse it only through [verify].
 * @property signature Base64 `SHA256withRSA` signature over
 *   [AwakeRootSignature.signingPayload] in [AwakeRootSignature.Domain.PublisherKeyList].
 */
@Serializable
public data class SignedPublisherKeyList(val document: String, val signature: String) {
    /** Checks the signature against [rootPublicKeys], then parses [document]. Suspends; see [PluginArchiveVerifier]. */
    public suspend fun verify(rootPublicKeys: List<String>): RootSignedDocument<PublisherKeyList> =
        AwakeRootSignature.verify(
            AwakeRootSignature.Domain.PublisherKeyList,
            document,
            signature,
            rootPublicKeys,
            PublisherKeyList.serializer(),
        )

    /** This envelope as JSON. */
    public fun toJson(): String = documentJson.encodeToString(serializer(), this)

    /** Parsing for [SignedPublisherKeyList]. */
    public companion object {
        /** Parses the `{document, signature}` envelope; the document is not checked until [verify]. */
        public fun fromJson(jsonText: String): SignedPublisherKeyList = documentJson.decodeFromString(serializer(), jsonText)
    }
}
