/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.pipeline.GpuCapability
import com.awakekt.awake.render.pipeline.GpuCapabilityKind
import com.awakekt.awake.render.pipeline.GpuCompressedTextures
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A project's model reads each image cooked to KTX2 beside it from the cooked file, and its material
 * takes the device's block format (#1211). An image that wasn't cooked is read and decoded as before.
 */
class ProjectModelImagesTest {

    @Test
    fun aModelsCookedImageUploadsInTheDevicesBlockFormat() = runTest {
        val (renderer, reads) = draw(image = "rock.png", cooked = true)

        assertEquals(CompressedTextureFormat.Bc7Rgba to COOKED_SIZE, renderer.blocks?.let { it.format to it.width })
        assertNull(renderer.rgba, "nothing went the RGBA8 way")
        assertEquals(listOf("models/rock.ktx2"), reads.distinct(), "the cooked file, and not the image")
    }

    @Test
    fun aModelsImageThatWasntCookedIsDecodedAsBefore() = runTest {
        val (renderer, reads) = draw(image = "rock.png", cooked = false)

        assertNull(renderer.blocks)
        assertEquals(PNG_SIZE, renderer.rgba?.width)
        assertEquals(listOf("models/rock.ktx2", "models/rock.png"), reads.distinct(), "the cooked file is looked for, then the image read")
    }

    /**
     * Preloads `models/rock.gltf`, whose one material samples [image], and makes its material on a BC7
     * device; with the files it read, besides the model, each as often as preloading reads it.
     */
    private suspend fun draw(image: String, cooked: Boolean): Pair<Bc7Device, List<String>> {
        val reads = mutableListOf<String>()
        val files = AssetSource { path ->
            if (!path.value.endsWith(".gltf")) reads += path.value
            runCatching {
                when (path.value) {
                    "models/rock.gltf" -> texturedTriangle(image).encodeToByteArray()
                    "models/rock.png" -> png()
                    "models/rock.ktx2" -> if (cooked) COOKED else error("not cooked")
                    else -> error("no ${path.value}")
                }
            }
        }
        val resolver = GltfAssetResolver().apply { readProjectFiles(files) }
        resolver.preload("models/rock.gltf")
        val renderer = Bc7Device()
        assertNotNull(resolver.createMaterial(renderer, resolver.materialName("models/rock.gltf")))
        return renderer to reads
    }

    /** A device that samples BC7, keeping the last block image and RGBA8 texture it was given. */
    private class Bc7Device : NoopRenderer() {
        var blocks: CompressedTextureImage? = null
        var rgba: TextureAsset? = null

        private val compressed = object : GpuCompressedTextures {
            override val formats = setOf(CompressedTextureFormat.Bc7Rgba)

            override fun createMaterial(image: CompressedTextureImage, filtering: TextureFiltering, uniformFloatCount: Int): Material {
                blocks = image
                return super@Bc7Device.createMaterial(null, null, uniformFloatCount, null)
            }
        }

        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material {
            if (texture != null) rgba = texture
            return super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? =
            if (kind == GpuCompressedTextures) compressed as T else null
    }

    private companion object {
        /** The cooked file: a 64 x 64 ETC1S texture the cook wrote, as `awake:asset:texture-ktx-loader`'s tests use. */
        const val COOKED_SIZE = 64
        const val PNG_SIZE = 2
        val COOKED: ByteArray = requireNotNull(ProjectModelImagesTest::class.java.getResourceAsStream("/cooked/rock.ktx2")).use { it.readBytes() }

        fun png(): ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(PNG_SIZE, PNG_SIZE, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()

        /** One triangle whose material's base colour samples the image at [uri], beside the model. */
        fun texturedTriangle(uri: String): String {
            val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
            val buffer = ByteArray(positions.size * 4 + 6).also { bytes ->
                positions.forEachIndexed { i, value -> for (b in 0 until 4) bytes[i * 4 + b] = (value.toRawBits() ushr (8 * b)).toByte() }
                bytes[positions.size * 4 + 2] = 1
                bytes[positions.size * 4 + 4] = 2
            }
            return """
                {
                  "asset": {"version": "2.0"},
                  "buffers": [{"uri": "data:application/octet-stream;base64,${Base64.encode(buffer)}", "byteLength": ${buffer.size}}],
                  "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 36}, {"buffer": 0, "byteOffset": 36, "byteLength": 6}],
                  "accessors": [
                    {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
                    {"bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR"}
                  ],
                  "materials": [{"pbrMetallicRoughness": {"baseColorTexture": {"index": 0}}}],
                  "textures": [{"source": 0}],
                  "images": [{"uri": "$uri"}],
                  "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1, "material": 0}]}],
                  "nodes": [{"mesh": 0}],
                  "scenes": [{"nodes": [0]}],
                  "scene": 0
                }
            """.trimIndent()
        }
    }
}
