/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.project.ContentBundleWriter
import com.awakekt.awake.project.ContentCache
import com.awakekt.awake.project.IndexedAssetSource
import com.awakekt.awake.project.ProjectIndex
import com.awakekt.awake.project.ProjectIndexEntry
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.scene
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A published project read through content bundles plays exactly as it does from loose files: the
 * same frame, a model, a sprite and a tilemap, drawn from the same geometry and textures, with one
 * request per bundle instead of one per file.
 */
class BundledProjectTest {
    private val looseIndex = ProjectIndex(
        files = FILES.map { (path, bytes) ->
            ProjectIndexEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = Sha256.digestHex(bytes), url = "files/$path")
        },
    )
    private val bundles = LinkedHashMap<String, ByteArray>()
    private val bundledIndex = kotlinx.coroutines.runBlocking {
        ContentBundleWriter().write(looseIndex, { FILES.getValue(it.path) }) { bundles[it.url] = it.bytes }
    }
    private val server = looseIndex.files.associate { it.url to FILES.getValue(it.path) } + bundles

    @Test
    fun aSceneDrawsTheSameFrameThroughBundlesAsThroughLooseFiles() = runTest {
        val looseRequests = mutableListOf<String>()
        val bundledRequests = mutableListOf<String>()

        val loose = frame(IndexedAssetSource(looseIndex, { url -> looseRequests += url; server.getValue(url) }, MemoryCache()))
        val bundled = frame(IndexedAssetSource(bundledIndex, { url -> bundledRequests += url; server.getValue(url) }, MemoryCache()))

        assertEquals(3, loose.size, "the positive control: the model, the sprite and the tilemap's chunk draw: $loose")
        assertTrue(loose.any { it.texture != null } && loose.any { it.geometry != null }, "textured and modelled draws: $loose")
        assertEquals(loose, bundled)
        assertEquals(FILES.size, looseRequests.size, "one request per file read loose: $looseRequests")
        assertEquals(bundles.keys.sorted(), bundledRequests.sorted(), "one request per bundle: $bundledRequests")
        assertTrue(bundles.size < FILES.size)
    }

    @Test
    fun aPrefetchedBundledProjectPlaysWithNoFurtherRequest() = runTest {
        val requests = mutableListOf<String>()
        val source = IndexedAssetSource(bundledIndex, { url -> requests += url; server.getValue(url) }, MemoryCache())

        source.prefetch(FILES.keys.toList())
        val prefetched = requests.toList()
        val drawn = frame(source)

        assertEquals(bundles.keys.sorted(), prefetched.sorted())
        assertEquals(prefetched, requests, "the scene loads from the cache")
        assertEquals(3, drawn.size)
    }

    /** What one draw shows: its geometry or texture, where it is, and how it blends. */
    private data class Drawn(val geometry: MeshGeometry?, val texture: List<Any>?, val model: List<Float>, val cull: String, val alpha: String)

    /** Plays the project in [files] for a few frames, once its files have loaded, and answers the last frame's draws. */
    private suspend fun TestScope.frame(files: IndexedAssetSource): List<Drawn> {
        val project = loadProject(files)
        project.sceneLoads = StandardTestDispatcher(testScheduler)
        val renderer = RecordingRenderer()
        val game = app { scene("play") { runProject(project) } }
        game.ready(renderer)
        game.update(FRAME, WIDTH, HEIGHT)
        advanceUntilIdle()
        repeat(2) { game.update(FRAME, WIDTH, HEIGHT) }
        val drawn = renderer.lastFrame.sortedBy { it.toString() }
        game.dispose()
        project.close()
        return drawn
    }

    /** Keeps the last frame's draws, as what each one shows, from the geometry and texture it was made with. */
    private class RecordingRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        private val geometries = HashMap<Mesh, MeshGeometry>()
        private val textures = HashMap<Material, TextureAsset>()
        var lastFrame: List<Drawn> = emptyList()
        private var pending = ArrayList<Drawn>()

        // Taken as each draw is prepared: a renderer may reuse the request for the next one.
        override val gpuDrawPreparer = GpuDrawPreparer { request, _, _ ->
            pending += request.drawn()
            null
        }

        private fun GpuDrawRequest.drawn() = Drawn(
            geometry = geometries[mesh],
            texture = textures[material]?.let { listOf(it.width, it.height, it.data.toList()) },
            model = model.data.toList(),
            cull = cullMode.toString(),
            alpha = alphaMode.toString(),
        )

        override fun draw(input: GpuPassInput) {
            lastFrame = pending
            pending = ArrayList()
        }

        override fun createMesh(geometry: MeshGeometry): Mesh = super.createMesh(geometry).also { geometries[it] = geometry }

        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material =
            super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures).also { if (texture != null) textures[it] = texture }
    }

    private class MemoryCache : ContentCache {
        private val stored = HashMap<String, ByteArray>()
        override suspend fun get(sha256: String): ByteArray? = stored[sha256]
        override suspend fun put(sha256: String, bytes: ByteArray) {
            stored[sha256] = bytes
        }
    }

    private companion object {
        fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            for (x in 0 until width) image.setRGB(x, 0, 0xFF000000.toInt() or (x * 40 shl 16))
            ImageIO.write(image, "png", it)
        }.toByteArray()

        val FILES: Map<String, ByteArray> = linkedMapOf(
            "awake.project.json" to
                """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
                    .encodeToByteArray(),
            "scenes/main.scene.json" to """
                { "version": 1, "name": "harbor", "nodes": [
                  { "name": "Statue", "transform": { "position": { "x": 1.0, "y": 0.0, "z": -2.0 } },
                    "components": [ { "component": "mesh_renderer", "mesh": "assets/models/statue.gltf", "material": "lit-shadow" } ] },
                  { "name": "Hero", "components": [ { "component": "sprite", "texture": "art/hero.png", "columns": 2, "pixelsPerUnit": 2 } ] },
                  { "name": "Ground", "components": [ { "component": "tilemap", "texture": "art/tiles.png", "width": 2, "height": 1,
                    "tiles": [0, 1], "columns": 2, "pixelsPerUnit": 2 } ] }
                ] }
            """.trimIndent().encodeToByteArray(),
            "assets/models/statue.gltf" to triangleGltf().encodeToByteArray(),
            "art/hero.png" to png(4, 2),
            "art/tiles.png" to png(6, 2),
        )
    }
}
