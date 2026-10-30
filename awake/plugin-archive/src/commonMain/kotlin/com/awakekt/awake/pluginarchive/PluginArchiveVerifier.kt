/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

/** Result of checking a `.awakeplugin` archive's signature against a publisher's public key. */
public sealed class PluginArchiveVerification {
    /** The archive's [PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME] matches its contents under the given key. */
    public data object Verified : PluginArchiveVerification()

    /**
     * No [PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME] entry: the archive was packaged without a signing key.
     *
     * @property reason Human-readable explanation, for logs and host UI.
     */
    public data class Unsigned(val reason: String) : PluginArchiveVerification()

    /**
     * A signature entry exists but does not check out: tampered contents, corrupt data, or the wrong key.
     *
     * @property reason Human-readable explanation, for logs and host UI.
     */
    public data class Unverified(val reason: String) : PluginArchiveVerification()
}

/**
 * Checks a `.awakeplugin` archive's RSASSA-PKCS1-v1_5 / SHA-256 signature (Java's `SHA256withRSA`)
 * over [PluginArchiveDigest.signingPayload] of its canonical digest. `templates/plugin`'s packaging
 * task signs that payload with the publisher's private key; the public key here is that key pair's
 * public half, an X.509 `SubjectPublicKeyInfo` in PEM or base64 DER (what `openssl rsa -pubout`
 * writes). Keys shorter than 2048 bits are refused. A host maps a plugin's
 * [PluginManifest.publisher] to its keys with a [PublisherKeyList]; [PluginTrust] does that lookup
 * and the revocation check together.
 *
 * The check runs on each platform's own provider through cryptography-kotlin: the JDK on desktop
 * and Android, Apple's Security framework on iOS, and WebCrypto in the browser. WebCrypto only
 * offers asynchronous calls, so [verify] is `suspend` on every target:
 * - Desktop and Android: call it from a coroutine on a background dispatcher
 *   (`withContext(Dispatchers.Default) { ... }`); a blocking caller with no coroutine can wrap it
 *   in `runBlocking`, never on the UI thread.
 * - iOS: call it from a Kotlin coroutine; Swift callers see it as an `async` function (or a
 *   completion handler) through the Kotlin/Native Objective-C export.
 * - wasmJs: call it from a coroutine (for example `MainScope().launch { ... }`). There is no
 *   blocking form in the browser, and none is offered.
 */
public object PluginArchiveVerifier {
    /** Checks [archiveEntries] (one of which should be `signature.sig`) against [publicKeyPem]. */
    public suspend fun verify(archiveEntries: List<PluginArchiveEntry>, publicKeyPem: String): PluginArchiveVerification {
        val signatureEntry = archiveEntries.find { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }
            ?: return PluginArchiveVerification.Unsigned(
                "Archive carries no $PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME entry.",
            )
        val payload = PluginArchiveDigest.signingPayload(PluginArchiveDigest.canonicalDigest(archiveEntries))
        return when (val check = checkRsaSha256(payload, signatureEntry.content, publicKeyPem)) {
            RsaCheck.Valid -> PluginArchiveVerification.Verified
            is RsaCheck.Invalid -> PluginArchiveVerification.Unverified(check.reason)
        }
    }
}
