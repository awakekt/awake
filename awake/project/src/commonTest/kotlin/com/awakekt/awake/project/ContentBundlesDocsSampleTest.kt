/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The "Content bundles" guide includes these samples, so they keep compiling and doing what it says. */
class ContentBundlesDocsSampleTest {
    private val project = BundledProject()

    /** Stands in for the publish step's storage: a folder, a bucket, a CDN origin. */
    private val storage = mutableMapOf<String, ByteArray>()

    private fun upload(url: String, bytes: ByteArray) {
        storage[url] = bytes
    }

    @Test
    fun aPublishStepWritesBundlesAndTheGameReadsThem() = runTest {
        val looseIndex = project.looseIndex
        val readProjectFile: suspend (String) -> ByteArray = { path -> project.files.getValue(path) }

        // --8<-- [start:write]
        val writer = ContentBundleWriter(
            grouping = ContentBundleGrouping.byFolder(depth = 1),
            targetBundleBytes = 16L * 1024 * 1024,
        )
        val bundledIndex = writer.write(looseIndex, read = { entry -> readProjectFile(entry.path) }) { bundle ->
            // Named by SHA-256: skip a bundle the host already has.
            if (bundle.url !in storage) upload(bundle.url, bundle.bytes)
        }
        upload("project.index.json", Json.encodeToString(ProjectIndex.serializer(), bundledIndex).encodeToByteArray())
        // --8<-- [end:write]

        val requests = mutableListOf<String>()
        val hostFetch: suspend (AssetPath) -> ByteArray = { path -> requests += path.value; storage.getValue(path.value) }
        val cache = MemoryContentCache()
        // --8<-- [start:open]
        val files = IndexedAssetSource.open(fetch = hostFetch, cache = cache)
        files.prefetch(listOf("scenes/main.scene.json", "assets/models/crate.glb"))
        // --8<-- [end:open]

        assertContentEquals(project.files.getValue("assets/models/crate.glb"), files.read(AssetPath("assets/models/crate.glb")).getOrThrow())
        assertEquals(listOf("project.index.json"), requests.take(1))
        assertEquals(2, requests.drop(1).size, "the scenes bundle and the assets bundle, once each: $requests")
    }

    @Test
    fun aSceneGroupingKeepsWhatASceneLoadsTogether() = runTest {
        // --8<-- [start:grouping]
        val pierFiles = setOf("scenes/pier.scene.json", "assets/models/buoy.glb", "assets/sounds/bell.ogg")
        val byFolder = ContentBundleGrouping.byFolder()
        val byScene = ContentBundleGrouping { path -> if (path in pierFiles) "scene:pier" else "folder:" + byFolder.groupOf(path) }
        // --8<-- [end:grouping]

        val bundled = BundledProject(writer = ContentBundleWriter(grouping = byScene))

        assertTrue(bundled.bundles.values.any { it.paths.toSet() == pierFiles })
    }

    @Test
    fun opaqueIdsAndRangeReads() = runTest {
        // --8<-- [start:opaque]
        val writer = ContentBundleWriter(pathIds = ProjectPathIds(salt = "com.example.harbor-town"))
        // --8<-- [end:opaque]
        val bundled = BundledProject(writer = writer)
        val serve = bundled.server()
        val hostRangeFetch: suspend (String, Long, Long) -> ByteArray = { url, offset, length ->
            serve(url).copyOfRange(offset.toInt(), (offset + length).toInt())
        }
        // --8<-- [start:range]
        val files = IndexedAssetSource(bundled.index, fetch = serve, rangeFetch = hostRangeFetch)
        // --8<-- [end:range]

        assertContentEquals(project.files.getValue("assets/sounds/bell.ogg"), files.read(AssetPath("assets/sounds/bell.ogg")).getOrThrow())
    }
}
