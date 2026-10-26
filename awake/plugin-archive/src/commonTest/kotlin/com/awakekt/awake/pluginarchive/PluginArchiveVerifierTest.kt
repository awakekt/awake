/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The fixture was generated once with real `openssl` RSA key pairs, so this test checks
 * [PluginArchiveVerifier] against a signature no part of this module produced, on every target.
 * It is the same fixture the verifier carried in `:awake:editor:contract`, so archives signed before
 * the move still verify. The other tests sign with a key generated on the target at test time.
 */
class PluginArchiveVerifierTest {
    private val manifestBytes = """{"id":"com.example.weather","name":"Weather","version":"1.0.0"}"""
        .encodeToByteArray()
    private val jarBytes = "FAKE-JAR-BYTES-FOR-TEST-FIXTURE".encodeToByteArray()

    private fun fixtureEntries(signature: ByteArray = Base64.decode(FIXTURE_SIGNATURE_BASE64)) = listOf(
        PluginArchiveEntry("plugin.json", manifestBytes),
        PluginArchiveEntry("lib/plugin.jar", jarBytes),
        PluginArchiveEntry(PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME, signature),
    )

    @Test
    fun verifiesASignatureFromAnIndependentRsaKeyPair() = runTest {
        assertIs<PluginArchiveVerification.Verified>(PluginArchiveVerifier.verify(fixtureEntries(), FIXTURE_PUBLIC_KEY_PEM))
    }

    @Test
    fun acceptsTheKeyAsBareBase64Der() = runTest {
        val bare = FIXTURE_PUBLIC_KEY_PEM.lines().filterNot { it.startsWith("-----") }.joinToString("")

        assertIs<PluginArchiveVerification.Verified>(PluginArchiveVerifier.verify(fixtureEntries(), bare))
    }

    @Test
    fun rejectsAChangedEntry() = runTest {
        val tampered = fixtureEntries().map {
            if (it.name == "lib/plugin.jar") PluginArchiveEntry(it.name, "TAMPERED-BYTES".encodeToByteArray()) else it
        }

        assertIs<PluginArchiveVerification.Unverified>(PluginArchiveVerifier.verify(tampered, FIXTURE_PUBLIC_KEY_PEM))
    }

    @Test
    fun rejectsAnotherPublishersKey() = runTest {
        assertIs<PluginArchiveVerification.Unverified>(PluginArchiveVerifier.verify(fixtureEntries(), OTHER_PUBLIC_KEY_PEM))
    }

    @Test
    fun rejectsAGarbageKeyOrSignatureWithoutThrowing() = runTest {
        assertIs<PluginArchiveVerification.Unverified>(PluginArchiveVerifier.verify(fixtureEntries(), "not a key"))
        assertIs<PluginArchiveVerification.Unverified>(
            PluginArchiveVerifier.verify(fixtureEntries(signature = byteArrayOf(1, 2, 3)), FIXTURE_PUBLIC_KEY_PEM),
        )
    }

    @Test
    fun flagsAnArchiveWithNoSignatureEntryAsUnsigned() = runTest {
        val unsigned = fixtureEntries().filterNot { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }

        assertIs<PluginArchiveVerification.Unsigned>(PluginArchiveVerifier.verify(unsigned, FIXTURE_PUBLIC_KEY_PEM))
    }

    @Test
    fun signsWithAGeneratedKeyThenVerifies() = runTest {
        val signer = TestSigner.generate()

        assertIs<PluginArchiveVerification.Verified>(
            PluginArchiveVerifier.verify(signer.signArchive(weatherEntries()), signer.publicKeyPem),
        )
    }

    @Test
    fun aValidSignatureFromAKeyShorterThan2048BitsIsRefused() = runTest {
        val signer = TestSigner.generate(bits = 1024)

        val result = PluginArchiveVerifier.verify(signer.signArchive(weatherEntries()), signer.publicKeyPem)

        assertEquals(PluginArchiveVerification.Unverified("RSA key is shorter than 2048 bits."), result)
    }

    @Test
    fun canonicalDigestDoesNotDependOnEntryOrderOrTheSignatureEntry() = runTest {
        val forward = weatherEntries()
        val reversedWithSignature = forward.reversed() + PluginArchiveEntry(PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME, byteArrayOf(1))

        assertEquals(
            PluginArchiveDigest.canonicalDigest(forward).toHex(),
            PluginArchiveDigest.canonicalDigest(reversedWithSignature).toHex(),
        )
    }

    @Test
    fun canonicalDigestMatchesItsPinnedValue() = runTest {
        // sha256 over: be32(14) "lib/plugin.jar" be32(9) "jar-bytes" be32(11) "plugin.json" be32(2) "{}",
        // computed independently with Python's hashlib. templates/plugin's signing task must agree.
        val entries = listOf(
            PluginArchiveEntry("plugin.json", "{}".encodeToByteArray()),
            PluginArchiveEntry("lib/plugin.jar", "jar-bytes".encodeToByteArray()),
        )

        assertEquals(PINNED_DIGEST_HEX, PluginArchiveDigest.canonicalDigest(entries).toHex())
        assertEquals(
            "awake-plugin-archive-v1\n$PINNED_DIGEST_HEX",
            PluginArchiveDigest.signingPayload(PluginArchiveDigest.canonicalDigest(entries)).decodeToString(),
        )
    }

    private companion object {
        const val PINNED_DIGEST_HEX = "8fd50a87edacd3a11ee603230e9cfb4325d34c684e351c11007381364f09dac6"

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
