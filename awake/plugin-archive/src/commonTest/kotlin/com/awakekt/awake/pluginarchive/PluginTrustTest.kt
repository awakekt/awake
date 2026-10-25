/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginTrustTest {
    private val revoked = RevokedPluginVersion("com.example.weather", "1.0.0", "Broken.", "2026-10-25T00:00:00Z")

    private fun keysFor(vararg keys: String) = PublisherKeyList(1, listOf(PublisherKeys("com.example.studio", keys.toList())))

    @Test
    fun verifiesAnArchiveSignedByAnyKeyOfItsPublisher() = runTest {
        val old = TestSigner.generate()
        val current = TestSigner.generate()
        val archive = PluginArchive.fromEntries(current.signArchive(weatherEntries()))

        assertEquals(
            PluginVerificationResult.VerifiedByPublisher("com.example.studio"),
            PluginTrust.verify(archive, keysFor(old.publicKeyPem, current.publicKeyPem)),
        )
    }

    @Test
    fun revocationWinsOverAGoodSignature() = runTest {
        val signer = TestSigner.generate()
        val archive = PluginArchive.fromEntries(signer.signArchive(weatherEntries()))

        assertEquals(
            PluginVerificationResult.Revoked(revoked),
            PluginTrust.verify(archive, keysFor(signer.publicKeyPem), PluginRevocationList(1, listOf(revoked))),
        )
    }

    @Test
    fun aKeyThePublisherDoesNotHoldIsABadSignature() = runTest {
        val signer = TestSigner.generate()
        val stranger = TestSigner.generate()
        val archive = PluginArchive.fromEntries(stranger.signArchive(weatherEntries()))

        assertIs<PluginVerificationResult.BadSignature>(PluginTrust.verify(archive, keysFor(signer.publicKeyPem)))
    }

    @Test
    fun unattributableArchivesAreUnsigned() = runTest {
        val signer = TestSigner.generate()
        val unsigned = PluginArchive.fromEntries(weatherEntries())
        val unknownPublisher = PluginArchive.fromEntries(signer.signArchive(weatherEntries()))
        val noPublisher = PluginArchive.fromEntries(
            signer.signArchive(
                listOf(PluginArchiveEntry(PLUGIN_ARCHIVE_MANIFEST_ENTRY_NAME, """{"id":"a.b","name":"A","version":"1"}""".encodeToByteArray())),
            ),
        )

        assertIs<PluginVerificationResult.Unsigned>(PluginTrust.verify(unsigned, keysFor(signer.publicKeyPem)))
        assertIs<PluginVerificationResult.Unsigned>(PluginTrust.verify(unknownPublisher, PublisherKeyList(1)))
        assertIs<PluginVerificationResult.Unsigned>(PluginTrust.verify(noPublisher, keysFor(signer.publicKeyPem)))
    }
}
