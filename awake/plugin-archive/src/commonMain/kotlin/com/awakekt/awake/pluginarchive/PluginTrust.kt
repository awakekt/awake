/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

/** Whether a host may trust a plugin archive, and why ([PluginTrust.verify]). */
public sealed class PluginVerificationResult {
    /**
     * Signed by its declared publisher with one of the keys the [PublisherKeyList] holds for it, and
     * not revoked.
     *
     * @property publisherId The [PluginManifest.publisher] whose key verified the archive.
     */
    public data class VerifiedByPublisher(val publisherId: String) : PluginVerificationResult()

    /**
     * The archive cannot be attributed to a publisher: it carries no signature, names no publisher,
     * or names one the key list does not hold. A host decides whether to allow it (for example, a
     * plugin its user is developing locally).
     *
     * @property reason Human-readable explanation, for logs and host UI.
     */
    public data class Unsigned(val reason: String) : PluginVerificationResult()

    /**
     * The archive names a listed publisher but its signature fails under every key for it: tampered
     * contents, a corrupt signature, or a key the publisher does not hold. Never load it.
     *
     * @property reason Human-readable explanation, for logs and host UI.
     */
    public data class BadSignature(val reason: String) : PluginVerificationResult()

    /**
     * This exact plugin version is revoked. Never load it, however it is signed.
     *
     * @property revocation The revocation entry, with its reason and time.
     */
    public data class Revoked(val revocation: RevokedPluginVersion) : PluginVerificationResult()
}

/**
 * Decides whether a [PluginArchive] can be trusted, from the root-verified [PublisherKeyList] and
 * [PluginRevocationList] a host holds ([SignedPublisherKeyList.verify], [SignedPluginRevocationList.verify]).
 *
 * The revocation check comes first, so a revoked version reports [PluginVerificationResult.Revoked]
 * even when its signature is fine. [verify] suspends for the reason given on [PluginArchiveVerifier].
 */
public object PluginTrust {
    /** Checks [archive] against [publishers] and [revocations]. */
    public suspend fun verify(
        archive: PluginArchive,
        publishers: PublisherKeyList,
        revocations: PluginRevocationList = PluginRevocationList.EMPTY,
    ): PluginVerificationResult {
        val manifest = archive.manifest
        val revocation = revocations.revocationOf(manifest.id, manifest.version)
        val keys = publishers.keysFor(manifest.publisher)
        return when {
            revocation != null -> PluginVerificationResult.Revoked(revocation)
            archive.signature == null ->
                PluginVerificationResult.Unsigned("Archive carries no $PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME entry.")
            manifest.publisher.isBlank() ->
                PluginVerificationResult.Unsigned("The manifest names no publisher to check the signature against.")
            keys.isEmpty() ->
                PluginVerificationResult.Unsigned("Publisher '${manifest.publisher}' is not in the publisher key list.")
            else -> verifyWithKeys(archive, manifest.publisher, keys)
        }
    }

    private suspend fun verifyWithKeys(archive: PluginArchive, publisherId: String, keys: List<String>): PluginVerificationResult {
        val results = keys.map { PluginArchiveVerifier.verify(archive.entries, it) }
        return if (PluginArchiveVerification.Verified in results) {
            PluginVerificationResult.VerifiedByPublisher(publisherId)
        } else {
            val reasons = results.mapNotNull {
                when (it) {
                    is PluginArchiveVerification.Unverified -> it.reason
                    is PluginArchiveVerification.Unsigned -> it.reason
                    PluginArchiveVerification.Verified -> null
                }
            }
            PluginVerificationResult.BadSignature(reasons.distinct().joinToString(" "))
        }
    }
}
