/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Stages fetch the files their lists name, skip what is missing, wait on gates and report failures. */
class ContentStagesTest {
    private val terrain = ByteArray(40) { 1 }
    private val props = ByteArray(60) { 2 }
    private val world = ByteArray(100) { 3 }
    private val fetched = mutableListOf<String>()

    private val publicFiles = mapOf(
        "stages/boot.txt" to "# boot content\nassets/terrain.bin\n\nassets/missing.bin\nassets/props.bin\n".encodeToByteArray(),
        "assets/terrain.bin" to terrain,
        "assets/props.bin" to props,
    )

    @Test
    fun aStageFetchesItsListedFilesWithByteProgressAndSkipsMissingOnes() = runTest {
        val stages = ContentStages(indexed(publicFiles, "public"), listOf(ContentStage("boot", listOf("stages/boot.txt", "stages/absent.txt"))))
        val seen = mutableListOf<ContentLoad>()
        // Unconfined, so the collector sees every state as it is set rather than the latest one.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { stages.state.collect { seen += it } }
        runCurrent()

        val ended = stages.load("boot")
        runCurrent()

        assertEquals(ContentLoad.Ready("boot"), ended)
        assertEquals(ContentLoad.Ready("boot"), stages.state.value)
        assertTrue("public/assets/terrain.bin" in fetched && "public/assets/props.bin" in fetched, "fetched: $fetched")
        val last = seen.filterIsInstance<ContentLoad.Loading>().last()
        assertEquals(100L, last.bytesTotal)
        assertEquals(100L, last.bytesDone)
        assertEquals(1f, last.fraction)
    }

    @Test
    fun theFallbackIsStagedWhenNoListFileExists() = runTest {
        val source = indexed(publicFiles, "public")
        val stages = ContentStages(source, listOf(ContentStage("boot", listOf("stages/none.txt"), fallback = listOf("assets/props.bin"))))

        assertEquals(listOf("assets/props.bin"), stages.paths(stages.stages.single()))
        assertEquals(ContentLoad.Ready("boot"), stages.load("boot"))
        assertEquals(listOf("public/assets/props.bin"), fetched)
    }

    @Test
    fun aGatedStageWaitsForContentUntilTheProtectedSourceIsAttached() = runTest {
        val listed = publicFiles + ("stages/world.txt" to "scenes/world.bin\n".encodeToByteArray())
        val layered = LayeredAssetSource(indexed(listed, "public"))
        val stages = ContentStages(layered, listOf(ContentStage("world", listOf("stages/world.txt"))))

        val load = async { stages.load("world") }
        runCurrent()
        assertEquals(ContentLoad.WaitingForContent("world"), stages.state.value)

        layered.attachProtected(indexed(mapOf("scenes/world.bin" to world), "protected"))
        assertEquals(ContentLoad.Ready("world"), load.await())
        assertTrue("protected/scenes/world.bin" in fetched)
    }

    @Test
    fun aStageThatDoesNotWaitSkipsGatedPaths() = runTest {
        val listed = publicFiles + ("stages/mixed.txt" to "assets/terrain.bin\nscenes/world.bin\n".encodeToByteArray())
        val layered = LayeredAssetSource(indexed(listed, "public"))
        val stage = ContentStage("mixed", listOf("stages/mixed.txt"), waitForGate = false)

        assertEquals(ContentLoad.Ready("mixed"), ContentStages(layered, listOf(stage)).load("mixed"))
        assertEquals(listOf("public/stages/mixed.txt", "public/assets/terrain.bin"), fetched)
    }

    @Test
    fun aFailureIsReportedAndTooNewContentIsToldApart() = runTest {
        val broken = AssetSource { path -> Result.failure(IllegalStateException("offline: ${path.value}")) }
        val tooNew = AssetSource { Result.failure(ContentFormatTooNewException("content index", 9, 4)) }
        val stage = ContentStage("boot", emptyList(), fallback = emptyList())
        val listStage = ContentStage("boot", listOf("stages/boot.txt"))

        assertEquals(ContentLoad.Ready("boot"), ContentStages(broken, listOf(stage)).load("boot"))
        assertIs<ContentLoad.Failed>(ContentStages(alwaysThere(broken), listOf(listStage)).load("boot"))
        assertIs<ContentLoad.TooNew>(ContentStages(alwaysThere(tooNew), listOf(listStage)).loadAll())
    }

    @Test
    fun aSourceThatCannotPrefetchOnlyChecksItsFiles() = runTest {
        val plain = AssetSource { path -> publicFiles[path.value]?.let { Result.success(it) } ?: Result.failure(NoSuchElementException(path.value)) }
        val stages = ContentStages(plain, listOf(ContentStage("boot", listOf("stages/boot.txt"))))

        assertEquals(listOf("assets/terrain.bin", "assets/props.bin"), stages.paths(stages.stages.single()))
        assertEquals(ContentLoad.Ready("boot"), stages.load("boot"))
    }

    @Test
    fun stageNamesAreUniqueAndKnown() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ContentStages(AssetSource { Result.failure(Exception()) }, listOf(ContentStage("a", emptyList()), ContentStage("a", emptyList())))
        }
        assertFailsWith<IllegalArgumentException> { ContentStages(AssetSource { Result.failure(Exception()) }, emptyList()).load("a") }
    }

    @Test
    fun aPathListSkipsBlankAndCommentLines() {
        assertEquals(listOf("a.bin", "b/c.bin"), ContentStages.parseList("# header\n a.bin \n\n#skip\nb/c.bin"))
    }

    private fun alwaysThere(source: AssetSource) = object : AssetSource by source {
        override suspend fun exists(path: AssetPath): Boolean = true
    }

    private fun indexed(files: Map<String, ByteArray>, prefix: String): IndexedAssetSource {
        val entries = files.map { (path, bytes) ->
            ProjectIndexEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = Sha256.digestHex(bytes), url = "$prefix/$path")
        }
        return IndexedAssetSource(ProjectIndex(files = entries), { url ->
            fetched += url
            files.getValue(url.removePrefix("$prefix/"))
        }, MemoryContentCache())
    }
}
