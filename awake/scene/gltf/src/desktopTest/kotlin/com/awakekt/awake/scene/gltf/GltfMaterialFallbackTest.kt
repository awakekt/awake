/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.MeshRendererBinding
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.spawn
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When `preloadMaterials` fails (for example, low-memory texture decoding failure) after geometry has already
 * preloaded, resolving the expected `gltf-material:` slot falls back to a plain untextured material instead of
 * returning null and crashing subsequent spawns of that model.
 */
@OptIn(ExperimentalEncodingApi::class)
class GltfMaterialFallbackTest {
    init {
        SceneComponentRegistry.registerGlobal(MeshRendererBinding)
    }

    @Test
    fun aSkinnedModelWhoseTextureDecodeFailsStillResolvesMaterialAndSpawnsMultipleTimes() = runTest {
        val uploadedUniformCounts = mutableListOf<Int>()
        val resolver = GltfAssetResolver().apply {
            setImageDecoder { throw IllegalStateException("Simulated low-memory texture decode failure") }
            preload(PATH, texturedSkinnedGltf().encodeToByteArray())
        }
        assertTrue(resolver.isPreloaded(PATH), "geometry preloaded even though texture decoding failed")

        val renderer = object : NoopRenderer(), GpuDrawPreparationSource {
            override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
            override fun createMaterial(
                texture: TextureAsset?,
                renderTarget: RenderTarget?,
                uniformFloatCount: Int,
                pbrTextures: PbrTextureSet?,
            ): Material {
                if (texture != null) uploadedUniformCounts += uniformFloatCount
                return super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
            }
        }
        val game = app { scene("fallback") { assets { resolver(resolver) } } }
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        val node = SceneNode(
            name = "Npc",
            components = listOf(SceneMeshRenderer(mesh = "gltf-primitive:$PATH#0", material = "gltf-material:$PATH#0")),
        )
        val first = runtime.spawn(node)
        val second = runtime.spawn(node)

        assertNotNull(runtime.world.get<MeshRenderer>(first.root), "first spawn falls back to plain untextured material")
        assertNotNull(runtime.world.get<MeshRenderer>(second.root), "second spawn reuses fallback without throwing")
        assertEquals(listOf(SkinnedUniformLayout.total), uploadedUniformCounts, "fallback matches textured skinned uniform layout")

        resolver.forget(PATH)
        assertNull(resolver.createMaterial(NoopRenderer(), "gltf-material:$PATH#0"), "forgotten model returns null again")
        game.dispose()
    }

    private fun texturedSkinnedGltf(): String {
        val buf = Buffer()
        val positions = buf.floats("VEC3", 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val uvs = buf.floats("VEC2", 0f, 0f, 1f, 0f, 0f, 1f)
        val joints = buf.unsignedBytes(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val weights = buf.floats("VEC4", 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
        val indices = buf.unsignedShorts(0, 1, 2)
        val inverseBind = buf.floats(
            "MAT4",
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        return buf.json(
            """
            "images": [{"uri":"data:image/png;base64,$PIXEL_PNG"}],
            "textures": [{"source":0}],
            "materials": [{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],
            "meshes": [{"primitives":[{"attributes":{"POSITION":$positions,"TEXCOORD_0":$uvs,"JOINTS_0":$joints,"WEIGHTS_0":$weights},"indices":$indices,"material":0}]}],
            "nodes": [{"name":"Root"},{"name":"Body","mesh":0,"skin":0}],
            "skins": [{"inverseBindMatrices":$inverseBind,"joints":[0]}],
            "scenes": [{"nodes":[0,1]}],
            "scene": 0
            """,
        )
    }

    private class Buffer {
        private var bytes = ByteArray(0)
        private val views = mutableListOf<String>()
        private val accessors = mutableListOf<String>()

        fun floats(type: String, vararg values: Float): Int = add(type, 5126, values.size, 4) { out ->
            values.forEachIndexed { index, value ->
                val bits = value.toRawBits()
                for (byte in 0 until 4) out[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
            }
        }

        fun unsignedBytes(vararg values: Int): Int =
            add("VEC4", 5121, values.size, 1) { out -> values.forEachIndexed { index, value -> out[index] = value.toByte() } }

        fun unsignedShorts(vararg values: Int): Int = add("SCALAR", 5123, values.size, 2) { out ->
            values.forEachIndexed { index, value ->
                out[index * 2] = value.toByte()
                out[index * 2 + 1] = (value ushr 8).toByte()
            }
        }

        fun json(body: String): String = """
            {
              "asset": {"version": "2.0"},
              "buffers": [{"uri":"data:application/octet-stream;base64,${Base64.encode(bytes)}","byteLength":${bytes.size}}],
              "bufferViews": [${views.joinToString(",")}],
              "accessors": [${accessors.joinToString(",")}],
              ${body.trimIndent()}
            }
        """.trimIndent()

        private fun add(type: String, componentType: Int, count: Int, componentSize: Int, write: (ByteArray) -> Unit): Int {
            val size = count * componentSize
            val offset = bytes.size
            bytes += ByteArray(size).also(write) + ByteArray((4 - size % 4) % 4)
            views += """{"buffer":0,"byteOffset":$offset,"byteLength":$size}"""
            val components = mapOf("SCALAR" to 1, "VEC2" to 2, "VEC3" to 3, "VEC4" to 4, "MAT4" to 16).getValue(type)
            accessors += """{"bufferView":${views.size - 1},"componentType":$componentType,"count":${count / components},"type":"$type"}"""
            return accessors.size - 1
        }
    }

    private companion object {
        const val PATH = "assets/characters/mvr_clamb.gltf"
        const val PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC"
    }
}
