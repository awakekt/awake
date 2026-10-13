/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Signs with a freshly generated `java.security` RSA key pair, the same way the signing task in
 * `templates/plugin/plugin/build.gradle.kts` signs: `SHA256withRSA` over
 * [PluginArchiveDigest.signingPayload]. [PluginArchiveVerifierTest] covers the pure-Kotlin verifier
 * against a fixture no JVM code in this module produced; this proves the two sides round-trip.
 */
class PluginArchiveSigningDesktopTest {
    private fun entries() = listOf(
        PluginArchiveEntry("plugin.json", """{"id":"com.example.weather"}""".encodeToByteArray()),
        PluginArchiveEntry("lib/plugin.jar", "jar-bytes".encodeToByteArray()),
    )

    private fun sign(entries: List<PluginArchiveEntry>, privateKey: java.security.PrivateKey): PluginArchiveEntry {
        val digest = PluginArchiveDigest.canonicalDigest(entries)
        val payload = PluginArchiveDigest.signingPayload(digest)
        val signatureBytes = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(payload)
            sign()
        }
        return PluginArchiveEntry(PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME, signatureBytes)
    }

    private fun PublicKey.toPem(): String {
        val body = Base64.getEncoder().encodeToString(encoded).chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----"
    }

    @Test
    fun signsThenVerifiesSuccessfully() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val signed = entries() + sign(entries(), keyPair.private)

        val result = PluginArchiveVerifier.verify(signed, keyPair.public.toPem())

        assertIs<PluginArchiveVerification.Verified>(result)
    }

    @Test
    fun aValidSignatureFromAKeyShorterThan2048BitsIsRefused() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.genKeyPair()
        val signed = entries() + sign(entries(), keyPair.private)

        val result = PluginArchiveVerifier.verify(signed, keyPair.public.toPem())

        assertIs<PluginArchiveVerification.Unverified>(result)
    }

    @Test
    fun aChangedEntryFailsVerification() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val signature = sign(entries(), keyPair.private)
        val tampered = listOf(
            PluginArchiveEntry("plugin.json", """{"id":"com.example.weather"}""".encodeToByteArray()),
            PluginArchiveEntry("lib/plugin.jar", "tampered-bytes".encodeToByteArray()),
            signature,
        )

        val result = PluginArchiveVerifier.verify(tampered, keyPair.public.toPem())

        assertIs<PluginArchiveVerification.Unverified>(result)
    }

    @Test
    fun aDifferentKeyFailsVerification() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val otherKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val signed = entries() + sign(entries(), keyPair.private)

        val result = PluginArchiveVerifier.verify(signed, otherKeyPair.public.toPem())

        assertIs<PluginArchiveVerification.Unverified>(result)
    }

    @Test
    fun unsignedPackagingIsFlaggedRatherThanAccepted() {
        val result = PluginArchiveVerifier.verify(entries(), "unused")

        assertIs<PluginArchiveVerification.Unsigned>(result)
    }
}
