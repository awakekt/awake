/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.io.encoding.Base64

/** An RSA key pair generated at test time on the target's own provider, signing as `SHA256withRSA`. */
internal class TestSigner private constructor(
    private val keyPair: RSA.PKCS1.KeyPair,
    val publicKeyPem: String,
    val privateKeyPem: String,
) {
    suspend fun sign(payload: ByteArray): ByteArray = keyPair.privateKey.signatureGenerator().generateSignature(payload)

    suspend fun signBase64(payload: ByteArray): String = Base64.encode(sign(payload))

    /** [entries] plus the `signature.sig` entry a packaging task would add, made by [PluginArchiveSigner]. */
    suspend fun signArchive(entries: List<PluginArchiveEntry>): List<PluginArchiveEntry> =
        PluginArchiveSigner.signedEntries(entries, privateKeyPem)

    companion object {
        suspend fun generate(bits: Int = 2048): TestSigner {
            val keyPair = CryptographyProvider.Default.get(RSA.PKCS1).keyPairGenerator(bits.bits, SHA256).generateKey()
            val publicPem = keyPair.publicKey.encodeToByteArray(RSA.PublicKey.Format.PEM).decodeToString()
            val privatePem = keyPair.privateKey.encodeToByteArray(RSA.PrivateKey.Format.PEM).decodeToString()
            return TestSigner(keyPair, publicPem, privatePem)
        }
    }
}

/** One entry for [zipOf]: stored as is, or as [deflated] raw DEFLATE bytes declaring [declaredSize]. */
internal class ZipItem(
    val name: String,
    val content: ByteArray = ByteArray(0),
    val deflated: ByteArray? = null,
    val declaredSize: Int = content.size,
    val crc: Int = crc32(content),
    val flags: Int = 0,
)

/** A minimal zip writer, so archives can be built on every target without a platform zip library. */
internal fun zipOf(items: List<ZipItem>, declaredCount: Int = items.size): ByteArray {
    val out = ArrayList<Byte>()
    val central = ArrayList<Byte>()
    fun MutableList<Byte>.u16(value: Int) { add(value.toByte()); add((value ushr 8).toByte()) }
    fun MutableList<Byte>.u32(value: Int) { u16(value and 0xFFFF); u16(value ushr 16) }
    for (item in items) {
        val name = item.name.encodeToByteArray()
        val data = item.deflated ?: item.content
        val method = if (item.deflated != null) 8 else 0
        val offset = out.size
        out.u32(0x04034b50); out.u16(20); out.u16(item.flags or 0x800); out.u16(method); out.u16(0); out.u16(0)
        out.u32(item.crc); out.u32(data.size); out.u32(item.declaredSize); out.u16(name.size); out.u16(0)
        out.addAll(name.toList()); out.addAll(data.toList())
        central.u32(0x02014b50); central.u16(20); central.u16(20); central.u16(item.flags or 0x800); central.u16(method)
        central.u16(0); central.u16(0); central.u32(item.crc); central.u32(data.size); central.u32(item.declaredSize)
        central.u16(name.size); central.u16(0); central.u16(0); central.u16(0); central.u16(0); central.u32(0); central.u32(offset)
        central.addAll(name.toList())
    }
    val directoryOffset = out.size
    out.addAll(central)
    out.u32(0x06054b50); out.u16(0); out.u16(0); out.u16(declaredCount); out.u16(declaredCount)
    out.u32(central.size); out.u32(directoryOffset); out.u16(0)
    return out.toByteArray()
}

internal const val WEATHER_MANIFEST_JSON: String =
    """{"id":"com.example.weather","name":"Weather","version":"1.0.0","publisher":"com.example.studio"}"""

internal fun weatherEntries(): List<PluginArchiveEntry> = listOf(
    PluginArchiveEntry(PLUGIN_ARCHIVE_MANIFEST_ENTRY_NAME, WEATHER_MANIFEST_JSON.encodeToByteArray()),
    PluginArchiveEntry("lib/plugin.jar", "jar-bytes".encodeToByteArray()),
)
