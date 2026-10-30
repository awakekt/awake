/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.project.IndexedAssetSource
import com.awakekt.awake.project.LayeredAssetSource
import com.awakekt.awake.project.ProjectIndex
import com.awakekt.awake.project.ProjectIndexEntry
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A project plays its sign-in scene from the public set alone, and its protected scenes once the app attaches them. */
class PublicSignInSetTest {
    private val publicFiles = mapOf(MANIFEST_PATH to MANIFEST, SIGN_IN to scene("sign-in"))
    private val protectedFiles = mapOf(HARBOR to scene("harbor"), SIGN_IN to scene("sign-in-signed"))
    private var signedIn = false
    private val protectedRequests = mutableListOf<String>()

    @Test
    fun theSignInScenePlaysWhileTheProtectedServerRefusesEveryRequest() = runTest {
        val files = LayeredAssetSource(indexed(publicFiles) { path -> publicFiles.getValue(path).encodeToByteArray() })
        assertFailsWith<IllegalStateException> { IndexedAssetSource.open(fetch = { path -> protectedFetch(path.value) }) }

        val project = loadProject(files, SIGN_IN, SceneComponentRegistry.scoped(), hasRenderer = false)

        assertEquals("sign-in", project.scene.name)
        assertEquals(listOf(IndexedAssetSource.DEFAULT_INDEX_PATH), protectedRequests, "only the refused index was asked for")
        val refused = assertFailsWith<IllegalArgumentException> { project.switchTo(HARBOR) }
        assertTrue(HARBOR in refused.message.orEmpty(), "the refusal names the protected scene: ${refused.message}")
        assertEquals("sign-in", project.scene.name, "and the sign-in scene plays on")
        project.close()
    }

    @Test
    fun afterSignInAProtectedSceneLoadsAndTheProtectedCopyWins() = runTest {
        val files = LayeredAssetSource(indexed(publicFiles) { path -> publicFiles.getValue(path).encodeToByteArray() })
        val project = loadProject(files, SIGN_IN, SceneComponentRegistry.scoped(), hasRenderer = false)

        signedIn = true
        files.attachProtected(indexed(protectedFiles, ::protectedFetch))
        project.switchTo(HARBOR)

        assertEquals("harbor", project.scene.name)
        project.switchTo(SIGN_IN)
        assertEquals("sign-in-signed", project.scene.name, "a file both sets have is read from the protected one")
        assertEquals(listOf(HARBOR, SIGN_IN), protectedRequests)
        project.close()
    }

    /** The protected server: it refuses every request until the player has signed in. */
    private fun protectedFetch(path: String): ByteArray {
        protectedRequests += path
        check(signedIn) { "401 $path" }
        return protectedFiles.getValue(path).encodeToByteArray()
    }

    private fun indexed(files: Map<String, String>, fetch: (String) -> ByteArray): IndexedAssetSource {
        val entries = files.map { (path, text) ->
            val bytes = text.encodeToByteArray()
            ProjectIndexEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = Sha256.digestHex(bytes), url = path)
        }
        return IndexedAssetSource(ProjectIndex(files = entries), { url -> fetch(url) })
    }

    private companion object {
        const val MANIFEST_PATH = "awake.project.json"
        const val SIGN_IN = "scenes/sign-in.scene.json"
        const val HARBOR = "scenes/harbor.scene.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"$HARBOR"}"""

        fun scene(name: String) =
            """{ "version": 1, "name": "$name", "nodes": [ { "name": "Camera", "components": [ { "component": "camera" } ] } ] }"""
    }
}
