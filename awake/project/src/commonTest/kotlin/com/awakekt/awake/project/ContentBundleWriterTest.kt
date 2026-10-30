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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A project's files packed into bundles: the same files every time, read back exactly. */
class ContentBundleWriterTest {
    @Test
    fun everyFileReadsBackThroughItsBundle() = runTest {
        val project = BundledProject()
        val source = IndexedAssetSource(project.index, project.server())

        project.files.forEach { (path, bytes) -> assertContentEquals(bytes, source.read(AssetPath(path)).getOrThrow(), path) }
        assertTrue(ProjectContentValidator.indexIssues(project.index).isEmpty())
        assertEquals(ProjectIndex.BUNDLED_FORMAT_VERSION, project.index.formatVersion)
        assertTrue(project.index.files.all { it.bundle != null && it.url.isEmpty() }, "every entry points into a bundle")
    }

    @Test
    fun theSameFilesWriteByteIdenticalBundlesAndIndex() {
        val first = BundledProject()
        val shuffled = BundledProject(files = first.files.entries.reversed().associate { it.key to it.value.copyOf() })

        assertEquals(first.index, shuffled.index)
        assertEquals(first.bundles.keys, shuffled.bundles.keys)
        first.bundles.forEach { (url, bundle) -> assertContentEquals(bundle.bytes, shuffled.bundles.getValue(url).bytes, url) }
    }

    @Test
    fun aBundleIsNamedByItsSha256() {
        val project = BundledProject()

        project.bundles.values.forEach { bundle ->
            assertEquals("bundles/${bundle.sha256}.awkb", bundle.url)
            assertEquals(com.awakekt.awake.core.io.Sha256.digestHex(bundle.bytes), bundle.sha256)
        }
        assertEquals(project.bundles.values.map { it.sha256 }, project.index.bundles.map { it.sha256 })
    }

    @Test
    fun byFolderKeepsEachTopFolderInBundlesOfItsOwn() {
        val project = BundledProject()

        val groups = project.bundles.values.map { bundle -> bundle.paths.map { it.substringBefore('/', "") }.toSet() }

        assertEquals(listOf(setOf(""), setOf("assets"), setOf("scenes")), groups, "one bundle per top folder, in name order")
    }

    @Test
    fun aDeeperFolderGroupingSplitsFurther() {
        val project = BundledProject(writer = ContentBundleWriter(grouping = ContentBundleGrouping.byFolder(depth = 2)))

        val groups = project.bundles.values.map { it.paths.first().substringBeforeLast('/', "") }

        assertEquals(listOf("", "assets/models", "assets/sounds", "assets/textures", "scenes"), groups)
    }

    @Test
    fun theSizeTargetSplitsAGroupAndAnOversizedFileStandsAlone() {
        val project = BundledProject(writer = ContentBundleWriter(grouping = ContentBundleGrouping.oneGroup(), targetBundleBytes = 250))

        val sizes = project.bundles.values.map { bundle -> bundle.paths.sumOf { project.files.getValue(it).size } }

        assertTrue(project.bundles.values.any { it.paths == listOf("assets/models/crate.glb") }, "the 300-byte crate has a bundle of its own")
        assertTrue(sizes.filter { it != 300 }.all { it <= 250 }, "the rest fill to the target: $sizes")
        assertEquals(project.files.keys, project.bundles.values.flatMap { it.paths }.toSet())
    }

    @Test
    fun aCustomGroupingKeepsAScenesFilesTogether() {
        val pier = setOf("scenes/pier.scene.json", "assets/models/buoy.glb", "assets/sounds/bell.ogg")
        val grouping = ContentBundleGrouping { path -> if (path in pier) "pier" else "main" }

        val project = BundledProject(writer = ContentBundleWriter(grouping = grouping))

        assertEquals(listOf(project.files.keys - pier, pier), project.bundles.values.map { it.paths.toSet() })
    }

    @Test
    fun aFileThatDoesNotMatchTheIndexFailsNamingIt() = runTest {
        val project = BundledProject()
        val stale = project.files + ("assets/models/buoy.glb" to ByteArray(200))

        val failure = assertFailsWith<IllegalStateException> {
            ContentBundleWriter().write(project.looseIndex, { stale.getValue(it.path) }) { }
        }

        assertTrue(failure.message.orEmpty().contains("assets/models/buoy.glb"), "$failure")
    }

    @Test
    fun aBundledIndexIsNotBundledAgain() = runTest {
        val project = BundledProject()

        assertFailsWith<IllegalArgumentException> { ContentBundleWriter().write(project.index, { ByteArray(0) }) { } }
    }

    @Test
    fun opaqueIdsLeaveNoReadablePathInTheIndexOrTheBundles() = runTest {
        val ids = ProjectPathIds(salt = "com.example.harbor-town")
        val project = BundledProject(writer = ContentBundleWriter(pathIds = ids))
        val json = Json.encodeToString(ProjectIndex.serializer(), project.index)
        val source = IndexedAssetSource(project.index, project.server())

        project.files.keys.forEach { path ->
            assertFalse(path in json, "the index names $path")
            project.bundles.values.forEach { assertFalse(path in it.bytes.decodeToString(), "a bundle names $path") }
            assertContentEquals(project.files.getValue(path), source.read(AssetPath(path)).getOrThrow())
            assertTrue(source.exists(AssetPath(path)))
        }
        assertEquals(ids, project.index.pathIds)
        assertTrue(project.index.files.all { it.path.isEmpty() })
        assertEquals(project.files.keys.map(ids::idOf).toSet(), project.index.files.map { it.id }.toSet())
        assertFalse(source.exists(AssetPath("assets/models/missing.glb")))
    }

    @Test
    fun anOpaqueIdIsASaltedHashOfThePath() {
        val ids = ProjectPathIds(salt = "salt")

        assertEquals(ProjectPathIds.ID_LENGTH, ids.idOf("assets/a.png").length)
        assertEquals(ids.idOf("assets/a.png"), ids.idOf("/assets/a.png"), "a leading slash names the same file")
        assertFalse(ids.idOf("assets/a.png") == ProjectPathIds(salt = "pepper").idOf("assets/a.png"))
    }

    @Test
    fun theBundledIndexRoundTripsThroughJsonAndALooseOneStaysAsItWas() {
        val project = BundledProject()
        val json = Json { ignoreUnknownKeys = true }

        val bundled = json.decodeFromString(ProjectIndex.serializer(), Json.encodeToString(ProjectIndex.serializer(), project.index))
        val loose = Json.encodeToString(ProjectIndex.serializer(), project.looseIndex)

        assertEquals(project.index, bundled)
        assertFalse("bundle" in loose || "\"id\"" in loose || "offset" in loose, "a loose index writes no new field: $loose")
        assertNull(json.decodeFromString(ProjectIndex.serializer(), loose).files.first().bundle)
    }

    @Test
    fun addingOneFileToAGroupChangesAtMostTwoBundles() = runTest {
        val writer = ContentBundleWriter(grouping = ContentBundleGrouping.byFolder(), targetBundleBytes = 800)
        val baseFiles = buildMap {
            put("awake.project.json", "{\"formatVersion\":1,\"id\":\"com.example.harbor-town\"}".encodeToByteArray())
            put("scenes/main.scene.json", "{\"version\":1,\"name\":\"harbor\"}".encodeToByteArray())
            for (index in 0 until 60) {
                val name = index.toString().padStart(3, '0')
                put("assets/props/prop-$name.bin", ByteArray(100) { ((index * 31) + it).toByte() })
            }
        }
        val before = BundledProject(files = baseFiles, writer = writer)
        val beforeAssets = before.bundles.values.filter { it.paths.first().startsWith("assets/") }
        assertTrue(beforeAssets.size >= 6, "a multi-bundle group: ${beforeAssets.size} bundles")

        val additions = listOf(
            "assets/props/prop-000-added.bin",
            "assets/props/prop-014-added.bin",
            "assets/props/prop-029-added.bin",
            "assets/props/prop-044-added.bin",
            "assets/props/prop-059-added.bin",
        )
        for ((step, addedPath) in additions.withIndex()) {
            val addedBytes = ByteArray(100) { ((step + 1) * 53 + it).toByte() }
            val after = BundledProject(files = baseFiles + (addedPath to addedBytes), writer = writer)
            val newBundles = after.bundles.keys - before.bundles.keys

            assertTrue(
                newBundles.size in 1..2,
                "adding $addedPath changed ${newBundles.size} bundles (expected 1..2): $newBundles",
            )
            assertTrue(
                after.bundles.values.all { bundle -> bundle.paths.sumOf { after.files.getValue(it).size } <= 800 },
                "every bundle stays within targetBundleBytes",
            )
            val source = IndexedAssetSource(after.index, after.server())
            assertContentEquals(addedBytes, source.read(AssetPath(addedPath)).getOrThrow())
        }
    }

    @Test
    fun duplicateContentFilesWithDistinctPathsStillSplitCleanly() {
        val writer = ContentBundleWriter(grouping = ContentBundleGrouping.oneGroup(), targetBundleBytes = 500)
        val identical = ByteArray(80) { 42 }
        val files = (0 until 30).associate { index ->
            "assets/tiles/tile-${index.toString().padStart(2, '0')}.png" to identical.copyOf()
        }
        val before = BundledProject(files = files, writer = writer)
        val after = BundledProject(files = files + ("assets/tiles/tile-12a.png" to identical.copyOf()), writer = writer)

        val changed = after.bundles.keys - before.bundles.keys
        assertTrue(before.bundles.size >= 4, "split into multiple bundles: ${before.bundles.size}")
        assertTrue(changed.size in 1..2, "adding one identical-content file changed ${changed.size} bundles: $changed")
    }
}
