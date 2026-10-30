/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** The guides' "public sign-in set" samples, kept compiling and doing what they say. */
class PublicSignInSetDocsSampleTest {
    private val signIn = "{\"name\":\"sign-in\"}".encodeToByteArray()
    private val harbor = "{\"name\":\"harbor\"}".encodeToByteArray()
    private val publicIndex = indexOf("public/files", "scenes/sign-in.scene.json" to signIn)
    private val protectedIndex = indexOf("files", "scenes/harbor.scene.json" to harbor)
    private val requests = mutableListOf<String>()

    /** Stands in for the page's own origin, which serves the public set to anyone. */
    private suspend fun fetchFromPage(path: AssetPath): ByteArray {
        requests += path.value
        return when (path.value) {
            "public/project.index.json" -> Json.encodeToString(ProjectIndex.serializer(), publicIndex).encodeToByteArray()
            "public/files/scenes/sign-in.scene.json" -> signIn
            else -> error("404 ${path.value}")
        }
    }

    /** Stands in for the protected content server, which answers only a signed-in player's token. */
    private val transport = ContentTransport { request ->
        requests += request.url
        val body = when (request.url) {
            "project.index.json" -> Json.encodeToString(ProjectIndex.serializer(), protectedIndex).encodeToByteArray()
            "files/scenes/harbor.scene.json" -> harbor
            else -> return@ContentTransport ContentResponse(404, ByteArray(0))
        }
        if (request.headers["Authorization"] == "Bearer player-token") ContentResponse(200, body) else ContentResponse(401, ByteArray(0))
    }

    @Test
    fun aGameStartsOnThePublicSetAndAttachesProtectedContentAfterSignIn() = runTest {
        // --8<-- [start:public]
        // Before sign-in: the public index ships next to the page, so a plain fetch reads it.
        val files = LayeredAssetSource(
            IndexedAssetSource.open(indexPath = AssetPath("public/project.index.json"), fetch = ::fetchFromPage),
        )
        // --8<-- [end:public]

        assertContentEquals(signIn, files.read(AssetPath("scenes/sign-in.scene.json")).getOrThrow())
        assertTrue(files.needsProtected(AssetPath("scenes/harbor.scene.json")))

        // --8<-- [start:attach]
        // After sign-in: the game opens its protected index with the player's token and attaches it.
        val contentFetch = TokenAuthorizedFetch(transport) { ReadToken("player-token", expiresIn = 1.hours) }
        files.attachProtected(IndexedAssetSource.open(fetch = contentFetch))
        // --8<-- [end:attach]

        assertContentEquals(harbor, files.read(AssetPath("scenes/harbor.scene.json")).getOrThrow())
        assertEquals(
            listOf("public/project.index.json", "public/files/scenes/sign-in.scene.json", "project.index.json", "files/scenes/harbor.scene.json"),
            requests,
            "each index and file once, the protected ones only after sign-in",
        )
    }

    private fun indexOf(base: String, vararg files: Pair<String, ByteArray>) = ProjectIndex(
        files = files.map { (path, bytes) -> ProjectIndexEntry(path, bytes.size.toLong(), Sha256.digestHex(bytes), "$base/$path") },
    )
}
