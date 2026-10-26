/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlinx.serialization.Serializable

/**
 * One plugin version a host must no longer load.
 *
 * @property id The plugin's [PluginManifest.id].
 * @property version The exact [PluginManifest.version] revoked; other versions are unaffected.
 * @property reason Human-readable explanation, for logs and host UI.
 * @property revokedAt When it was revoked, as an ISO-8601 UTC timestamp (e.g. "2026-10-25T12:00:00Z").
 */
@Serializable
public data class RevokedPluginVersion(val id: String, val version: String, val reason: String, val revokedAt: String) {
    init {
        require(id.isNotBlank()) { "Revoked plugin id must not be blank." }
        require(version.isNotBlank()) { "Revoked plugin version must not be blank." }
    }
}

/**
 * The versioned list of revoked plugin versions, as the Awake root key signs it.
 *
 * JSON: `{"version": 7, "revoked": [{"id": "com.example.weather", "version": "1.0.0", "reason": "...", "revokedAt": "..."}]}`.
 *
 * @property version Increases with every list the signer publishes, so a host can refuse to go back
 *   to an older one, which would un-revoke a plugin ([isNewerThan]).
 * @property revoked The revoked plugin versions.
 */
@Serializable
public data class PluginRevocationList(val version: Long, val revoked: List<RevokedPluginVersion> = emptyList()) {
    init {
        require(version >= 0) { "Plugin revocation list version must not be negative, was $version." }
    }

    /** The revocation for [id] at [version], or null when that version is not revoked. */
    public fun revocationOf(id: String, version: String): RevokedPluginVersion? =
        revoked.firstOrNull { it.id == id && it.version == version }

    /** Whether this list replaces [other]: a host keeps the highest version it has verified. */
    public fun isNewerThan(other: PluginRevocationList): Boolean = version > other.version

    /** This list as the JSON a signer signs. */
    public fun toJson(): String = documentJson.encodeToString(serializer(), this)

    /** Parsing for [PluginRevocationList], and the empty list. */
    public companion object {
        /** A list revoking nothing, for a host that has not fetched one yet. */
        public val EMPTY: PluginRevocationList = PluginRevocationList(version = 0)

        /** Parses an unsigned [PluginRevocationList] document; use [SignedPluginRevocationList.verify] for one from the network. */
        public fun fromJson(jsonText: String): PluginRevocationList = documentJson.decodeFromString(serializer(), jsonText)
    }
}

/**
 * A [PluginRevocationList] with the root key's signature, as served: `{"document": "...", "signature": "..."}`.
 *
 * @property document The exact JSON string the root key signed; parse it only through [verify].
 * @property signature Base64 `SHA256withRSA` signature over
 *   [AwakeRootSignature.signingPayload] in [AwakeRootSignature.Domain.PluginRevocationList].
 */
@Serializable
public data class SignedPluginRevocationList(val document: String, val signature: String) {
    /** Checks the signature against [rootPublicKeys], then parses [document]. Suspends; see [PluginArchiveVerifier]. */
    public suspend fun verify(rootPublicKeys: List<String>): RootSignedDocument<PluginRevocationList> =
        AwakeRootSignature.verify(
            AwakeRootSignature.Domain.PluginRevocationList,
            document,
            signature,
            rootPublicKeys,
            PluginRevocationList.serializer(),
        )

    /** This envelope as JSON. */
    public fun toJson(): String = documentJson.encodeToString(serializer(), this)

    /** Parsing for [SignedPluginRevocationList]. */
    public companion object {
        /** Parses the `{document, signature}` envelope; the document is not checked until [verify]. */
        public fun fromJson(jsonText: String): SignedPluginRevocationList = documentJson.decodeFromString(serializer(), jsonText)
    }
}
