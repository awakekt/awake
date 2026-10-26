/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * The publisher side of [PluginArchiveVerifier]: signs an archive's entries so the signing path and
 * the checking path share one digest and payload.
 *
 * The signature is RSASSA-PKCS1-v1_5 / SHA-256 (`SHA256withRSA`) over
 * [PluginArchiveDigest.signingPayload] of the entries' canonical digest, made with the publisher's
 * RSA private key as PKCS#8, PEM or bare base64 DER (what `openssl genpkey` writes). It runs on every
 * target's provider and suspends for the reason given on [PluginArchiveVerifier]. Signing keys belong
 * on a publisher's machine or a signing service, never in a shipped app.
 *
 * On the desktop JVM, `PluginArchiveSigner.sign` rewrites a whole archive and `PluginArchiveSignerMain`
 * signs a file from the command line; `templates/plugin`'s `signAwakePlugin` task runs the latter.
 */
public object PluginArchiveSigner {
    /** The `signature.sig` contents for [entries] (any existing signature entry is ignored) under [privateKey]. */
    public suspend fun signature(entries: List<PluginArchiveEntry>, privateKey: String): ByteArray {
        val payload = PluginArchiveDigest.signingPayload(PluginArchiveDigest.canonicalDigest(entries))
        return CryptographyProvider.Default.get(RSA.PKCS1)
            .privateKeyDecoder(SHA256)
            .decodeFromByteArray(RSA.PrivateKey.Format.DER, pemOrBase64Der(privateKey))
            .signatureGenerator()
            .generateSignature(payload)
    }

    /** [entries] without any old signature entry, plus a new [PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME] entry under [privateKey]. */
    public suspend fun signedEntries(entries: List<PluginArchiveEntry>, privateKey: String): List<PluginArchiveEntry> {
        val unsigned = entries.filterNot { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }
        return unsigned + PluginArchiveEntry(PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME, signature(unsigned, privateKey))
    }
}
