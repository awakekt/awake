/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Fixture generated once with real `openssl` RSA key pairs (not java.security), so this test
 * exercises [PluginArchiveVerifier] against a signature no part of this module produced, on every
 * target including wasmJs and iOS. [PluginArchiveSigningDesktopTest] additionally signs and
 * verifies with a freshly generated key at test time, matching the Gradle signing task's own path.
 */
class PluginArchiveVerifierTest {
    private val manifestBytes = """{"id":"com.example.weather","name":"Weather","version":"1.0.0"}"""
        .encodeToByteArray()
    private val jarBytes = "FAKE-JAR-BYTES-FOR-TEST-FIXTURE".encodeToByteArray()

    private fun signedEntries() = listOf(
        PluginArchiveEntry("plugin.json", manifestBytes),
        PluginArchiveEntry("lib/plugin.jar", jarBytes),
        PluginArchiveEntry(PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME, decodeBase64(FIXTURE_SIGNATURE_BASE64)),
    )

    @Test
    fun verifiesASignatureFromAnIndependentRsaKeyPair() {
        val result = PluginArchiveVerifier.verify(signedEntries(), FIXTURE_PUBLIC_KEY_PEM)

        assertIs<PluginArchiveVerification.Verified>(result)
    }

    @Test
    fun rejectsAChangedEntry() {
        val tampered = listOf(
            PluginArchiveEntry("plugin.json", manifestBytes),
            PluginArchiveEntry("lib/plugin.jar", "TAMPERED-BYTES".encodeToByteArray()),
            PluginArchiveEntry(PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME, decodeBase64(FIXTURE_SIGNATURE_BASE64)),
        )

        val result = PluginArchiveVerifier.verify(tampered, FIXTURE_PUBLIC_KEY_PEM)

        assertIs<PluginArchiveVerification.Unverified>(result)
    }

    @Test
    fun rejectsAnotherPublishersKey() {
        val result = PluginArchiveVerifier.verify(signedEntries(), OTHER_PUBLIC_KEY_PEM)

        assertIs<PluginArchiveVerification.Unverified>(result)
    }

    @Test
    fun flagsAnArchiveWithNoSignatureEntryAsUnsigned() {
        val unsigned = listOf(
            PluginArchiveEntry("plugin.json", manifestBytes),
            PluginArchiveEntry("lib/plugin.jar", jarBytes),
        )

        val result = PluginArchiveVerifier.verify(unsigned, FIXTURE_PUBLIC_KEY_PEM)

        assertIs<PluginArchiveVerification.Unsigned>(result)
    }

    @Test
    fun canonicalDigestDoesNotDependOnEntryOrder() {
        val forward = listOf(
            PluginArchiveEntry("plugin.json", manifestBytes),
            PluginArchiveEntry("lib/plugin.jar", jarBytes),
        )
        val reversed = forward.reversed()

        assertEquals(
            PluginArchiveDigest.canonicalDigest(forward).toList(),
            PluginArchiveDigest.canonicalDigest(reversed).toList(),
        )
    }

    @Test
    fun canonicalDigestIgnoresTheSignatureEntryItself() {
        val withoutSignature = listOf(PluginArchiveEntry("plugin.json", manifestBytes))
        val withSignature = withoutSignature + PluginArchiveEntry(PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME, byteArrayOf(1, 2, 3))

        assertEquals(
            PluginArchiveDigest.canonicalDigest(withoutSignature).toList(),
            PluginArchiveDigest.canonicalDigest(withSignature).toList(),
        )
    }

    private companion object {
        @OptIn(ExperimentalEncodingApi::class)
        fun decodeBase64(value: String): ByteArray = Base64.decode(value)

        // Generated once with `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048` and
        // `openssl rsa -pubout`; FIXTURE_SIGNATURE_BASE64 is `openssl dgst -sha256 -sign` over
        // PluginArchiveDigest.signingPayload(canonicalDigest(entries)) for the two entries above,
        // in `plugin.json`, `lib/plugin.jar` order. No private key material is kept.
        const val FIXTURE_PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0u/kNJRKjC1c5nVEHTOR
GGmOanHmdM35Bp4VSg4Hd22c2knPpHSWS9lB6uUtUJMBsDI3F0Ks4ne6FSZyiy2/
RbFS5QWiHGFbG4Njy/AIR4XyI5qw2bjtp1otWuZ8hZFI16JOo23ZC8Mir9cfQyr1
yp7y8VHY9+by+IBO6yaurtIWNkSvRhiwjnwPCuXCEaeARWDtBweyqeo9O0RR8qDe
yvpLTzx6U4xDaM2jrwLbOLJ39ZvJM5pQ7/mo/hXF/ueGOQMBcvLzjFk+UvI31SJ4
wF1JUlTCTut2nlu2IT1aZpzePqWaT8BWdbv3bJ5vsPRniobrwBAsJ/+cxjI+vvFa
JwIDAQAB
-----END PUBLIC KEY-----"""

        const val OTHER_PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwxgGisc4fxEuKJ4TP6RV
hERqbeZ2UKmOnIV5eEIOtBCfF7Yz7zLRb3/uYzbIKgbJI4FRTWktm8sRbWfcIqme
/dRpZAEiG0A/ib4YQtaM0tzKuRAJPjTz1MezaDG+xpQ65sxqM0Gf9RFIG8j/oGQ4
2zVRr9eROvCsQ/aZPL61uep7Kv+7rMS/M7ESG5BChkD/M7uc9FArs7hJz5JB+a8P
yZFVdC+caHVkQSU7dldRAep73aFQORm4RNHsmyDrYVAyRO4tbSEQOwoQ2GdqXeG7
GefOPx6ABxsvPaw34H6Xd44qujrs5og51Vfe/MmeBMI38vjtt/MdZ6mZLRSrH45V
qQIDAQAB
-----END PUBLIC KEY-----"""

        const val FIXTURE_SIGNATURE_BASE64 =
            "NB4TH5RN6HLks5anSFhbMu90CXnPUXLQVjKs4TSMR6AL/U3HSjwBdZ7aIc+lOy86I1oGKgbYXNpFv9ID2aKj2YO5qwUiSJzPFRUOCWMsPiACwtxnewOG2uccm7Wvuuq66hHdDk/ss4sBlg9LuYyy6zNalKDZXPpdahnZB2YpfmQ2lKYhabhoduBOVHXMsw7oSEHroombTABZCCKKAjf38NE7LKoEYRdDMOtfnGR2XsSOvhoYW35dqcbR13L0ZUF8HV4r83iTq3otow97Z48/otwMj57b8lHJph4vmTrxruszRAc+oDoo9bcuYmPJtu3T6CjytEQVLdAEOyrnRli8kQ=="
    }
}
