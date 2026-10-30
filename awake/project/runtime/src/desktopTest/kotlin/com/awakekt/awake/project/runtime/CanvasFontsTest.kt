/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A `canvas_element`'s `font` file, loaded with the project and drawn by the runtime's own canvas. */
class CanvasFontsTest {
    @Test
    fun aPlayedProjectDrawsInTheFontFileItsElementsNameAndTheRestInTheDefault() = runTest {
        val reads = mutableListOf<String>()
        val (game, runtime) = play(loadProject(recording(reads, PROJECT)))
        val loaded = assertNotNull(runtime.canvasFonts[FONT], "the font file loaded")
        val first = runtime.atlasesByRow()
        game.update(FRAME, WIDTH, HEIGHT)
        val second = runtime.atlasesByRow()

        assertEquals(
            mapOf(0 to setOf(loaded), 1 to setOf(UiFonts.default()), 2 to setOf(loaded)),
            first.drawnBy(loaded, UiFonts.default()),
            "the title and the button in the file, the body in the default",
        )
        for (row in 0..2) {
            assertTrue(first.getValue(row).zip(second.getValue(row)).all { (a, b) -> a === b }, "the same atlases next frame, so each uploads once")
        }
        assertEquals(1, reads.count { it == FONT }, "read once for the two elements that name it")
    }

    @Test
    fun aHostWithNoRendererReadsNoFontFile() = runTest {
        val reads = mutableListOf<String>()

        val headless = loadProject(recording(reads, PROJECT), hasRenderer = false)
        assertFalse(FONT in reads, "a host that draws nothing reads no font")
        assertNull(headless.content[CoreSceneContent.CanvasFonts])

        val drawn = loadProject(recording(reads, PROJECT))
        assertTrue(FONT in reads, "the control: a host that draws reads it")
        assertNotNull(drawn.content[CoreSceneContent.CanvasFonts])
    }

    @Test
    fun aMissingFontFileWarnsOnceAndItsElementsDrawInTheDefault() = runTest {
        assertWarnsOnceAndDrawsInTheDefault(Result.failure(IllegalStateException("no such file")), "no such file")
    }

    @Test
    fun aCorruptFontFileWarnsOnceAndItsElementsDrawInTheDefault() = runTest {
        // Cut short past its table directory, so the loader gets as far as a table and names it.
        assertWarnsOnceAndDrawsInTheDefault(Result.success(SUBSET.copyOf(SUBSET.size / 2)), "table")
    }

    private suspend fun assertWarnsOnceAndDrawsInTheDefault(file: Result<ByteArray>, reason: String) {
        val files = AssetSource { path -> if (path.value == FONT) file else PROJECT.read(path) }
        val warnings = mutableListOf<String>()
        val sink = LogSink { if (it.level == LogLevel.Warn && FONT in it.message) warnings += it.message }
        Log.install(sink)
        try {
            val (game, runtime) = play(loadProject(files))
            game.update(FRAME, WIDTH, HEIGHT)

            val warning = warnings.single()
            assertTrue(reason in warning, "the warning names the path and why: $warning")
            val default = setOf(UiFonts.default())
            assertEquals(mapOf(0 to default, 1 to default, 2 to default), runtime.atlasesByRow().drawnBy(UiFonts.default()))
        } finally {
            Log.remove(sink)
        }
    }

    private suspend fun play(project: LoadedProject): Pair<AwakeAppLifecycle, SceneAppLifecycleRuntime> {
        val game = app { scene("play") { runProject(project) } }
        game.ready(DocsRenderer())
        game.update(FRAME, WIDTH, HEIGHT)
        return game to game.requireService()
    }

    /** The atlases last frame's glyphs drew from, in order, by the [ROW]-tall band, and so the element, each lies in. */
    private fun SceneAppLifecycleRuntime.atlasesByRow(): Map<Int, List<UiFont?>> =
        uiPrimitives.filterIsInstance<UiDrawPrimitive.Glyph>().groupBy({ (it.y / ROW).toInt() }, { it.font })

    /**
     * Each row's atlases as the [fonts] they belong to: a font itself, or one of its exact-size glyph pages,
     * which small text draws from. A page's cell size is the pixel size it was rasterized at.
     */
    private fun Map<Int, List<UiFont?>>.drawnBy(vararg fonts: UiFont): Map<Int, Set<UiFont?>> = mapValues { (_, atlases) ->
        atlases.map { atlas ->
            fonts.firstOrNull { font ->
                font === atlas || (atlas != null && "Hg".any { font.pixelGlyphs?.glyph(it.code, FontWeight.Normal, atlas.cellSize)?.atlas === atlas })
            } ?: atlas
        }.toSet()
    }

    private fun recording(reads: MutableList<String>, files: AssetSource) = AssetSource { path: AssetPath ->
        reads += path.value
        files.read(path)
    }

    private companion object {
        const val FONT = "fonts/test.ttf"
        const val ROW = 40

        /** The OFL Roboto subset `awake:core:text`'s loader tests use, read where it lies, its licence beside it. */
        val SUBSET = File("../../core/text/src/commonTest/resources/fonts/roboto-subset.ttf").readBytes()

        val PROJECT = AssetSource { path ->
            runCatching {
                when (path.value) {
                    "awake.project.json" ->
                        """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
                            .encodeToByteArray()
                    "scenes/main.scene.json" -> """
                        { "version": 1, "name": "hud", "nodes": [
                          { "name": "Title", "components": [ { "component": "canvas_element", "text": "Hg", "offsetY": 0, "height": $ROW, "font": "$FONT" } ] },
                          { "name": "Body", "components": [ { "component": "canvas_element", "text": "Hg", "offsetY": ${ROW * 1}, "height": $ROW } ] },
                          { "name": "Play", "components": [ { "component": "canvas_element", "kind": "Button", "text": "Hg", "offsetY": ${ROW * 2},
                            "height": $ROW, "font": "$FONT" } ] }
                        ] }
                    """.trimIndent().encodeToByteArray()
                    FONT -> SUBSET
                    else -> error("no ${path.value}")
                }
            }
        }
    }
}
