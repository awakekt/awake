/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.project.AssetMismatch
import com.awakekt.awake.project.AwakeAssetLockEntry
import com.awakekt.awake.project.AwakeAssetsLock
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.project.ContentCache
import com.awakekt.awake.project.IndexedAssetSource
import com.awakekt.awake.project.LockVerifiedAssetSource
import com.awakekt.awake.project.ProjectIndex
import com.awakekt.awake.project.ProjectIndexEntry
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A game watching a scene switch sees it load, with how far, and then become ready, or fail with why:
 * all through the state a loading curtain reads, with the scene it leaves playing meanwhile.
 */
class SceneSwitchStateTest {
    @Test
    fun aSwitchGoesFromLoadingToReadyInOrder() = runTest {
        for (delta in listOf(DELTA, 0f)) {
            val project = loadProject(files())
            project.sceneLoads = Dispatchers.Unconfined
            val game = play(project)
            val seen = Seen(game.status)
            assertEquals(SceneSwitchState.Idle, game.status.state.value, "nothing is asked for yet")

            game.switch.request(LEVEL)
            seen.record()
            game.update(delta)
            seen.record()

            assertEquals("level", project.scene.name, "the level is in place")
            assertEquals(SceneSwitchState.Loading(LEVEL, 1f), game.status.state.value, "but the frame that first plays it is not over")
            game.update(delta)
            seen.record()

            assertEquals(listOf(SceneSwitchState.Idle, SceneSwitchState.Loading(LEVEL, 0f), SceneSwitchState.Loading(LEVEL, 1f), SceneSwitchState.Ready(LEVEL)), seen.states, "for a step of $delta")
            game.frames(FRAMES)
            assertEquals(SceneSwitchState.Ready(LEVEL), game.status.state.value, "ready stays until the next request")
            project.close()
        }
    }

    @Test
    fun theScenePlaysOnWhileTheNextOneLoads() = runTest {
        val source = Indexed(mapOf(MANIFEST_PATH to MANIFEST, MENU to MENU_SCENE, LEVEL to LEVEL_SCENE, LEVEL_A to ByteArray(A_SIZE), LEVEL_B to ByteArray(B_SIZE)))
        val project = loadProject(source.open())
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project)
        source.hold(LEVEL, LEVEL_A, LEVEL_B)

        game.switch.request(LEVEL)
        game.frames(FRAMES)

        assertEquals("menu", project.scene.name, "the menu plays while the level's files are on their way")
        assertNotNull(game.world.find("Title"))
        assertEquals(SceneSwitchState.Loading(LEVEL, 0f), game.status.state.value)
        project.close()
    }

    @Test
    fun aSceneThatCannotBeReadOrCheckedEndsInFailedAndTheOldSceneKeepsPlaying() = runTest {
        for (target in listOf(MISSING, BROKEN, UNDRAWABLE, ESCAPES)) {
            val project = loadProject(files())
            project.sceneLoads = Dispatchers.Unconfined
            val game = play(project)
            val errors = Errors()
            Log.install(errors)
            try {
                game.switch.request(target)
                game.frames(FRAMES)
            } finally {
                Log.remove(errors)
            }

            val failed = assertIs<SceneSwitchState.Failed>(game.status.state.value, "for $target")
            assertEquals(target, failed.path)
            assertTrue(!failed.cause.message.isNullOrBlank(), "for $target, the cause says why: ${failed.cause}")
            assertEquals(1, errors.records.size, "for $target, it is logged once as well")
            assertEquals("menu", project.scene.name, "for $target, the menu plays on")
            assertNotNull(game.world.find("Title"))
            assertNull(game.world.find("Ghost"))
            project.close()
        }
    }

    @Test
    fun aFailedSwitchIsFollowedByAGoodOne() = runTest {
        val project = loadProject(files())
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project)
        val seen = Seen(game.status)

        game.switch.request(MISSING)
        game.frames(1)
        seen.record()
        game.switch.request(LEVEL)
        seen.record()
        game.frames(FRAMES)
        seen.record()

        assertEquals(
            listOf(
                SceneSwitchState.Idle,
                SceneSwitchState.Failed(MISSING, assertIs<SceneSwitchState.Failed>(seen.states[1]).cause),
                SceneSwitchState.Loading(LEVEL, 0f),
                SceneSwitchState.Ready(LEVEL),
            ),
            seen.states,
        )
        project.close()
    }

    @Test
    fun progressNeverDecreasesAndReachesOneBeforeReady() = runTest {
        val source = Indexed(mapOf(MANIFEST_PATH to MANIFEST, MENU to MENU_SCENE, LEVEL to LEVEL_SCENE, LEVEL_A to ByteArray(A_SIZE), LEVEL_B to ByteArray(B_SIZE)))
        val project = loadProject(source.open())
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project)
        source.hold(LEVEL, LEVEL_A, LEVEL_B)
        val seen = Seen(game.status)

        game.switch.request(LEVEL)
        seen.record()
        // The scene file arrives first, as the load reads it to find what it names; then each file it names.
        for (path in listOf(LEVEL, LEVEL_A, LEVEL_B)) {
            source.release(path)
            game.update()
            seen.record()
        }
        game.frames(FRAMES)
        seen.record()

        val progress = seen.states.filterIsInstance<SceneSwitchState.Loading>().map { it.progress }
        assertEquals(progress.sorted(), progress, "progress never decreases: $progress")
        assertEquals(0f, progress.first())
        assertEquals(1f, progress.last())
        assertTrue(progress.any { it > 0f && it < 1f }, "bytes are counted on the way: $progress")
        assertEquals(SceneSwitchState.Ready(LEVEL), seen.states.last())
        assertEquals(SceneSwitchState.Loading::class, seen.states[seen.states.lastIndex - 1]::class, "it was 1 before it was ready")
        project.close()
    }

    @Test
    fun aSourceThatCannotReportBytesJumpsFromZeroToOne() = runTest {
        val loose = Indexed(mapOf(MANIFEST_PATH to MANIFEST, MENU to MENU_SCENE, LEVEL to LEVEL_SCENE, LEVEL_A to ByteArray(A_SIZE), LEVEL_B to ByteArray(B_SIZE)))
        // An index with no cache: a prefetch would be thrown away, so it is not done.
        val project = loadProject(loose.open(cache = null))
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project)
        val seen = Seen(game.status)
        loose.fetches.clear()

        game.switch.request(LEVEL)
        seen.record()
        game.frames(1)
        seen.record()
        game.frames(FRAMES)
        seen.record()

        assertEquals(listOf(SceneSwitchState.Idle, SceneSwitchState.Loading(LEVEL, 0f), SceneSwitchState.Loading(LEVEL, 1f), SceneSwitchState.Ready(LEVEL)), seen.states)
        assertEquals(listOf("files/$LEVEL"), loose.fetches, "the level's scene is fetched once and its named files not at all")
        project.close()
    }

    @Test
    fun progressThroughTheAssetLockCountsTheIndexsBytes() = runTest {
        val source = Indexed(
            mapOf(MANIFEST_PATH to MANIFEST, MENU to MENU_SCENE, LEVEL to LEVEL_SCENE, LEVEL_A to ByteArray(A_SIZE), LEVEL_B to ByteArray(B_SIZE), PROJECT_ASSETS_LOCK to LOCK),
        )
        val mismatches = mutableListOf<AssetMismatch>()
        val project = loadProject(source.open().verifiedByAssetsLock { mismatches += it })
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project)
        source.hold(LEVEL, LEVEL_A, LEVEL_B)
        val seen = Seen(game.status)

        game.switch.request(LEVEL)
        seen.record()
        for (path in listOf(LEVEL, LEVEL_A, LEVEL_B)) {
            source.release(path)
            game.update()
            seen.record()
        }
        game.frames(FRAMES)
        seen.record()

        val progress = seen.states.filterIsInstance<SceneSwitchState.Loading>().map { it.progress }
        assertEquals(progress.sorted(), progress, "progress never decreases: $progress")
        assertEquals(listOf(0f, 1f), listOf(progress.first(), progress.last()))
        assertTrue(progress.any { it > 0f && it < 1f }, "the lock passes the index's bytes on, so they're counted on the way: $progress")
        assertEquals(SceneSwitchState.Ready(LEVEL), seen.states.last())
        assertEquals("level", project.scene.name)
        assertTrue(mismatches.isEmpty(), "each file is still checked against the lock, and matches: $mismatches")
        project.close()
    }

    @Test
    fun loadProjectThroughTheAssetLockReportsHowFarTheEntrySceneHasCome() = runTest {
        val source = Indexed(mapOf(MANIFEST_PATH to MANIFEST, MENU to MENU_SCENE, MENU_A to ByteArray(A_SIZE), MENU_B to ByteArray(B_SIZE), PROJECT_ASSETS_LOCK to LOCK))
        val reported = mutableListOf<Float>()

        loadProject(source.open().verifiedByAssetsLock(), onProgress = { reported += it }).close()

        assertEquals(0f, reported.first())
        assertEquals(1f, reported.last())
        assertTrue(reported.size > 2 && reported.zipWithNext().all { (before, after) -> after > before }, "it only rises, with bytes on the way: $reported")
    }

    @Test
    fun theAssetLockOverASourceThatCannotReportBytesJumpsFromZeroToOne() = runTest {
        val texts = mapOf(MANIFEST_PATH to MANIFEST, MENU to MENU_SCENE, LEVEL to LEVEL_SCENE, PROJECT_ASSETS_LOCK to LOCK)
        val files = AssetSource { path -> runCatching { texts.getValue(path.value).encodeToByteArray() } }
        val wrapped = files.verifiedByAssetsLock()
        assertIs<LockVerifiedAssetSource>(wrapped, "the files are wrapped, not passed through")
        val reported = mutableListOf<Float>()
        val project = loadProject(wrapped, onProgress = { reported += it })
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project)
        val seen = Seen(game.status)

        game.switch.request(LEVEL)
        seen.record()
        game.frames(1)
        seen.record()
        game.frames(FRAMES)
        seen.record()

        assertEquals(listOf(0f, 1f), reported, "the first load")
        assertEquals(listOf(SceneSwitchState.Idle, SceneSwitchState.Loading(LEVEL, 0f), SceneSwitchState.Loading(LEVEL, 1f), SceneSwitchState.Ready(LEVEL)), seen.states, "the switch")
        project.close()
    }

    @Test
    fun askingForAnotherSceneWhileOneLoadsReplacesTheTarget() = runTest {
        val source = Indexed(
            mapOf(MANIFEST_PATH to MANIFEST, MENU to MENU_SCENE, LEVEL to LEVEL_SCENE, OTHER to OTHER_SCENE, LEVEL_A to ByteArray(A_SIZE), LEVEL_B to ByteArray(B_SIZE)),
        )
        val project = loadProject(source.open())
        project.sceneLoads = Dispatchers.Unconfined
        val game = play(project)
        source.hold(LEVEL)
        val seen = Seen(game.status)

        game.switch.request(LEVEL)
        game.switch.request(LEVEL)
        seen.record()
        assertEquals(listOf("files/$LEVEL"), source.fetches.filter { it.endsWith("level.scene.json") }, "asking again for the same scene changes nothing")
        game.switch.request(OTHER)
        seen.record()
        source.release(LEVEL)
        game.frames(1)
        seen.record()
        game.frames(FRAMES)
        seen.record()

        assertEquals("other", project.scene.name, "the last request is the one that plays")
        assertEquals(
            listOf(SceneSwitchState.Idle, SceneSwitchState.Loading(LEVEL, 0f), SceneSwitchState.Loading(OTHER, 0f), SceneSwitchState.Loading(OTHER, 1f), SceneSwitchState.Ready(OTHER)),
            seen.states,
        )
        project.close()
    }

    @Test
    fun loadProjectReportsHowFarTheEntrySceneHasCome() = runTest {
        val source = Indexed(mapOf(MANIFEST_PATH to MANIFEST, MENU to MENU_SCENE, MENU_A to ByteArray(A_SIZE), MENU_B to ByteArray(B_SIZE)))
        val reported = mutableListOf<Float>()

        loadProject(source.open(), onProgress = { reported += it }).close()

        assertEquals(0f, reported.first())
        assertEquals(1f, reported.last())
        assertTrue(reported.size > 2 && reported.zipWithNext().all { (before, after) -> after > before }, "it only rises, with bytes on the way: $reported")
    }

    @Test
    fun loadProjectOfASourceThatCannotReportBytesReportsZeroThenOne() = runTest {
        val reported = mutableListOf<Float>()

        loadProject(files(), onProgress = { reported += it }).close()

        assertEquals(listOf(0f, 1f), reported)
    }

    @Test
    fun theSwitchStatusIsAServiceAndReachesTheSystems() = runTest {
        val probe = ProbeCapability()
        val project = loadProject(files(), listOf(probe))
        val game = play(project)

        assertSame(game.status, assertNotNull(probe.services.single().sceneSwitchStatus))
        assertSame(game.switch, probe.services.single().sceneSwitch)
        project.close()
    }

    private class Game(val app: AwakeAppLifecycle) {
        val runtime: SceneAppLifecycleRuntime = app.requireService()
        val switch: SceneSwitch = app.requireService()
        val status: SceneSwitchStatus = app.requireService()
        val world: World get() = runtime.world

        fun update(delta: Float = DELTA) = app.update(delta, WIDTH, HEIGHT)

        fun frames(count: Int) = repeat(count) { update() }
    }

    /** The distinct states a test saw, in order, each time it looked. */
    private class Seen(private val status: SceneSwitchStatus) {
        val states = mutableListOf(status.state.value)

        fun record() {
            val now = status.state.value
            if (states.last() != now) states += now
        }
    }

    private suspend fun play(project: LoadedProject): Game {
        val app = app { scene("play") { runProject(project) } }
        app.ready(TestRenderer())
        return Game(app).also { it.frames(1) }
    }

    private fun World.find(name: String): Entity? {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return found
    }

    private fun files(): AssetSource {
        val texts = mapOf(MANIFEST_PATH to MANIFEST, MENU to MENU_SCENE, LEVEL to LEVEL_SCENE, BROKEN to BROKEN_SCENE, UNDRAWABLE to UNDRAWABLE_SCENE)
        return AssetSource { path -> runCatching { texts.getValue(path.value).encodeToByteArray() } }
    }

    /**
     * A published project: [contents] as an index lists them, fetched by the test, which can [hold] a
     * file until it is [release]d. Text is given as a string, anything else as bytes.
     */
    private class Indexed(contents: Map<String, Any>) {
        private val bytes = contents.mapValues { (_, value) -> value as? ByteArray ?: (value as String).encodeToByteArray() }
        private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val fetches = mutableListOf<String>()

        fun open(cache: ContentCache? = MemoryCache()): IndexedAssetSource {
            val files = bytes.map { (path, content) ->
                ProjectIndexEntry(path = path, sizeBytes = content.size.toLong(), sha256 = Sha256.digestHex(content), url = "files/$path")
            }
            return IndexedAssetSource(
                ProjectIndex(files = files),
                { url ->
                    fetches += url
                    gates[url.removePrefix("files/")]?.await()
                    bytes.getValue(url.removePrefix("files/"))
                },
                cache,
            )
        }

        fun hold(vararg paths: String) = paths.forEach { gates[it] = CompletableDeferred() }

        fun release(path: String) {
            gates.getValue(path).complete(Unit)
        }
    }

    private class MemoryCache : ContentCache {
        private val stored = mutableMapOf<String, ByteArray>()

        override suspend fun get(sha256: String): ByteArray? = stored[sha256]

        override suspend fun put(sha256: String, bytes: ByteArray) {
            stored[sha256] = bytes
        }
    }

    /** Collects the error records a test logs. */
    private class Errors : LogSink {
        val records = mutableListOf<LogRecord>()

        override fun emit(record: LogRecord) {
            if (record.level == LogLevel.Error) records += record
        }
    }

    /** Keeps the services each scene's systems were given. */
    private class ProbeCapability : SceneCapability {
        override val id = "com.example.harbor-town.probe"
        val services = mutableListOf<SceneHostServices>()

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            plan.fixed("probe") { host ->
                services += host
                object : System {
                    override fun update(world: World, delta: Float) = Unit
                }
            }
        }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 3
        const val A_SIZE = 100
        const val B_SIZE = 300

        const val MANIFEST_PATH = "awake.project.json"
        const val MENU = "scenes/menu.scene.json"
        const val LEVEL = "scenes/level.scene.json"
        const val OTHER = "scenes/other.scene.json"
        const val MISSING = "scenes/missing.scene.json"
        const val BROKEN = "scenes/broken.scene.json"
        const val UNDRAWABLE = "scenes/undrawable.scene.json"
        const val ESCAPES = "../outside.scene.json"
        const val MENU_A = "assets/menu-a.bin"
        const val MENU_B = "assets/menu-b.bin"
        const val LEVEL_A = "assets/level-a.bin"
        const val LEVEL_B = "assets/level-b.bin"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/menu.scene.json"}"""

        // The scene names its files in node names: whichever field holds a project path, the files count.
        const val MENU_SCENE = """
{ "version": 1, "name": "menu", "nodes": [
  { "name": "MenuCamera", "components": [ { "component": "camera" } ] },
  { "name": "Title" },
  { "name": "assets/menu-a.bin" },
  { "name": "assets/menu-b.bin" }
] }
"""

        const val LEVEL_SCENE = """
{ "version": 1, "name": "level", "nodes": [
  { "name": "LevelCamera", "components": [ { "component": "camera" } ] },
  { "name": "assets/level-a.bin" },
  { "name": "assets/level-b.bin" }
] }
"""

        /** Pins the manifest and the scenes, as `assets.lock.json` does; the files they name go unpinned. */
        val LOCK = AwakeProjectValidator.encodeAssetsLock(
            AwakeAssetsLock(
                assets = listOf(MANIFEST_PATH to MANIFEST, MENU to MENU_SCENE, LEVEL to LEVEL_SCENE).associate { (path, text) ->
                    path to text.encodeToByteArray().let { AwakeAssetLockEntry(Sha256.digestHex(it), it.size.toLong()) }
                },
            ),
        )

        const val OTHER_SCENE = """
{ "version": 1, "name": "other", "nodes": [
  { "name": "OtherCamera", "components": [ { "component": "camera" } ] }
] }
"""

        const val BROKEN_SCENE = """
{ "version": 1, "name": "broken", "nodes": [
  { "name": "Ghost", "components": [ { "component": "grappling_hook" } ] }
] }
"""

        const val UNDRAWABLE_SCENE = """
{ "version": 1, "name": "undrawable", "nodes": [
  { "name": "Ghost", "components": [ { "component": "meshRenderer", "mesh": "no-such-mesh", "material": "lit-shadow" } ] }
] }
"""
    }
}
