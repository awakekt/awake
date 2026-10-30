/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginArchiveReaderTest {
    private val manifest = ZipItem(PLUGIN_ARCHIVE_MANIFEST_ENTRY_NAME, WEATHER_MANIFEST_JSON.encodeToByteArray())

    // Raw DEFLATE of PAYLOAD_TEXT repeated 40 times, from Python's zlib (wbits -15), so every
    // target's inflater is checked against bytes it did not produce.
    private val deflated = Base64.decode("80gsSsovUgjJL89TKE9NLMlILVIoyClNz8xTKEiszMlPTNFT8BhVM6pmVM2omlE1dFcDAA==")
    private val inflated = PAYLOAD_TEXT.repeat(40).encodeToByteArray()

    private suspend fun readFailure(items: List<ZipItem>, declaredCount: Int = items.size): String =
        assertFailsWith<PluginArchiveException> { PluginArchiveReader.read(zipOf(items, declaredCount)) }.message.orEmpty()

    @Test
    fun readsStoredAndDeflatedEntriesAndSkipsDirectories() = runTest {
        val archive = PluginArchiveReader.read(
            zipOf(
                listOf(
                    manifest,
                    ZipItem("lib/"),
                    ZipItem("lib/payload.txt", inflated, deflated = deflated, crc = 0xfbffa416.toInt()),
                    ZipItem(PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME, byteArrayOf(9, 8, 7)),
                ),
            ),
        )

        assertEquals("com.example.weather", archive.manifest.id)
        assertEquals(listOf("plugin.json", "lib/payload.txt", "signature.sig"), archive.entries.map { it.name })
        assertContentEquals(inflated, archive.entry("lib/payload.txt")?.content)
        assertEquals(listOf("lib/payload.txt"), archive.payload.map { it.name })
        assertContentEquals(byteArrayOf(9, 8, 7), archive.signature)
    }

    @Test
    fun anArchiveWithoutASignatureHasNone() = runTest {
        assertNull(PluginArchiveReader.read(zipOf(listOf(manifest))).signature)
    }

    @Test
    fun refusesUnsafeEntryPaths() = runTest {
        for (path in listOf("../evil.jar", "/abs.jar", "lib/../../x", "lib\\x.jar", "C:x.jar", "lib//x", "./x", "lib/\u0001")) {
            val message = readFailure(listOf(manifest, ZipItem(path, byteArrayOf(1))))
            assertTrue("not a safe relative path" in message, "$path: $message")
        }
    }

    @Test
    fun refusesDuplicateEntries() = runTest {
        assertTrue("more than once" in readFailure(listOf(manifest, ZipItem("a.txt"), ZipItem("a.txt"))))
    }

    @Test
    fun refusesMoreEntriesThanTheLimitBeforeReadingThem() = runTest {
        assertTrue("at most 4096" in readFailure(listOf(manifest), declaredCount = PluginArchive.MAX_ENTRIES + 1))
    }

    @Test
    fun refusesADeclaredUnpackedSizeOverTheLimitBeforeInflating() = runTest {
        val bomb = ZipItem("lib/bomb.bin", deflated = deflated, declaredSize = (PluginArchive.MAX_UNPACKED_BYTES + 1).toInt())

        assertTrue("unpacked bytes" in readFailure(listOf(manifest, bomb)))
    }

    @Test
    fun refusesAnEntryThatInflatesPastItsDeclaredSize() = runTest {
        val liar = ZipItem("lib/payload.txt", deflated = deflated, declaredSize = 100)

        assertTrue("does not inflate" in readFailure(listOf(manifest, liar)))
    }

    @Test
    fun refusesCorruptDeflateDataAndABadCrc() = runTest {
        val corrupt = ZipItem("lib/x", deflated = byteArrayOf(-1, -1, -1, -1), declaredSize = 10)
        assertTrue("does not inflate" in readFailure(listOf(manifest, corrupt)))
        assertTrue("CRC-32" in readFailure(listOf(manifest, ZipItem("lib/x", byteArrayOf(1, 2), crc = 7))))
    }

    @Test
    fun refusesEncryptedEntriesNonZipBytesAndAMissingOrBrokenManifest() = runTest {
        assertTrue("encrypted" in readFailure(listOf(manifest, ZipItem("lib/x", byteArrayOf(1), flags = 1))))
        assertTrue(
            "Not a zip" in assertFailsWith<PluginArchiveException> { PluginArchiveReader.read("hello".encodeToByteArray()) }.message.orEmpty(),
        )
        assertTrue("no plugin.json" in readFailure(listOf(ZipItem("lib/x", byteArrayOf(1)))))
        assertTrue("not a valid manifest" in readFailure(listOf(ZipItem("plugin.json", """{"id":""}""".encodeToByteArray()))))
    }

    @Test
    fun fromEntriesAppliesTheSameLimits() {
        val tooBig = PluginArchiveEntry("big.bin", ByteArray(0))
        assertFailsWith<PluginArchiveException> { PluginArchive.fromEntries(List(PluginArchive.MAX_ENTRIES + 1) { tooBig }) }
        assertFailsWith<PluginArchiveException> {
            PluginArchive.fromEntries(weatherEntries() + PluginArchiveEntry("../x", ByteArray(0)))
        }
        assertEquals("Weather", PluginArchive.fromEntries(weatherEntries()).manifest.name)
    }

    private companion object {
        const val PAYLOAD_TEXT = "Harbor Town weather plugin payload. "
    }
}
