/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.asset.shaderdocument.ShaderDocuments
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * An effect whose document has not been loaded yet, as in an editor that has not opened it, is
 * reported as not drawn yet and draws once it arrives; one whose document failed to load is reported
 * as failed, as before.
 */
class ShaderEffectAwaitedDocumentTest {
    private val sky = ShaderDocuments.compile(ShaderEffectFixtures.SKY)
    private val records = mutableListOf<LogRecord>()
    private val sink = LogSink { records += it }
    private val world = World()
    private val host = RecordingHost()

    @BeforeTest
    fun listen() {
        Log.reset()
        Log.install(sink)
    }

    @AfterTest
    fun stopListening() {
        Log.reset()
    }

    private fun shaderLog(level: LogLevel): List<String> = records.filter { it.tag == "scene-shader" && it.level == level }.map { it.message }

    @Test
    fun aDocumentNotLoadedYetIsReportedOnceAsNotDrawnYetAndDrawsWhenItArrives() {
        val system = ShaderEffectSystem(host, ShaderEffectAssets.Empty)
        world.create().also { world.add(it, ShaderEffectSource(SceneShaderEffect("sky.shader.json"))) }

        repeat(3) { system.update(world, STEP) }
        system.assets = ShaderEffectAssets(textures = mapOf("unrelated.png" to PIXEL))
        repeat(2) { system.update(world, STEP) }

        assertEquals(emptyList(), shaderLog(LogLevel.Error), "nothing failed")
        val waiting = shaderLog(LogLevel.Info)
        assertEquals(1, waiting.size, "once, however many frames and new assets: $waiting")
        assertTrue("'sky.shader.json': no document for it has been loaded yet. Not drawn yet" in waiting.single(), waiting.single())
        assertTrue(host.live.isEmpty())

        system.assets = ShaderEffectAssets(mapOf("sky.shader.json" to sky))
        repeat(2) { system.update(world, STEP) }

        assertTrue(host.live.single().drawFrame().drew, "drawn once its document is in")
        system.close()
    }

    @Test
    fun aDocumentThatFailedToLoadIsReportedAsFailed() {
        val system = ShaderEffectSystem(host, ShaderEffectAssets(failed = setOf("sky.shader.json")))
        world.create().also { world.add(it, ShaderEffectSource(SceneShaderEffect("sky.shader.json"))) }

        repeat(2) { system.update(world, STEP) }

        val errors = shaderLog(LogLevel.Error)
        assertTrue(errors.single().endsWith("'sky.shader.json': the document did not load. Not drawn."), errors.toString())
        assertEquals(emptyList(), shaderLog(LogLevel.Info))
        system.close()
    }

    @Test
    fun loadingRecordsTheDocumentsThatFailedAndNotThoseThatLoaded() = runTest {
        val files = ShaderEffectFixtures.Files(
            mapOf("sky.shader.json" to ShaderEffectFixtures.SKY, "broken.shader.json" to ShaderEffectFixtures.BROKEN),
        )
        val scene = SceneDocument(
            nodes = listOf("sky.shader.json", "broken.shader.json", "absent.shader.json").mapIndexed { index, path ->
                SceneNode("node$index", components = listOf(SceneShaderEffect(path)))
            },
        )

        val loaded = loadShaderEffects(scene, files)

        assertEquals(setOf("sky.shader.json"), loaded.documents.keys)
        assertEquals(setOf("broken.shader.json", "absent.shader.json"), loaded.failed)
    }

    @Test
    fun assetsLaidOverOthersReplaceTheirFilesAndClearAFailureTheyLoad() {
        val pool = ShaderDocuments.compile(ShaderEffectFixtures.POOL)
        val read = ShaderEffectAssets(
            documents = mapOf("sky.shader.json" to sky),
            textures = mapOf("ripples.png" to PIXEL),
            failed = setOf("pool.shader.json", "glow.shader.json"),
        )
        val edited = TextureAsset(ByteArray(4), width = 1, height = 1)
        val editor = ShaderEffectAssets(documents = mapOf("pool.shader.json" to pool), textures = mapOf("ripples.png" to edited))

        val merged = read + editor

        assertEquals(mapOf("sky.shader.json" to sky, "pool.shader.json" to pool), merged.documents)
        assertSame(edited, merged.textures["ripples.png"])
        assertEquals(setOf("glow.shader.json"), merged.failed)
    }

    private companion object {
        const val STEP = 1f / 60f
        val PIXEL = TextureAsset(ByteArray(4) { -1 }, width = 1, height = 1)
    }
}
