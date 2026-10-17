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
import com.awakekt.awake.core.localization.systemLocale
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
