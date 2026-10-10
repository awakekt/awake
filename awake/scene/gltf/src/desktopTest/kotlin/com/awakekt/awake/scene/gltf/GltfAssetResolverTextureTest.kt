/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.io.AssetSource
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class GltfAssetResolverTextureTest {
    @Test
    fun embeddedBaseColorTextureSelectsAPathScopedMaterial() = runTest {
        val path = "assets/painted-triangle.gltf"
        val resolver = GltfAssetResolver()

        resolver.preloadMaterials(path, texturedTriangleJson().encodeToByteArray())

        assertEquals("gltf-material:$path", resolver.materialName(path))
        val parameters = assertNotNull(resolver.materialParameters(path))
        assertEquals(0.7f, parameters.metallic)
        assertEquals(0.35f, parameters.roughness)
    }

    @Test
    fun multiplePrimitiveMaterialsBecomeIndependentRenderSlots() = runTest {
        val path = "assets/multi-material.gltf"
        val resolver = GltfAssetResolver()

        resolver.preloadMaterials(path, texturedTriangleJson(primitiveCount = 2).encodeToByteArray())

        val slots = resolver.materialSlots(path)
        assertEquals(2, slots.size)
        assertEquals("gltf-primitive:$path#0", slots[0].mesh)
        assertEquals("gltf-material:$path#0", slots[0].material)
        assertEquals("gltf-primitive:$path#1", slots[1].mesh)
        assertEquals("gltf-material:$path#1", slots[1].material)
    }

    /** A model changed on disk: forgetting it drops what was parsed, so preloading reads the new file. */
    @Test
    fun aForgottenModelIsReadAgainAndOthersStay() = runTest {
        val path = "assets/models/house#2.gltf"
        val other = "assets/models/house.gltf"
        val resolver = GltfAssetResolver()
        resolver.preload(path, texturedTriangleJson(primitiveCount = 2).encodeToByteArray())
        resolver.preload(other, texturedTriangleJson().encodeToByteArray())

        resolver.preload(path, texturedTriangleJson().encodeToByteArray())
        assertEquals(2, resolver.materialSlots(path).size, "a loaded model is kept until it is forgotten")

        resolver.forget(path)
        assertEquals(emptyList(), resolver.materialSlots(path))
        assertEquals("gltf-material:$other", resolver.materialName(other), "another model stays loaded")

        resolver.preload(path, texturedTriangleJson().encodeToByteArray())
        assertEquals(1, resolver.materialSlots(path).size, "the new file is parsed")
        assertEquals("gltf-material:$path", resolver.materialName(path))
    }

    /** A model preloaded into a resolver of its own, as a host does off the frame thread, resolves here once adopted. */
    @Test
    fun anAdoptedModelResolvesAsThoughItWerePreloadedHere() = runTest {
        val path = "assets/characters/walker.gltf"
        val other = "assets/models/painted.gltf"
        val staged = GltfAssetResolver()
        staged.preload(path, skinnedPartsWithFactorsJson().encodeToByteArray())
        staged.preload(other, texturedTriangleJson().encodeToByteArray())
        val resolver = GltfAssetResolver()
        assertFalse(resolver.isPreloaded(path))

        resolver.adopt(path, staged)

        assertTrue(resolver.isPreloaded(path))
        assertSame(staged.getLoadedScene(path), resolver.getLoadedScene(path), "the parsed scene is taken, not parsed again")
        assertEquals(staged.materialSlots(path), resolver.materialSlots(path))
        assertEquals(VertexFormat.PositionNormalColorUvSkin, resolver.skinnedPartGeometry("gltf-primitive:$path#0")?.format)
        assertEquals(0.3f, resolver.materialDefaults("gltf-primitive:$path#0", "gltf-material:$path#0")?.metallic)
        assertFalse(resolver.isPreloaded(other), "only the model named is adopted")

        resolver.forget(path)
        assertFalse(resolver.isPreloaded(path), "an adopted model is forgotten like a preloaded one")
    }

    @Test
    fun adoptingKeepsWhatAResolverAlreadyHolds() = runTest {
        val path = "assets/models/house.gltf"
        val staged = GltfAssetResolver()
        staged.preload(path, texturedTriangleJson(primitiveCount = 2).encodeToByteArray())
        val resolver = GltfAssetResolver()
        resolver.preload(path, texturedTriangleJson().encodeToByteArray())

        resolver.adopt(path, staged)

        assertEquals(1, resolver.materialSlots(path).size)
    }

    @Test
    fun aSavedPrimitiveMeshResolvesToItsModelFile() {
        val resolver = GltfAssetResolver()
        val slotMesh = "gltf-primitive:assets/models/house#2.glb#1"

        assertTrue(resolver.canResolveMesh(slotMesh))
        assertEquals("assets/models/house#2.glb", resolver.modelPath(slotMesh))
        assertEquals("assets/models/house.glb", resolver.modelPath("assets/models/house.glb"))
        assertFalse(resolver.canResolveMesh("gltf-primitive:assets/models/notes.txt#0"))
    }

    @Test
    fun externalSidecarImageResolvesRelativeToTheModelPath() = runTest {
        val path = "assets/models/sidecar.gltf"
        val resolver = GltfAssetResolver()
        val imageBytes = Base64.Default.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
        resolver.setAssetSource(
            AssetSource { assetPath ->
                imageBytes.takeIf { assetPath.value == "assets/models/paint.png" }
                    ?.let(Result.Companion::success)
                    ?: Result.failure(IllegalStateException("Missing asset: ${assetPath.value}"))
            },
        )

        resolver.preloadMaterials(
            path,
            texturedTriangleJson(externalImage = true).encodeToByteArray(),
        )

        assertEquals("gltf-material:$path", resolver.materialName(path))
        assertNotNull(resolver.materialParameters(path))
    }

    @Test
    fun externalSidecarBufferResolvesRelativeToTheModelPath() = runTest {
        val path = "assets/models/sidecar.gltf"
        val resolver = GltfAssetResolver()
        resolver.setAssetSource(
            AssetSource { assetPath ->
                if (assetPath.value == "assets/models/triangle.bin") {
                    Result.success(triangleBufferBytes())
                } else {
                    Result.failure(IllegalStateException("Missing asset: ${assetPath.value}"))
                }
            },
        )

        resolver.preload(path, texturedTriangleJson(externalBuffer = true).encodeToByteArray())

        assertEquals("gltf-material:$path", resolver.materialName(path))
        assertNotNull(resolver.materialParameters(path))
    }

    /** A skinned model's parts: the textured one draws its texture, skinned; the other stays untextured. */
    @Test
    fun aSkinnedModelsPartsDrawTheirOwnTextures() = runTest {
        val path = "assets/characters/walker.gltf"
        val resolver = GltfAssetResolver()

        resolver.preload(path, skinnedPartsJson().encodeToByteArray())

        assertEquals(
            listOf("gltf-primitive:$path#0" to "gltf-material:$path#0", "gltf-primitive:$path#1" to "skinned-material"),
            resolver.materialSlots(path).map { it.mesh to it.material },
        )
        assertTrue(resolver.materialSlots(path).all { it.parameters != null }, "every part says what it was authored with")
        assertEquals(VertexFormat.PositionNormalColorUvSkin, resolver.partGeometry("gltf-primitive:$path#0")?.format)
        assertEquals(VertexFormat.PositionNormalColorSkin, resolver.partGeometry("gltf-primitive:$path#1")?.format)
        assertNull(resolver.partGeometry("gltf-primitive:$path#2"))
    }

    /** A skinned part draws with the factors its own glTF material was authored with, textured or not. */
    @Test
    fun aSkinnedPartKeepsItsMaterialFactors() = runTest {
        val path = "assets/characters/walker.gltf"
        val resolver = GltfAssetResolver()
        resolver.preload(path, skinnedPartsWithFactorsJson().encodeToByteArray())

        val textured = assertNotNull(resolver.materialDefaults("gltf-primitive:$path#0", "gltf-material:$path#0"))
        assertEquals(0.3f, textured.metallic)
        assertEquals(0.7f, textured.roughness)
        assertEquals(Color(0.2f, 0.4f, 0.6f, 1f), textured.baseColorFactor)

        val plain = assertNotNull(resolver.materialDefaults("gltf-primitive:$path#1", "skinned-material"))
        assertEquals(0f, plain.metallic)
        assertEquals(0.2f, plain.roughness)
        assertEquals(Color(0.9f, 0.1f, 0.1f, 1f), plain.baseColorFactor)
        assertEquals(Color(0.1f, 0.2f, 0.3f, 0f), plain.emissiveFactor)
    }

    /** The point of looking a part up by its mesh: both untextured parts here share one material name. */
    @Test
    fun partsThatShareAMaterialNameStillKeepTheirOwnFactors() = runTest {
        val path = "assets/characters/walker.gltf"
        val resolver = GltfAssetResolver()
        resolver.preload(path, skinnedPartsWithFactorsJson(firstPartTextured = false).encodeToByteArray())

        val first = assertNotNull(resolver.materialDefaults("gltf-primitive:$path#0", "skinned-material"))
        val second = assertNotNull(resolver.materialDefaults("gltf-primitive:$path#1", "skinned-material"))

        assertEquals(0.3f, first.metallic)
        assertEquals(0f, second.metallic)
        assertEquals(0.2f, second.roughness)
    }

    @Test
    fun theSlotsCarryEachPartsAuthoredFactors() = runTest {
        val path = "assets/characters/walker.gltf"
        val resolver = GltfAssetResolver()
        resolver.preload(path, skinnedPartsWithFactorsJson().encodeToByteArray())

        val slots = resolver.materialSlots(path)

        assertEquals(0.3f, slots[0].parameters?.metallic)
        assertEquals(Color(0.2f, 0.4f, 0.6f, 1f), slots[0].parameters?.baseColorFactor)
        assertEquals(0.2f, slots[1].parameters?.roughness)
    }

    @Test
    fun aPartsFactorsAreOneSharedInstanceNotACopyPerEntity() = runTest {
        val path = "assets/characters/walker.gltf"
        val resolver = GltfAssetResolver()
        resolver.preload(path, skinnedPartsWithFactorsJson().encodeToByteArray())

        assertSame(
            resolver.materialDefaults("gltf-primitive:$path#1", "skinned-material"),
            resolver.materialDefaults("gltf-primitive:$path#1", "skinned-material"),
        )
    }

    @Test
    fun aMeshThatIsNotASkinnedPartKeepsItsMaterialsFactors() = runTest {
        val path = "assets/models/painted.gltf"
        val resolver = GltfAssetResolver()
        resolver.preload(path, texturedTriangleJson().encodeToByteArray())

        val byMaterial = assertNotNull(resolver.materialDefaults("gltf-material:$path"))
        assertSame(byMaterial, resolver.materialDefaults(path, "gltf-material:$path"), "a static model is looked up by its material, as before")
        assertEquals(0.7f, byMaterial.metallic)
    }

    @Test
    fun forgettingAModelDropsItsPartsFactors() = runTest {
        val path = "assets/characters/walker.gltf"
        val resolver = GltfAssetResolver()
        resolver.preload(path, skinnedPartsWithFactorsJson().encodeToByteArray())
        assertNotNull(resolver.materialDefaults("gltf-primitive:$path#1", "skinned-material"))

        resolver.forget(path)

        assertNull(resolver.materialDefaults("gltf-primitive:$path#1", "skinned-material"), "nothing stale is served for a model that was dropped")
    }

    /**
     * [skinnedPartsJson] with factors on its materials: the first part's textured material is metallic 0.3,
     * roughness 0.7, tinted blue-grey; the second part gets a material of its own, metallic 0, roughness 0.2,
     * red, with a faint emissive. With [firstPartTextured] false the first part is untextured too, so both
     * parts draw with the one shared `skinned-material`.
     */
    private fun skinnedPartsWithFactorsJson(firstPartTextured: Boolean = true): String {
        val textured = """{"pbrMetallicRoughness":{"baseColorTexture":{"index":0},"baseColorFactor":[0.2,0.4,0.6,1.0],"metallicFactor":0.3,"roughnessFactor":0.7}}"""
        val untexturedFirst = """{"pbrMetallicRoughness":{"baseColorFactor":[0.2,0.4,0.6,1.0],"metallicFactor":0.3,"roughnessFactor":0.7}}"""
        val plain = """{"pbrMetallicRoughness":{"baseColorFactor":[0.9,0.1,0.1,1.0],"metallicFactor":0.0,"roughnessFactor":0.2},"emissiveFactor":[0.1,0.2,0.3]}"""
        val json = skinnedPartsJson()
            .replace(
                """"materials": [{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}]""",
                """"materials": [${if (firstPartTextured) textured else untexturedFirst},$plain]""",
            )
            .replace(
                """{"attributes":{"POSITION":0,"JOINTS_0":2,"WEIGHTS_0":3},"indices":1}""",
                """{"attributes":{"POSITION":0,"JOINTS_0":2,"WEIGHTS_0":3},"indices":1,"material":1}""",
            )
        return if (firstPartTextured) json else json.replace(""","TEXCOORD_0":4},"indices":1,"material":0}""", """},"indices":1,"material":0}""")
    }

    /** One triangle skinned to one joint, drawn by two part nodes: the first textured, the second not. */
    private fun skinnedPartsJson(): String {
        val bytes = triangleBufferBytes()
        val image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        val skinning = ByteArray(12) + floatBytes(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f) +
            floatBytes(0f, 0f, 1f, 0f, 0f, 1f) + floatBytes(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val buffer = bytes + ByteArray(2) + skinning
        val base = bytes.size + 2
        return """
            {
              "buffers": [{"uri":"data:application/octet-stream;base64,${Base64.encode(buffer)}","byteLength":${buffer.size}}],
              "bufferViews": [
                {"buffer":0,"byteOffset":0,"byteLength":36},
                {"buffer":0,"byteOffset":36,"byteLength":6},
                {"buffer":0,"byteOffset":$base,"byteLength":12},
                {"buffer":0,"byteOffset":${base + 12},"byteLength":48},
                {"buffer":0,"byteOffset":${base + 60},"byteLength":24},
                {"buffer":0,"byteOffset":${base + 84},"byteLength":64}
              ],
              "accessors": [
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"},
                {"bufferView":2,"componentType":5121,"count":3,"type":"VEC4"},
                {"bufferView":3,"componentType":5126,"count":3,"type":"VEC4"},
                {"bufferView":4,"componentType":5126,"count":3,"type":"VEC2"},
                {"bufferView":5,"componentType":5126,"count":1,"type":"MAT4"}
              ],
              "materials": [{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],
              "textures": [{"source":0}],
              "images": [{"uri":"data:image/png;base64,$image"}],
              "meshes": [
                {"primitives":[{"attributes":{"POSITION":0,"JOINTS_0":2,"WEIGHTS_0":3,"TEXCOORD_0":4},"indices":1,"material":0}]},
                {"primitives":[{"attributes":{"POSITION":0,"JOINTS_0":2,"WEIGHTS_0":3},"indices":1}]}
              ],
              "nodes": [{"name":"Root"}, {"mesh":0,"skin":0}, {"mesh":1,"skin":0}],
              "skins": [{"inverseBindMatrices":5,"joints":[0]}],
              "scenes": [{"nodes":[0,1,2]}],
              "scene": 0
            }
        """.trimIndent()
    }

    private fun floatBytes(vararg values: Float): ByteArray {
        val bytes = ByteArray(values.size * 4)
        values.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            for (byte in 0 until 4) bytes[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
        }
        return bytes
    }

    private fun texturedTriangleJson(
        primitiveCount: Int = 1,
        externalImage: Boolean = false,
        externalBuffer: Boolean = false,
    ): String {
        val bytes = triangleBufferBytes()
        val image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        val imageUri = if (externalImage) "paint.png" else "data:image/png;base64,$image"
        val bufferUri = if (externalBuffer) "triangle.bin" else "data:application/octet-stream;base64,${Base64.encode(bytes)}"
        val materials = List(primitiveCount) {
            """{"pbrMetallicRoughness":{"baseColorTexture":{"index":$it},"metallicFactor":0.7,"roughnessFactor":0.35}}"""
        }.joinToString(",")
        val textures = List(primitiveCount) { """{"source":$it}""" }.joinToString(",")
        val images = List(primitiveCount) { """{"uri":"$imageUri"}""" }.joinToString(",")
        val primitives = List(primitiveCount) {
            """{"attributes":{"POSITION":0},"indices":1,"material":$it}"""
        }.joinToString(",")
        return """
            {
              "buffers": [{"uri":"$bufferUri","byteLength":${bytes.size}}],
              "bufferViews": [
                {"buffer":0,"byteOffset":0,"byteLength":36},
                {"buffer":0,"byteOffset":36,"byteLength":6}
              ],
              "accessors": [
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}
              ],
              "materials": [$materials],
              "textures":[$textures],
              "images":[$images],
              "meshes":[{"primitives":[$primitives]}],
              "nodes":[{"mesh":0}],
              "scenes":[{"nodes":[0]}],
              "scene":0
            }
        """.trimIndent()
    }

    private fun triangleBufferBytes(): ByteArray {
        val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val bytes = ByteArray(positions.size * 4 + 6)
        var offset = 0
        positions.forEach { value ->
            val bits = value.toRawBits()
            bytes[offset] = bits.toByte()
            bytes[offset + 1] = (bits ushr 8).toByte()
            bytes[offset + 2] = (bits ushr 16).toByte()
            bytes[offset + 3] = (bits ushr 24).toByte()
            offset += 4
        }
        bytes[offset + 2] = 2
        return bytes
    }
}
