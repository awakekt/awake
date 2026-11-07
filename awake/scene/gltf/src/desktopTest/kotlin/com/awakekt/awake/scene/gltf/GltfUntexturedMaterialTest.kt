/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.MeshRendererBinding
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneAppSpec
import com.awakekt.awake.scene.runtime.SceneAssetLibrary
import com.awakekt.awake.scene.runtime.spawn
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A `.glb` whose material has no base colour image, only `pbrMetallicRoughness` factors, can be named
 * as `gltf-material:` and spawns, keeping its factors (#1409). glTF allows a material with no textures.
 */
class GltfUntexturedMaterialTest {
    init {
        SceneComponentRegistry.registerGlobal(MeshRendererBinding)
    }

    @Test
    fun anUntexturedMaterialResolvesByItsGltfMaterialNameAndSpawns() = runTest {
        val resolver = GltfAssetResolver().apply { preload(PATH, untexturedGlb()) }
        val library = SceneAssetLibrary(emptyMap(), emptyMap(), emptyMap(), dynamicResolvers = listOf(resolver))
        val runtime = runtime(library)

        val spawned = runtime.spawn(SceneNode(name = "Effect", components = listOf(SceneMeshRenderer(mesh = PATH, material = "gltf-material:$PATH"))))

        val renderer = assertNotNull(runtime.world.get<MeshRenderer>(spawned.root))
        assertEquals(VertexFormat.PositionNormalColor, renderer.mesh.format, "the untextured geometry draws with the plain lit pipeline")
        assertEquals(1, library.materialHolderCount("gltf-material:$PATH"))
    }

    @Test
    fun anUntexturedMaterialKeepsItsFactors() = runTest {
        val resolver = GltfAssetResolver().apply { preload(PATH, untexturedGlb()) }

        val defaults = assertNotNull(resolver.materialDefaults("gltf-material:$PATH"))

        assertEquals(0.25f, defaults.metallic)
        assertEquals(0.6f, defaults.roughness)
        assertEquals(Color(0.8f, 0.4f, 0.2f, 0.5f), defaults.baseColorFactor)
        assertEquals(Color(0.1f, 0.2f, 0.3f, 0f), defaults.emissiveFactor)
        assertEquals(AlphaMode.Masked, defaults.alphaMode)
        assertNotNull(resolver.createMaterial(NoopRenderer(), "gltf-material:$PATH"))
    }

    /** The untextured material is not mistaken for a textured one: the editor still places it as before. */
    @Test
    fun anUntexturedModelIsStillPlacedWithThePlainMaterial() = runTest {
        val resolver = GltfAssetResolver().apply { preload(PATH, untexturedGlb()) }

        assertEquals("lit-shadow", resolver.materialName(PATH))
        assertNull(resolver.materialParameters(PATH), "no texture parameters are reported for it")
    }

    @Test
    fun aForgottenModelsUntexturedMaterialNoLongerResolves() = runTest {
        val resolver = GltfAssetResolver().apply { preload(PATH, untexturedGlb()) }
        val adopter = GltfAssetResolver().apply { adopt(PATH, resolver) }

        resolver.forget(PATH)

        assertNull(resolver.createMaterial(NoopRenderer(), "gltf-material:$PATH"))
        assertNotNull(adopter.createMaterial(NoopRenderer(), "gltf-material:$PATH"), "an adopting resolver keeps what it took")
    }

    private suspend fun runtime(library: SceneAssetLibrary) = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = {},
            renderableFactory = { request -> library.resolve(this, request) },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ),
    ).apply {
        initialize(NoServices)
        ready(NoopRenderer())
    }

    private object NoServices : AppServiceLookup {
        override fun <T : Any> service(type: KClass<T>): T? = null
    }

    private companion object {
        const val PATH = "assets/effects/effect-mesh.glb"

        /** One triangle whose material has only factors: no images, no textures, no `baseColorTexture`. */
        fun untexturedGlb(): ByteArray {
            val material = """{"pbrMetallicRoughness":{"baseColorFactor":[0.8,0.4,0.2,0.5],"metallicFactor":0.25,"roughnessFactor":0.6},""" +
                """"emissiveFactor":[0.1,0.2,0.3],"alphaMode":"MASK"}"""
            val json = """
                {
                  "asset":{"version":"2.0"},
                  "buffers":[{"byteLength":42}],
                  "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":6}],
                  "accessors":[
                    {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                    {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}
                  ],
                  "materials":[$material],
                  "meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1,"material":0}]}],
                  "nodes":[{"mesh":0}],
                  "scenes":[{"nodes":[0]}],
                  "scene":0
                }
            """.trimIndent()
            return glb(json, triangle())
        }

        fun triangle(): ByteArray {
            val bytes = ByteArray(42)
            floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f).forEachIndexed { index, value -> bytes.putInt(index * 4, value.toRawBits()) }
            bytes[36 + 2] = 1
            bytes[36 + 4] = 2
            return bytes
        }

        /** The smallest valid GLB: header, a space-padded JSON chunk and a zero-padded BIN chunk. */
        fun glb(json: String, bin: ByteArray): ByteArray {
            val jsonBytes = json.encodeToByteArray().let { it + ByteArray((4 - it.size % 4) % 4) { ' '.code.toByte() } }
            val binBytes = bin + ByteArray((4 - bin.size % 4) % 4)
            val out = ByteArray(12 + 8 + jsonBytes.size + 8 + binBytes.size)
            out.putInt(0, 0x46546C67)
            out.putInt(4, 2)
            out.putInt(8, out.size)
            out.putInt(12, jsonBytes.size)
            out.putInt(16, 0x4E4F534A)
            jsonBytes.copyInto(out, 20)
            val bin0 = 20 + jsonBytes.size
            out.putInt(bin0, binBytes.size)
            out.putInt(bin0 + 4, 0x004E4942)
            binBytes.copyInto(out, bin0 + 8)
            return out
        }

        fun ByteArray.putInt(offset: Int, value: Int) {
            for (byte in 0 until 4) this[offset + byte] = (value ushr (byte * 8)).toByte()
        }
    }
}
