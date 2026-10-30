/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The public set serves before sign-in, the protected source after it, and no file is fetched twice. */
class LayeredAssetSourceTest {
    private val manifest = "{\"id\":\"com.example.harbor-town\"}".encodeToByteArray()
    private val signIn = "{\"version\": 1, \"name\": \"sign-in\"}".encodeToByteArray()
    private val world = "{\"version\": 1, \"name\": \"harbor\"}".encodeToByteArray()
    private val publicLogo = ByteArray(16) { 1 }
    private val protectedLogo = ByteArray(24) { 2 }

    private val publicFiles = mapOf("awake.project.json" to manifest, "scenes/sign-in.scene.json" to signIn, "assets/logo.png" to publicLogo)
    private val protectedFiles = mapOf("scenes/harbor.scene.json" to world, "assets/logo.png" to protectedLogo)
    private val fetched = mutableListOf<String>()

    @Test
    fun thePublicSetReadsWithNoProtectedSource() = runTest {
        val layered = LayeredAssetSource(indexed(publicFiles, "public"))

        assertContentEquals(signIn, layered.read(AssetPath("scenes/sign-in.scene.json")).getOrThrow())
        assertContentEquals(manifest, layered.read(AssetPath("awake.project.json")).getOrThrow())
        assertFalse(layered.hasProtected)
    }

    @Test
    fun aProtectedPathBeforeSignInFailsWithoutFetching() = runTest {
        val layered = LayeredAssetSource(indexed(publicFiles, "public"))

        val failure = layered.read(AssetPath("scenes/harbor.scene.json")).exceptionOrNull()

        assertIs<ProtectedContentNotAttachedException>(failure)
        assertEquals("scenes/harbor.scene.json", failure.path)
        assertTrue(layered.needsProtected(AssetPath("scenes/harbor.scene.json")))
        assertFalse(layered.needsProtected(AssetPath("scenes/sign-in.scene.json")))
        assertEquals(emptyList(), fetched)
    }

    @Test
    fun afterSignInProtectedScenesReadAndTheProtectedCopyWins() = runTest {
        val layered = LayeredAssetSource(indexed(publicFiles, "public"))
        layered.attachProtected(indexed(protectedFiles, "protected"))

        assertContentEquals(world, layered.read(AssetPath("scenes/harbor.scene.json")).getOrThrow())
        assertContentEquals(protectedLogo, layered.read(AssetPath("assets/logo.png")).getOrThrow())
        assertContentEquals(signIn, layered.read(AssetPath("scenes/sign-in.scene.json")).getOrThrow())
        assertEquals(listOf("protected/scenes/harbor.scene.json", "protected/assets/logo.png", "public/scenes/sign-in.scene.json"), fetched)
        assertFalse(layered.needsProtected(AssetPath("scenes/harbor.scene.json")))
    }

    @Test
    fun aTamperedProtectedFileStillFailsItsCheck() = runTest {
        val layered = LayeredAssetSource(indexed(publicFiles, "public"))
        layered.attachProtected(indexed(protectedFiles, "protected", tampered = "assets/logo.png"))

        val failure = layered.read(AssetPath("assets/logo.png")).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("assets/logo.png"), "the protected layer's check holds: $failure")
    }

    @Test
    fun aPathNeitherLayerHasFailsAsTheProtectedSourceFailsIt() = runTest {
        val layered = LayeredAssetSource(indexed(publicFiles, "public"))
        layered.attachProtected(indexed(protectedFiles, "protected"))

        assertIs<NoSuchElementException>(layered.read(AssetPath("scenes/missing.scene.json")).exceptionOrNull())
        assertFalse(layered.exists(AssetPath("scenes/missing.scene.json")))
    }

    @Test
    fun awaitProtectedResumesWhenTheSourceIsAttached() = runTest {
        val layered = LayeredAssetSource(indexed(publicFiles, "public"))
        val protectedSource = indexed(protectedFiles, "protected")

        val waiting = async { layered.awaitProtected() }
        runCurrent()
        assertFalse(waiting.isCompleted)
        layered.attachProtected(protectedSource)

        assertSame(protectedSource, waiting.await())
    }

    @Test
    fun signingOutDetachesTheProtectedLayer() = runTest {
        val layered = LayeredAssetSource(indexed(publicFiles, "public"))
        layered.attachProtected(indexed(protectedFiles, "protected"))
        layered.detachProtected()

        assertIs<ProtectedContentNotAttachedException>(layered.read(AssetPath("scenes/harbor.scene.json")).exceptionOrNull())
        assertContentEquals(publicLogo, layered.read(AssetPath("assets/logo.png")).getOrThrow())
    }

    /** An index over [files] served under [prefix]; every fetch is recorded in [fetched]. */
    private fun indexed(files: Map<String, ByteArray>, prefix: String, tampered: String? = null): IndexedAssetSource {
        val entries = files.map { (path, bytes) ->
            ProjectIndexEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = Sha256.digestHex(bytes), url = "$prefix/$path")
        }
        return IndexedAssetSource(ProjectIndex(files = entries), { url ->
            fetched += url
            val path = url.removePrefix("$prefix/")
            val bytes = files.getValue(path)
            if (path == tampered) bytes.copyOf().also { it[0] = 99 } else bytes
        })
    }
}
