/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A wrong key reads as an authentication failure on every path, and a key that can't be had as itself. */
class EncryptedKeyFailureTest {
    private val crate = "assets/models/crate.glb"
    private val wrongKey = ByteArray(32) { 7 }

    @Test
    fun aWrongKeyFailsAuthenticationOnTheWholeBundlePathAndIsAskedForAgain() = runTest {
        val project = EncryptedProject.write()
        var asks = 0
        val source = IndexedAssetSource(project.index, project.server(), keys = { asks++; wrongKey })

        val first = source.read(AssetPath(crate)).exceptionOrNull()
        val second = source.read(AssetPath(crate)).exceptionOrNull()

        assertTrue(first?.message.orEmpty().contains("failed authentication: the wrong key (${EncryptedProject.KEY_ID})"), "got $first")
        assertTrue(second?.message.orEmpty().contains("failed authentication"), "got $second")
        assertEquals(2, asks, "a key a matching bundle proved wrong is dropped, so the next read asks again")
    }

    @Test
    fun aWrongKeyFailsAuthenticationOnTheRangePath() = runTest {
        val project = EncryptedProject.write()
        val range: suspend (String, Long, Long) -> ByteArray = { url, offset, length ->
            project.bundles.getValue(url).bytes.copyOfRange(offset.toInt(), (offset + length).toInt())
        }
        val source = IndexedAssetSource(project.index, { error("unused") }, rangeFetch = range, keys = { wrongKey })

        val failure = source.read(AssetPath(crate)).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("$crate failed authentication: the wrong key"), "got $failure")
    }

    @Test
    fun aProviderFailureOnACacheHitFailsTheReadAsItselfWithNoFetch() = runTest {
        val project = EncryptedProject.write()
        val cache = MemoryContentCache()
        IndexedAssetSource(project.index, project.server(), cache, keys = project.keys()).prefetch(project.files.keys.toList())
        var asks = 0
        var fetches = 0
        val signedOut = BundleKeyProvider { asks++; error("sign in to play") }
        val offline = IndexedAssetSource(project.index, { fetches++; error("offline") }, cache, keys = signedOut)

        val failure = offline.read(AssetPath(crate)).exceptionOrNull()

        assertEquals("sign in to play", failure?.message)
        assertEquals(1, asks)
        assertEquals(0, fetches, "a cached member isn't fetched again because the key couldn't be had")
    }

    @Test
    fun aWrongKeyForOneBundleForgetsOnlyThatBundlesKey() = runTest {
        val commonKey = ByteArray(32) { (it * 3 + 1).toByte() }
        val expansionKey = ByteArray(32) { (it * 9 + 2).toByte() }
        val bundles = LinkedHashMap<String, WrittenContentBundle>()
        val index = ContentBundleWriter(
            encryption = BundleEncryptionKey("common-2026", commonKey),
            groupEncryption = { group -> if (group == "scenes") BundleEncryptionKey("expansion-a", expansionKey) else null },
        ).write(looseIndexOf(BundledProject.SAMPLE_FILES), { BundledProject.SAMPLE_FILES.getValue(it.path) }) { bundles[it.url] = it }

        val asks = mutableMapOf<String, Int>()
        val source = IndexedAssetSource(
            index = index,
            fetch = { url -> bundles.getValue(url).bytes },
            keys = { keyId ->
                val count = (asks[keyId] ?: 0) + 1
                asks[keyId] = count
                when {
                    keyId == "common-2026" -> commonKey
                    count == 1 -> wrongKey
                    else -> expansionKey
                }
            },
        )

        assertContentEquals(BundledProject.SAMPLE_FILES.getValue(crate), source.read(AssetPath(crate)).getOrThrow())
        val firstExpansion = source.read(AssetPath("scenes/pier.scene.json")).exceptionOrNull()
        assertTrue(firstExpansion?.message.orEmpty().contains("failed authentication: the wrong key (expansion-a)"), "got $firstExpansion")

        // Common's seal stays cached; expansion-a's wrong key was forgotten and is asked again.
        assertContentEquals(
            BundledProject.SAMPLE_FILES.getValue("awake.project.json"),
            source.read(AssetPath("awake.project.json")).getOrThrow(),
        )
        assertContentEquals(
            BundledProject.SAMPLE_FILES.getValue("scenes/pier.scene.json"),
            source.read(AssetPath("scenes/pier.scene.json")).getOrThrow(),
        )
        assertEquals(mapOf("common-2026" to 1, "expansion-a" to 2), asks)
    }
}
