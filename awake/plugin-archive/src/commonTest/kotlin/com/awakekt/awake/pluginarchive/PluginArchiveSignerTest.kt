/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * Signing on each target's own provider. [PluginArchiveVerifier], whose own fixtures were made with
 * openssl, is the independent check that what [PluginArchiveSigner] writes is a real signature.
 */
class PluginArchiveSignerTest {
    @Test
    fun aSignedArchiveVerifiesAndTrustsItsPublisher() = runTest {
        val signer = TestSigner.generate()
        val archive = PluginArchive.fromEntries(PluginArchiveSigner.signedEntries(weatherEntries(), signer.privateKeyPem))

        assertEquals(PluginArchiveVerification.Verified, PluginArchiveVerifier.verify(archive.entries, signer.publicKeyPem))
        assertEquals(
            PluginVerificationResult.VerifiedByPublisher("com.example.studio"),
            PluginTrust.verify(archive, PublisherKeyList(1, listOf(PublisherKeys("com.example.studio", listOf(signer.publicKeyPem))))),
        )
    }

    @Test
    fun signingIsDeterministicAndIgnoresAnOldSignature() = runTest {
        val signer = TestSigner.generate()
        val first = PluginArchiveSigner.signature(weatherEntries(), signer.privateKeyPem)
        val withStaleSignature = weatherEntries() + PluginArchiveEntry(PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME, byteArrayOf(1, 2, 3))

        // PKCS#1 v1.5 signatures are deterministic, so the stale entry must not change the bytes.
        assertContentEquals(first, PluginArchiveSigner.signature(withStaleSignature, signer.privateKeyPem))
        val resigned = PluginArchiveSigner.signedEntries(withStaleSignature, signer.privateKeyPem)
        assertEquals(1, resigned.count { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME })
        assertContentEquals(first, resigned.single { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }.content)
    }

    @Test
    fun acceptsTheKeyAsBareBase64Der() = runTest {
        val signer = TestSigner.generate()
        val bare = signer.privateKeyPem.lines().filterNot { it.startsWith("-----") }.joinToString("")

        assertContentEquals(
            PluginArchiveSigner.signature(weatherEntries(), signer.privateKeyPem),
            PluginArchiveSigner.signature(weatherEntries(), bare),
        )
    }

    @Test
    fun aKeyThatIsNotAPrivateKeyFails() = runTest {
        val signer = TestSigner.generate()

        assertFails { PluginArchiveSigner.signature(weatherEntries(), "not a key") }
        assertFails { PluginArchiveSigner.signature(weatherEntries(), signer.publicKeyPem) }
    }
}
