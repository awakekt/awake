/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PluginArchiveSignerTest {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
    private val privatePem = pem("PRIVATE KEY", keyPair.private.encoded)
    private val publicPem = pem("PUBLIC KEY", keyPair.public.encoded)

    private fun pem(label: String, der: ByteArray): String =
        "-----BEGIN $label-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) + "\n-----END $label-----"

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("lib/"))
            zip.closeEntry()
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun aSignedArchiveVerifiesAndTrustsItsPublisher() = runTest {
        val signed = PluginArchiveSigner.sign(zip("lib/plugin.jar" to "jar-bytes", "plugin.json" to WEATHER_MANIFEST_JSON), privatePem)

        val archive = PluginArchiveReader.read(signed)
        assertEquals(listOf("lib/plugin.jar", "plugin.json", "signature.sig"), archive.entries.map { it.name })
        assertEquals(PluginArchiveVerification.Verified, PluginArchiveVerifier.verify(archive.entries, publicPem))
        assertEquals(
            PluginVerificationResult.VerifiedByPublisher("com.example.studio"),
            PluginTrust.verify(archive, PublisherKeyList(1, listOf(PublisherKeys("com.example.studio", listOf(publicPem))))),
        )
    }

    @Test
    fun matchesJavaSecuritySha256WithRsaOverTheSigningPayload() = runTest {
        val entries = weatherEntries()
        val payload = PluginArchiveDigest.signingPayload(PluginArchiveDigest.canonicalDigest(entries))
        val expected = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(payload)
            sign()
        }

        // PKCS#1 v1.5 signatures are deterministic, so the two must be byte-identical.
        assertContentEquals(expected, PluginArchiveSigner.signature(entries, privatePem))
        assertContentEquals(expected, PluginArchiveSigner.signature(entries, privatePem.lines().filterNot { it.startsWith("-----") }.joinToString("")))
    }

    @Test
    fun resigningReplacesTheOldSignature() = runTest {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val first = PluginArchiveSigner.sign(zip("plugin.json" to WEATHER_MANIFEST_JSON), pem("PRIVATE KEY", other.private.encoded))

        val archive = PluginArchiveReader.read(PluginArchiveSigner.sign(first, privatePem))

        assertEquals(1, archive.entries.count { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME })
        assertEquals(PluginArchiveVerification.Verified, PluginArchiveVerifier.verify(archive.entries, publicPem))
    }

    @Test
    fun refusesToSignAnArchiveAHostWouldRefuse() {
        assertFailsWith<PluginArchiveException> { PluginArchiveSigner.sign(zip("../evil" to "x", "plugin.json" to WEATHER_MANIFEST_JSON), privatePem) }
        assertFailsWith<PluginArchiveException> { PluginArchiveSigner.sign(zip("lib/plugin.jar" to "x"), privatePem) }
    }
}
