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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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

    /**
     * The typical case on one fixed set of files. It isn't a guarantee for every set: a stretch with no
     * hash peak that's longer than the target is filled greedily, and a change there can move more.
     */
    @Test
    fun addingOneFileToThisGroupChangesAtMostTwoBundles() = runTest {
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
    fun theSlidingPeakSearchSplitsWhereComparingEveryNeighbourDoes() {
        val random = Random(1038)
        repeat(40) { round ->
            val target = 200L + random.nextLong(2_000)
            val entries = List(random.nextInt(1, 400)) { index ->
                // Zero-size and near-target files exercise windows that are empty or hold one file.
                val size = when (random.nextInt(10)) {
                    0 -> 0L
                    1 -> target - 1
                    else -> random.nextLong(1, target / 3 + 2)
                }
                ProjectIndexEntry(path = "assets/f-$round-${index.toString().padStart(4, '0')}.bin", sizeBytes = size, sha256 = "$round-$index")
            }
            val writer = ContentBundleWriter(grouping = ContentBundleGrouping.oneGroup(), targetBundleBytes = target)

            assertEquals(
                referencePlan(entries, target).map { chunk -> chunk.map { it.path } },
                writer.plan(entries).map { chunk -> chunk.map { it.path } },
                "round $round, target $target, ${entries.size} files",
            )
        }
    }

    @Test
    fun planningManySmallFilesTakesTimeInProportionToTheirCount() {
        // 100k 1 KiB files at the 16 MiB default: comparing every neighbour in a quarter-target window
        // was about 8,000 comparisons a file, or some 800 million here.
        val entries = List(100_000) { index ->
            ProjectIndexEntry(path = "assets/props/p-${index.toString().padStart(6, '0')}.bin", sizeBytes = 1024, sha256 = index.toString())
        }
        val writer = ContentBundleWriter(grouping = ContentBundleGrouping.oneGroup())

        val started = TimeSource.Monotonic.markNow()
        val plan = writer.plan(entries)
        val took = started.elapsedNow()

        assertTrue(took < 20.seconds, "planning 100k files took $took")
        assertEquals(entries.map { it.path }, plan.flatten().map { it.path }, "every file once, in path order")
        assertTrue(plan.all { chunk -> chunk.sumOf { it.sizeBytes } <= ContentBundleWriter.DEFAULT_TARGET_BUNDLE_BYTES })
        assertTrue(plan.size > 1, "the group is split: ${plan.size} bundles")
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

/**
 * The planner as first written, comparing each candidate cut with every neighbour in its window: slow,
 * but plainly right, so the sliding version is checked against it. One group, no file over [target].
 */
private fun referencePlan(entries: List<ProjectIndexEntry>, target: Long): List<List<ProjectIndexEntry>> {
    val run = entries.sortedBy { it.path }
    val total = run.sumOf { it.sizeBytes }
    if (total <= target) return listOf(run)
    val window = maxOf(1L, target / 4)
    val starts = LongArray(run.size)
    val ends = LongArray(run.size)
    var offset = 0L
    run.forEachIndexed { index, entry ->
        starts[index] = offset
        offset += entry.sizeBytes
        ends[index] = offset
    }
    val digests = run.map { Sha256.digest("${it.path}\u0000${it.sha256}".encodeToByteArray()) }
    fun beats(a: Int, b: Int): Boolean {
        for (i in digests[a].indices) {
            val d = (digests[a][i].toInt() and 0xff) - (digests[b][i].toInt() and 0xff)
            if (d != 0) return d > 0
        }
        return false
    }
    val segments = mutableListOf<List<ProjectIndexEntry>>()
    var segmentStart = 0
    for (index in 0 until run.lastIndex) {
        val cut = ends[index]
        val peak = cut >= window && total - cut >= window &&
            (index - 1 downTo 0).takeWhile { starts[index] - ends[it] < window }.all { beats(index, it) } &&
            (index + 1 until run.size).takeWhile { starts[it] - ends[index] < window }.all { beats(index, it) }
        if (peak) {
            segments += run.subList(segmentStart, index + 1)
            segmentStart = index + 1
        }
    }
    segments += run.subList(segmentStart, run.size)
    return segments.flatMap { segment ->
        val packed = mutableListOf<List<ProjectIndexEntry>>()
        var current = mutableListOf<ProjectIndexEntry>()
        var filled = 0L
        for (entry in segment) {
            if (current.isNotEmpty() && filled + entry.sizeBytes > target) {
                packed += current
                current = mutableListOf()
                filled = 0L
            }
            current += entry
            filled += entry.sizeBytes
        }
        if (current.isNotEmpty()) packed += current
        packed
    }
}
