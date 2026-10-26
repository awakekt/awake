/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AwakeRootSignatureTest {
    @Test
    fun verifiesAnOpensslSignedPublisherKeyList() = runTest {
        val result = SignedPublisherKeyList(PUBLISHER_LIST, PUBLISHER_LIST_SIGNATURE).verify(listOf(ROOT_PUBLIC_KEY))

        val list = assertIs<RootSignedDocument.Verified<PublisherKeyList>>(result).value
        assertEquals(3, list.version)
        assertEquals(listOf("MIIBIjAN-placeholder"), list.keysFor("com.example.studio"))
        assertEquals(emptyList(), list.keysFor("com.example.unknown"))
    }

    @Test
    fun verifiesAnOpensslSignedRevocationList() = runTest {
        val result = SignedPluginRevocationList(REVOCATION_LIST, REVOCATION_LIST_SIGNATURE).verify(listOf(ROOT_PUBLIC_KEY))

        val list = assertIs<RootSignedDocument.Verified<PluginRevocationList>>(result).value
        assertEquals(
            RevokedPluginVersion("com.example.weather", "1.0.0", "Leaks project files.", "2026-10-25T12:00:00Z"),
            list.revocationOf("com.example.weather", "1.0.0"),
        )
        assertEquals(null, list.revocationOf("com.example.weather", "1.0.1"))
    }

    @Test
    fun aSignatureForOneDomainDoesNotVerifyAnother() = runTest {
        assertFalse(
            AwakeRootSignature.isValid(
                AwakeRootSignature.Domain.PluginRevocationList,
                PUBLISHER_LIST,
                PUBLISHER_LIST_SIGNATURE,
                listOf(ROOT_PUBLIC_KEY),
            ),
        )
    }

    @Test
    fun anyOfSeveralRootKeysMayVerify() = runTest {
        val other = TestSigner.generate()

        val result = SignedPublisherKeyList(PUBLISHER_LIST, PUBLISHER_LIST_SIGNATURE)
            .verify(listOf(other.publicKeyPem, ROOT_PUBLIC_KEY))

        assertIs<RootSignedDocument.Verified<PublisherKeyList>>(result)
    }

    @Test
    fun rejectsAChangedDocumentAnUnknownKeyOrNoKey() = runTest {
        val changed = PUBLISHER_LIST.replace("\"version\":3", "\"version\":4")
        val other = TestSigner.generate()

        assertIs<RootSignedDocument.Rejected>(SignedPublisherKeyList(changed, PUBLISHER_LIST_SIGNATURE).verify(listOf(ROOT_PUBLIC_KEY)))
        assertIs<RootSignedDocument.Rejected>(
            SignedPublisherKeyList(PUBLISHER_LIST, PUBLISHER_LIST_SIGNATURE).verify(listOf(other.publicKeyPem)),
        )
        assertEquals(
            RootSignedDocument.Rejected("No root public key is trusted."),
            SignedPublisherKeyList(PUBLISHER_LIST, PUBLISHER_LIST_SIGNATURE).verify(emptyList()),
        )
        assertEquals(
            RootSignedDocument.Rejected("The signature is not base64."),
            SignedPublisherKeyList(PUBLISHER_LIST, "not base64!").verify(listOf(ROOT_PUBLIC_KEY)),
        )
    }

    @Test
    fun signsGeneratedDocumentsThatRoundTripThroughTheirEnvelopes() = runTest {
        val root = TestSigner.generate()
        val keys = PublisherKeyList(1, listOf(PublisherKeys("com.example.studio", listOf(root.publicKeyPem))))
        val revocations = PluginRevocationList(2, listOf(RevokedPluginVersion("com.example.weather", "1.0.0", "Broken.", "2026-10-25T00:00:00Z")))

        val signedKeys = SignedPublisherKeyList(
            keys.toJson(),
            root.signBase64(AwakeRootSignature.signingPayload(AwakeRootSignature.Domain.PublisherKeyList, keys.toJson())),
        )
        val signedRevocations = SignedPluginRevocationList(
            revocations.toJson(),
            root.signBase64(AwakeRootSignature.signingPayload(AwakeRootSignature.Domain.PluginRevocationList, revocations.toJson())),
        )

        val parsedKeys = SignedPublisherKeyList.fromJson(signedKeys.toJson())
        val parsedRevocations = SignedPluginRevocationList.fromJson(signedRevocations.toJson())
        assertEquals(RootSignedDocument.Verified(keys), parsedKeys.verify(listOf(root.publicKeyPem)))
        assertEquals(RootSignedDocument.Verified(revocations), parsedRevocations.verify(listOf(root.publicKeyPem)))
    }

    @Test
    fun aValidSignatureOverMalformedJsonIsRejected() = runTest {
        val root = TestSigner.generate()
        val document = """{"version":-1}"""
        val signature = root.signBase64(AwakeRootSignature.signingPayload(AwakeRootSignature.Domain.PublisherKeyList, document))

        assertTrue(AwakeRootSignature.isValid(AwakeRootSignature.Domain.PublisherKeyList, document, signature, listOf(root.publicKeyPem)))
        assertIs<RootSignedDocument.Rejected>(SignedPublisherKeyList(document, signature).verify(listOf(root.publicKeyPem)))
    }

    @Test
    fun envelopesAndListsKeepTheirJsonShapes() {
        assertEquals("""{"document":"{}","signature":"c2ln"}""", SignedPublisherKeyList("{}", "c2ln").toJson())
        assertEquals("""{"document":"{}","signature":"c2ln"}""", SignedPluginRevocationList("{}", "c2ln").toJson())
        assertEquals(PublisherKeyList.fromJson(PUBLISHER_LIST).toJson(), PUBLISHER_LIST)
        assertEquals(PluginRevocationList.fromJson(REVOCATION_LIST).toJson(), REVOCATION_LIST)
        assertTrue(PluginRevocationList(8).isNewerThan(PluginRevocationList.fromJson(REVOCATION_LIST)))
    }

    private companion object {
        // Generated once with openssl: a 2048-bit root key (`openssl genpkey`, `openssl rsa -pubout`)
        // and `openssl dgst -sha256 -sign` over "<domain tag>\n<document>" for each document below.
        // No private key material is kept.
        const val ROOT_PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAob9QiSm6SG2C++RZYC4k
ZbVbuYQZllEU7ZzmUOgvRyxCH3QS96ifc4pDGwZ9cdZn1Xe+GTbL1BmZxCieYmxI
ay+u9qZwI/w6aRixGe68jMhpCKRenW4l4xXqyaIPUdgWOiJZdmd/n4AaFbgOZmtm
+fXtutLfGuQinxAlcKZ4x81XAQBKMr7XS5XJoqe3mK8ALQWwqBXQW1N8EbfGcDkz
MpomGKYvbAsdoi2S4twhFo3OSr8+SE2g9kSuis2ZU+qAm4gFqBWBijURn+vdi+1l
9+leV2iMgTAvIHeaYYdLx/euA7EaXe0gGuXHsktqo+bpq65IHAcaHeRSiRmd+8uX
cwIDAQAB
-----END PUBLIC KEY-----"""

        const val PUBLISHER_LIST = """{"version":3,"publishers":[{"id":"com.example.studio","keys":["MIIBIjAN-placeholder"]}]}"""
        const val PUBLISHER_LIST_SIGNATURE = "S72th341W0WTewMWAS5z3mGopKz1xmZx5YzhqQ2zn33Fw1FU7ZYWSnHYdv8f19XOwnM5A1CMwIjboiYbEBJn1Sg61VRng1peLS6yYgk7U/WcmARwqVHlFnwUOTB2RjqYDklmW2dcch5AlAKPrXjotUIvlzd3HqUJmf7QrbxyWzdsh75+14t7H+hFtGeN7W+WSyebgkSc9fKqkAsXLHQ2YmAGFuDNY1MmpKtjTaM72dyeoHl+DVVy86vBNUz6ZsGY2MHDjG8mUIrt1bsmaDJu7kvqLdLX/jDf+tWtvxRYVB7Tw/+1ItxSHE47OctfYomvJ25cpp/lSGrsNkwWL61j9w=="

        const val REVOCATION_LIST =
            """{"version":7,"revoked":[{"id":"com.example.weather","version":"1.0.0","reason":"Leaks project files.","revokedAt":"2026-10-25T12:00:00Z"}]}"""
        const val REVOCATION_LIST_SIGNATURE = "c1ktIrVxFsuOKjP3V7nI+mEfvUgpFyG8Ua0hudXaxjp6z2zfaZWMlsOw6zGrS39SfhqKxe03LdWcumHS9xwaCq4O5xE2uywIxIdd1867Q41XfYPx1pcgUjrAllh6cVskdaJPBevLvDOxyO00VrQUakxKp1jp2dIEr9QNrMb0bzH6hHk3aXLvCcqUfoigFqGtc297wmMRYtV9mTHKsergGu8gwP4VZ+fpwB3Eyl/GzciJKtKiUGxodb+dvdAOTowUsmQG7oPQAijtjIXxPyUeRwDDDfEqpCKPwObUBZ8YzXAnTDBSGJxo5Wj5/Iv8JUJr/eNYhyStaJtD3X1vmXdz8g=="
    }
}
