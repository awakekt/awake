/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

/**
 * Raw pixel data a game supplies to a backend's game-application bootstrap (see
 * `VulkanEngine`) -- backend-neutral input, not a rendering abstraction itself.
 * [data] is expected in the same tightly-packed RGBA8 layout the existing demo texture uses
 * today. Both Vulkan and WebGPU's `Material` implementations support real textures.
 */
data class TextureAsset(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    /**
     * How many equally-sized images [data] holds, back to back.
     *
     * More than one makes this a 2D array texture -- a terrain splat's diffuse layers, say, where
     * a shader indexes a layer rather than binding N separate textures. A layer count rather than
     * a `List<TextureAsset>` because every consumer already accepts a `TextureAsset`, so the
     * single-layer case stays exactly what it was and nothing downstream needs a second type.
     */
    val layerCount: Int = 1,
    /** Whether this texture represents a 6-face cubemap (+X, -X, +Y, -Y, +Z, -Z). */
    val isCubemap: Boolean = false,
) {
    init {
        require(layerCount >= 1) { "A texture holds at least one layer; was $layerCount." }
        if (isCubemap) {
            require(layerCount == 6) { "A cubemap texture must have exactly 6 layers; was $layerCount." }
            require(width == height) { "Cubemap faces must be square; was ${width}x${height}." }
        }
        require(data.size == width * height * RGBA_BYTES * layerCount) {
            "A ${width}x$height RGBA8 texture of $layerCount layer(s) needs " +
                "${width * height * RGBA_BYTES * layerCount} bytes, got ${data.size}. A size " +
                "mismatch here uploads one layer's worth of another layer's pixels."
        }
    }

    /** Byte offset of [layer] within [data]. */
    fun layerOffset(layer: Int): Int {
        require(layer in 0 until layerCount) { "Layer $layer is outside 0..${layerCount - 1}." }
        return layer * width * height * RGBA_BYTES
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextureAsset) return false
        return data.contentEquals(other.data) &&
            width == other.width &&
            height == other.height &&
            layerCount == other.layerCount &&
            isCubemap == other.isCubemap
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + layerCount
        result = 31 * result + isCubemap.hashCode()
        return result
    }
}

/**
 * Assembles 6 square face buffers (+X, -X, +Y, -Y, +Z, -Z) into a single 6-layer cubemap [TextureAsset].
 */
fun createCubemapAsset(
    faces: List<ByteArray>,
    faceSize: Int,
): TextureAsset {
    require(faces.size == 6) { "A cubemap requires exactly 6 faces (+X, -X, +Y, -Y, +Z, -Z); got ${faces.size}." }
    require(faceSize > 0) { "Face size must be positive; got $faceSize." }
    val faceBytes = faceSize * faceSize * RGBA_BYTES
    val combined = ByteArray(faceBytes * 6)
    faces.forEachIndexed { i, faceData ->
        require(faceData.size == faceBytes) {
            "Face $i size mismatch: expected $faceBytes bytes (${faceSize}x${faceSize} RGBA8), got ${faceData.size}."
        }
        faceData.copyInto(combined, destinationOffset = i * faceBytes)
    }
    return TextureAsset(
        data = combined,
        width = faceSize,
        height = faceSize,
        layerCount = 6,
        isCubemap = true,
    )
}

private const val RGBA_BYTES = 4

/**
 * The rest of a glTF metallic-roughness material's texture channels, alongside
 * [com.awakekt.awake.render.renderer.Renderer.createMaterial]'s existing `texture`
 * (base color) parameter -- each `null` unless
 * the source material has that channel (see
 * [com.awakekt.awake.asset.gltf.GltfMesh]'s `metallicRoughnessImageBytes`/
 * `normalImageBytes`/`occlusionImageBytes`/`emissiveImageBytes`, which this bundles after a
 * caller decodes them the same way it decodes `baseColorImageBytes` into the `texture`
 * parameter today). A backend without full PBR support is free to ignore whichever fields it
 * doesn't sample. */
data class PbrTextureSet(
    val metallicRoughness: TextureAsset? = null,
    val normal: TextureAsset? = null,
    val occlusion: TextureAsset? = null,
    val emissive: TextureAsset? = null,
)
