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
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformContentCacheDesktopTest {
    private val temporary = Files.createTempDirectory("awake-content-cache").toFile()
    private val model = ByteArray(64) { it.toByte() }
    private val index = ProjectIndex(
        files = listOf(ProjectIndexEntry("assets/crate.glb", model.size.toLong(), Sha256.digestHex(model), "files/crate.glb")),
    )
    private val requests = mutableListOf<String>()

    @AfterTest
    fun deleteTemporary() {
        temporary.deleteRecursively()
    }

    @Test
    fun linuxUsesAnAbsoluteXdgCacheHomeElseTheHomeCacheFolder() {
        assertEquals("/data/cache", desktopCacheDirectory("Linux", "/home/player", env("XDG_CACHE_HOME" to "/data/cache")))
        assertEquals(File("/home/player", ".cache").path, desktopCacheDirectory("Linux", "/home/player", env()))
        assertEquals(
            File("/home/player", ".cache").path,
            desktopCacheDirectory("Linux", "/home/player", env("XDG_CACHE_HOME" to "relative/cache")),
            "the XDG Base Directory specification ignores a relative path",
        )
        assertEquals(File("/home/player", ".cache").path, desktopCacheDirectory("FreeBSD", "/home/player", env()))
    }

    @Test
    fun macOsUsesLibraryCaches() {
        assertEquals(
            File(File("/Users/player", "Library"), "Caches").path,
            desktopCacheDirectory("Mac OS X", "/Users/player", env("XDG_CACHE_HOME" to "/ignored")),
        )
    }

    @Test
    fun windowsUsesLocalAppDataElseTheHomeAppDataLocalFolder() {
        assertEquals(
            "C:\\Users\\player\\AppData\\Local",
            desktopCacheDirectory("Windows 11", "C:\\Users\\player", env("LOCALAPPDATA" to "C:\\Users\\player\\AppData\\Local")),
        )
        assertEquals(
            File(File("C:\\Users\\player", "AppData"), "Local").path,
            desktopCacheDirectory("Windows 11", "C:\\Users\\player", env()),
        )
    }

    @Test
    fun noHomeAndNoVariableMeansNoCacheFolder() {
        assertNull(desktopCacheDirectory("Linux", null, env()))
        assertNull(desktopCacheDirectory("Mac OS X", "", env()))
        assertNull(desktopCacheDirectory("Windows 10", null, env()))
    }

    @Test
    fun aSecondRunReadsUnchangedContentFromTheCacheItLeftOnDisk() = runTest {
        val firstRun = linuxCache("harbor-town", env("XDG_CACHE_HOME" to temporary.path))
        assertContentEquals(model, openAndRead(assertNotNull(firstRun)))
        assertEquals(listOf(IndexedAssetSource.DEFAULT_INDEX_PATH, "files/crate.glb"), requests)
        val stored = File(temporary, "harbor-town/awake-content/${index.files[0].sha256.take(2)}/${index.files[0].sha256}")
        assertTrue(stored.isFile, "the file is kept under <XDG_CACHE_HOME>/<namespace>/awake-content")

        requests.clear()
        // A fresh cache over the same folder, as the next launch of the app makes.
        val secondRun = linuxCache("harbor-town", env("XDG_CACHE_HOME" to temporary.path))
        assertContentEquals(model, openAndRead(assertNotNull(secondRun)))
        assertEquals(listOf(IndexedAssetSource.DEFAULT_INDEX_PATH), requests, "only the index is fetched again")
    }

    @Test
    fun aCacheFolderThatCantBeCreatedMeansNoCacheAndTheNetworkStillServes() = runTest {
        val blocker = File(temporary, "blocked").apply { writeText("a file where the cache folder would go") }

        val cache = linuxCache("harbor-town", env("XDG_CACHE_HOME" to blocker.path))

        assertNull(cache)
        assertContentEquals(model, openAndRead(cache))
    }

    @Test
    fun storageLostAfterTheCacheOpenedReadsFromTheNetwork() = runTest {
        val cache = assertNotNull(linuxCache("harbor-town", env("XDG_CACHE_HOME" to temporary.path)))
        val folder = File(temporary, "harbor-town/$CONTENT_CACHE_DIRECTORY")
        assertTrue(folder.deleteRecursively())
        folder.writeText("the cache folder is now a file, so every read and write fails")

        assertContentEquals(model, openAndRead(cache))
        assertContentEquals(model, openAndRead(cache))
        assertEquals(4, requests.size, "both opens fetch the index and the file")
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

    private fun linuxCache(namespace: String, environment: (String) -> String?): ContentCache? =
        desktopContentCache(namespace, osName = "Linux", userHome = temporary.path, environment = environment)

    private fun env(vararg variables: Pair<String, String>): (String) -> String? = variables.toMap()::get
}
