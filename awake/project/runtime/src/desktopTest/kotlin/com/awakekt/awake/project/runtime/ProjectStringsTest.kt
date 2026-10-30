/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.localization.LocaleId
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.core.localization.systemLocale
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A project's `strings/` tables, loaded with it and shown by its scenes' keyed canvas text, with no game code. */
class ProjectStringsTest {
    @Test
    fun aPlayedProjectShowsItsKeyedTextFromItsStringsAndSwitchesLocale() = runTest {
        val project = loadProject(project(KEYED_SCENE))
        val strings = assertNotNull(project.strings, "strings/en.json is there, so the project has strings")
        // The device's locale is whatever the machine running this has; start from a known one.
        strings.setLocale(LocaleId.English)
        val (game, runtime) = play(project)
        assertSame(strings, runtime.canvasStrings, "the runtime's canvas shows the project's strings")

        val english = runtime.glyphs()
        strings.setLocale(LocaleId.of("fil"))
        game.update(FRAME, WIDTH, HEIGHT)
        val filipino = runtime.glyphs()

        assertEquals(drawn("Start"), english)
        assertEquals(drawn("Simulan"), filipino, "a frame after the switch, with the scene still playing")
    }

    @Test
    fun onlyTheActiveAndDefaultLocalesTablesAreRead() = runTest {
        val reads = mutableListOf<String>()

        loadProject(recording(reads, project(KEYED_SCENE)))

        val active = (systemLocale().fallbackChain + LocaleId.English).map { "strings/${it.tag}.json" }.toSet()
        val tables = reads.filter { it.startsWith("strings/") }.toSet()
        assertTrue("strings/en.json" in tables, "the default locale's table is read: $tables")
        assertTrue(tables.all { it in active }, "no other locale's table is read until it is used: $tables")
    }

    @Test
    fun aProjectWithNoStringsHasNoneAndItsKeyedTextShowsTheKey() = runTest {
        val files = project(KEYED_SCENE)
        val project = loadProject(AssetSource { path -> if (path.value.startsWith("strings/")) Result.failure(IllegalStateException("no ${path.value}")) else files.read(path) })

        assertNull(project.strings)
        val (_, runtime) = play(project)
        assertEquals(drawn("menu.start"), runtime.glyphs())
    }

    @Test
    fun aHostWithNoRendererStillHasTheStrings() = runTest {
        val project = loadProject(project(KEYED_SCENE), hasRenderer = false)

        assertEquals("Start", assertNotNull(project.strings).run { setLocale(LocaleId.English); text("menu.start") })
    }

    @Test
    fun textTheAppReadBeforeLoadingAndTheProjectsComeFromOneInstanceThatSwitchesTogether() = runTest {
        val early = appStrings(LocaleId.English)
        assertEquals("The server refused the session.", early.text("connect.refused"), "read before the project loads")

        val project = loadProject(project(KEYED_SCENE), strings = early)

        assertSame(early, project.strings, "the project's text is added to the app's instance, not a second one")
        assertEquals("The server refused the session.", early.text("connect.refused"))
        assertEquals("Start", early.text("menu.start"), "the project's key wins over the app's early one")
        val (game, runtime) = play(project)
        assertSame(early, runtime.canvasStrings, "the playing scene shows the same instance")
        assertEquals(drawn("Start"), runtime.glyphs())

        early.setLocale(LocaleId.of("fil"))
        game.update(FRAME, WIDTH, HEIGHT)

        assertEquals("Tinanggihan ng server ang session.", early.text("connect.refused"))
        assertEquals("Simulan", early.text("menu.start"))
        assertEquals(drawn("Simulan"), runtime.glyphs(), "one setLocale changed the app's text and the scene's")
    }

    @Test
    fun theLocaleTheAppChoseIsKept() = runTest {
        val early = appStrings(LocaleId.of("fil"))

        loadProject(project(KEYED_SCENE), hasRenderer = false, strings = early)

        assertEquals(LocaleId.of("fil"), early.locale.value, "not the device's locale")
        assertEquals("Simulan", early.text("menu.start"))
    }

    @Test
    fun theAppsEarlyKeysAreOverriddenOnlyWithinALocale() = runTest {
        // The app has Korean for menu.start; the project's Korean table wins over it, and a key only
        // the app has in a locale stays the app's.
        val early = Strings.load(
            StringTableSource.of(
                mapOf(
                    LocaleId.English to mapOf("menu.start" to "Begin"),
                    LocaleId.of("ko") to mapOf("menu.start" to "앱 시작", "connect.refused" to "서버가 거부했습니다"),
                ),
            ),
            locale = LocaleId.of("ko"),
        )

        loadProject(project(KEYED_SCENE), hasRenderer = false, strings = early)

        assertEquals("시작", early.text("menu.start"), "the project's Korean over the app's Korean")
        assertEquals("서버가 거부했습니다", early.text("connect.refused"))
        early.setLocale(LocaleId.English)
        assertEquals("Start", early.text("menu.start"), "the project's English over the app's English")
    }

    @Test
    fun aPhysicsWorldPassedAsATrailingLambdaStillBindsToPhysicsWorld() = runTest {
        val early = appStrings(LocaleId.English)

        // A scene with no bodies never calls it; this checks only which parameter the lambda fills.
        val project = loadProject(project(KEYED_SCENE), hasRenderer = false, strings = early) { error("no bodies, no world") }

        assertSame(early, project.strings)
    }

    @Test
    fun aLoadThatFailsLeavesTheAppsStringsAsTheyWere() = runTest {
        val early = appStrings(LocaleId.English)

        assertFailsWith<IllegalArgumentException> {
            loadProject(project("""{ "version": 1, "name": "broken", "nodes": [ { "name": "x", "components": [ { "component": "no_such_component" } ] } ] }"""), strings = early)
        }

        assertEquals("Early start", early.text("menu.start"), "the project's tables weren't added")
    }

    @Test
    fun withoutTheAppsStringsTheProjectHasItsOwnAndLeavesTheAppsAlone() = runTest {
        val early = appStrings(LocaleId.English)

        val project = loadProject(project(KEYED_SCENE), hasRenderer = false)

        val own = assertNotNull(project.strings)
        assertTrue(own !== early, "a Strings of the project's own")
        assertEquals("Early start", early.text("menu.start"))
        assertTrue(!own.contains("connect.refused"))
    }

    @Test
    fun loadingTheProjectAgainAndClosingTheFirstLeavesOneCopyOfItsTables() = runTest {
        val early = appStrings(LocaleId.English)
        val firstReads = mutableListOf<String>()
        val secondReads = mutableListOf<String>()
        val first = loadProject(recording(firstReads, project(KEYED_SCENE)), hasRenderer = false, strings = early)
        val second = loadProject(recording(secondReads, project(KEYED_SCENE)), hasRenderer = false, strings = early)

        first.close()

        assertEquals("Start", early.text("menu.start"), "the second load's tables are still there")
        firstReads.clear()
        secondReads.clear()
        early.setLocale(LocaleId.of("fil"))
        assertEquals(emptyList(), firstReads.filter { it.startsWith("strings/") }, "the closed project's tables are gone")
        assertEquals(listOf("strings/fil.json"), secondReads.filter { it.startsWith("strings/") }, "one copy, read once")
        assertEquals("Simulan", early.text("menu.start"))

        second.close()

        assertEquals("Maagang simula", early.text("menu.start"), "no copy of the project's tables is left")
    }

    @Test
    fun closingTheProjectKeepsTheAppsOwnTables() = runTest {
        val early = appStrings(LocaleId.of("fil"))
        val project = loadProject(project(KEYED_SCENE), hasRenderer = false, strings = early)
        assertEquals("Simulan", early.text("menu.start"))

        project.close()

        assertSame(early, project.strings)
        assertEquals("Maagang simula", early.text("menu.start"), "the app's key shows again")
        assertEquals("Tinanggihan ng server ang session.", early.text("connect.refused"))
        assertEquals(LocaleId.of("fil"), early.locale.value, "the app's locale is kept")
        early.setLocale(LocaleId.English)
        assertEquals("Early start", early.text("menu.start"))
        assertEquals("The server refused the session.", early.text("connect.refused"))
    }

    @Test
    fun closingTwiceRemovesNothingMore() = runTest {
        val early = appStrings(LocaleId.English)
        val first = loadProject(project(KEYED_SCENE), hasRenderer = false, strings = early)
        first.close()
        val second = loadProject(project(KEYED_SCENE), hasRenderer = false, strings = early)

        first.close()

        assertTrue(first.closed)
        assertEquals("Start", early.text("menu.start"), "a second close leaves the next load's tables alone")
        assertEquals("The server refused the session.", early.text("connect.refused"))
        second.close()
        second.close()
        assertEquals("Early start", early.text("menu.start"))
    }

    @Test
    fun aLoadCancelledWhileItAddsTheTablesTakesThemOutAgain() = runTest {
        val early = appStrings(LocaleId.English)
        val files = project(KEYED_SCENE)
        val reading = CompletableDeferred<Unit>()
        var stall = true
        val stalling = AssetSource { path ->
            if (stall && path.value.startsWith("strings/")) {
                reading.complete(Unit)
                awaitCancellation()
            }
            files.read(path)
        }
        val load = launch { loadProject(stalling, hasRenderer = false, strings = early) }
        reading.await()

        load.cancelAndJoin()
        stall = false

        assertEquals("Early start", early.text("menu.start"))
        early.setLocale(LocaleId.of("fil"))
        assertEquals("Maagang simula", early.text("menu.start"), "the cancelled load's tables aren't read for another locale")
    }

    @Test
    fun aProjectWithItsOwnStringsStillHasThemAfterClosing() = runTest {
        val early = appStrings(LocaleId.English)
        val project = loadProject(project(KEYED_SCENE), hasRenderer = false)
        val own = assertNotNull(project.strings)

        project.close()
        project.close()

        own.setLocale(LocaleId.English)
        assertEquals("Start", own.text("menu.start"), "closing leaves a project's own Strings as it was")
        assertEquals("Early start", early.text("menu.start"))
    }

    /** What a game builds before its project loads, for a connection screen's text. */
    private suspend fun appStrings(locale: LocaleId): Strings = Strings.load(
        StringTableSource.of(
            mapOf(
                LocaleId.English to mapOf("connect.refused" to "The server refused the session.", "menu.start" to "Early start"),
                LocaleId.of("fil") to mapOf("connect.refused" to "Tinanggihan ng server ang session.", "menu.start" to "Maagang simula"),
            ),
        ),
        locale = locale,
    )

    /** What the scene draws with [text] as a literal, for comparing keyed text against. */
    private suspend fun drawn(text: String): List<UiDrawPrimitive.Glyph> {
        val (_, runtime) = play(loadProject(project(literalScene(text))))
        return runtime.glyphs()
    }

    private suspend fun play(project: LoadedProject): Pair<AwakeAppLifecycle, SceneAppLifecycleRuntime> {
        val game = app { scene("play") { runProject(project) } }
        game.ready(DocsRenderer())
        game.update(FRAME, WIDTH, HEIGHT)
        return game to game.requireService()
    }

    private fun SceneAppLifecycleRuntime.glyphs(): List<UiDrawPrimitive.Glyph> = uiPrimitives.filterIsInstance<UiDrawPrimitive.Glyph>()

    private fun recording(reads: MutableList<String>, files: AssetSource) = AssetSource { path: AssetPath ->
        reads += path.value
        files.read(path)
    }

    private companion object {
        const val KEYED_SCENE = """{ "version": 1, "name": "menu", "nodes": [
            { "name": "Play", "components": [ { "component": "canvas_element", "kind": "Button", "textKey": "menu.start", "width": 200, "height": 40 } ] }
        ] }"""

        fun literalScene(text: String) = KEYED_SCENE.replace("\"textKey\": \"menu.start\"", "\"text\": \"$text\"")

        fun project(scene: String) = AssetSource { path ->
            runCatching {
                when (path.value) {
                    "awake.project.json" ->
                        """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
                    "scenes/main.scene.json" -> scene
                    "strings/en.json" -> """{ "menu": { "start": "Start" } }"""
                    "strings/fil.json" -> """{ "menu": { "start": "Simulan" } }"""
                    "strings/ko.json" -> """{ "menu": { "start": "시작" } }"""
                    else -> error("no ${path.value}")
                }.encodeToByteArray()
            }
        }
    }
}
