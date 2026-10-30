/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
}
