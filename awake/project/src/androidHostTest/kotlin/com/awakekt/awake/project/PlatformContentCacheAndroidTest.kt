/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import android.content.Context
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Android default cache, in a temporary folder standing in for the app's `Context.cacheDir`. */
internal class PlatformContentCacheAndroidTest {
    // Stands in for Context.cacheDir.
    private val cacheDir = Files.createTempDirectory("awake-android-cache").toFile()
    private val model = ByteArray(48) { (it * 3).toByte() }
    private val index = ProjectIndex(
        files = listOf(ProjectIndexEntry("assets/crate.glb", model.size.toLong(), Sha256.digestHex(model), "files/crate.glb")),
    )
    private val requests = mutableListOf<String>()

    @AfterTest
    fun deleteCacheDir() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun withoutTheAppsCacheDirThereIsNoDefault() {
        assertNull(platformContentCache("harbor-town"))
    }

    @Test
    fun aSecondRunReadsUnchangedContentFromTheAppsCacheDir() = runTest {
        assertContentEquals(model, openAndRead(assertNotNull(platformContentCache("harbor-town", cacheDir))))
        val sha256 = index.files[0].sha256
        assertTrue(File(cacheDir, "harbor-town/awake-content/${sha256.take(2)}/$sha256").isFile)

        requests.clear()
        assertContentEquals(model, openAndRead(assertNotNull(platformContentCache("harbor-town", cacheDir))))
        assertEquals(listOf(IndexedAssetSource.DEFAULT_INDEX_PATH), requests, "only the index is fetched again")
    }

    @Test
    fun theNamespaceIsCheckedLikeEverywhereElse() {
        assertFailsWith<IllegalArgumentException> { platformContentCache("../escape", cacheDir) }
        assertTrue(cacheDir.listFiles().isNullOrEmpty(), "nothing was created")
    }

    /** The "Protected content" guide's Android sample: compiled here, and not run, since it needs an app's `Context`. */
    @Suppress("unused")
    private suspend fun guideSample(context: Context, contentFetch: TokenAuthorizedFetch): IndexedAssetSource {
        // --8<-- [start:android-cache]
        // Android: the app passes its own cache folder, which only its Context knows.
        val cache = platformContentCache(namespace = "harbor-town", cacheDir = context.cacheDir)
        val source = IndexedAssetSource.open(fetch = contentFetch, cache = cache)
        // --8<-- [end:android-cache]
        return source
    }

    private suspend fun openAndRead(cache: ContentCache?): ByteArray {
        val indexBytes = Json.encodeToString(ProjectIndex.serializer(), index).encodeToByteArray()
        val source = IndexedAssetSource.open(
            fetch = { path ->
                requests += path.value
                when (path.value) {
                    IndexedAssetSource.DEFAULT_INDEX_PATH -> indexBytes
                    "files/crate.glb" -> model
                    else -> error("Unexpected request: $path")
                }
            },
            cache = cache,
        )
        return source.read(AssetPath("assets/crate.glb")).getOrThrow()
    }
}
