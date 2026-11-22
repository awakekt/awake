/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneMeshLoad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Multi-cycle streaming soak test: repeatedly loads, uploads, and releases a releasable glTF model
 * through [SceneAssetLibrary] and [GltfAssetResolver] and verifies that GPU handles, holder counts,
 * and CPU copies all drop cleanly on every cycle instead of accumulating.
 */
class SceneAssetStreamingSoakTest {

    @Test
    fun repeatedLoadUploadReleaseCyclesLeaveNoRetainedHandlesOrCpuCopies() = runTest {
        var liveMeshes = 0
        var liveMaterials = 0
        val renderer = object : NoopRenderer(), GpuDrawPreparationSource {
            override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }

            override fun createMesh(geometry: com.awakekt.awake.core.geometry.MeshGeometry): Mesh {
                liveMeshes++
                val delegate = super.createMesh(geometry)
                return object : Mesh by delegate {
                    override fun destroy() {
                        liveMeshes--
                        delegate.destroy()
                    }
                }
            }

            override fun createMaterial(
                texture: TextureAsset?,
                renderTarget: RenderTarget?,
                uniformFloatCount: Int,
                pbrTextures: PbrTextureSet?,
            ): Material {
                liveMaterials++
                val delegate = super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
                return object : Material by delegate {
                    override fun destroy() {
                        liveMaterials--
                        delegate.destroy()
                    }
                }
            }
        }

        val bytes = TRIANGLE.encodeToByteArray()
        val resolver = GltfAssetResolver().apply {
            setAssetSource(AssetSource { Result.success(bytes) })
        }
        val game = app { scene("soak") { assets { resolver(resolver) } } }
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val library = runtime.requireAssetLibrary()
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            repeat(CYCLES) { cycle ->
                awaitLoad(assertNotNull(resolver.loadMesh(PATH, background), "cycle $cycle reloads dropped mesh"))
                val materialName = resolver.materialName(PATH)
                resolver.loadMaterial(materialName, background)?.let { awaitLoad(it) }

                val entity = runtime.world.create()
                val request = SceneRenderableRequest(entity, SceneMeshRenderer(mesh = PATH, material = materialName))
                library.resolve(runtime, request)
                assertEquals(1, liveMeshes, "cycle $cycle holds 1 GPU mesh")
                assertEquals(1, liveMaterials, "cycle $cycle holds 1 GPU material")
                assertTrue(resolver.isMeshLetGo(PATH), "cycle $cycle drops CPU geometry once uploaded")
                assertTrue(resolver.isMaterialLetGo(materialName), "cycle $cycle drops CPU image bytes once uploaded")

                library.releaseRenderable(request)
                runtime.world.destroy(entity)
                assertEquals(0, liveMeshes, "cycle $cycle destroys GPU mesh on release")
                assertEquals(0, liveMaterials, "cycle $cycle destroys GPU material on release")
                assertFalse(library.isMeshBuilt(PATH), "cycle $cycle leaves no built mesh")
                assertFalse(library.isMaterialBuilt(materialName), "cycle $cycle leaves no built material")
                assertEquals(0, library.meshHolderCount(PATH))
                assertEquals(0, library.materialHolderCount(materialName))
            }
        } finally {
            background.cancel()
            game.dispose()
        }
    }

    private suspend fun awaitLoad(load: SceneMeshLoad) {
        while (!load.poll()) yield()
        load.close()
        assertNull(load.failure)
    }

    private companion object {
        const val CYCLES = 24
        const val PATH = "assets/props/soak-triangle.gltf"
        const val PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        const val BUFFER = "AAAAAAAAAAAAAAAAAACAPwAAAAAAAAAAAAAAAAAAgD8AAAAAAAABAAIA"
        val TRIANGLE = """
            {
              "buffers": [{"uri":"data:application/octet-stream;base64,$BUFFER","byteLength":42}],
              "bufferViews": [{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":6}],
              "accessors": [
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}
              ],
              "materials": [{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],
              "textures": [{"source":0}],
              "images": [{"uri":"data:image/png;base64,$PIXEL_PNG"}],
              "meshes": [{"primitives":[{"attributes":{"POSITION":0},"indices":1,"material":0}]}],
              "nodes": [{"mesh":0}], "scenes": [{"nodes":[0]}], "scene": 0
            }
        """.trimIndent()
    }
}
