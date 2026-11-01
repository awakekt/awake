/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

/**
 * The glTF metallic-roughness maps of a material made with `GpuDevice.createImageMaterial`, as
 * [GpuImageSource]s: what [PbrTextureSet] holds as [TextureAsset]s, for maps that may be copied on the
 * GPU. Each is null where the material has no such map.
 *
 * @property metallicRoughness Metallic-roughness map, or null.
 * @property normal Normal map, or null.
 * @property occlusion Ambient occlusion map, or null.
 * @property emissive Emissive map, or null.
 */
class PbrImageSet(
    val metallicRoughness: GpuImageSource? = null,
    val normal: GpuImageSource? = null,
    val occlusion: GpuImageSource? = null,
    val emissive: GpuImageSource? = null,
) {
    /** The maps in [PbrTextureSet]'s order: metallic-roughness, normal, occlusion, emissive. */
    val maps: List<GpuImageSource?> get() = listOf(metallicRoughness, normal, occlusion, emissive)

    /** Whether any map is set. */
    val isEmpty: Boolean get() = maps.all { it == null }

    /** Every map read to the CPU, as `createMaterial(texture = ...)` takes them: the CPU path. */
    fun readTextures(): PbrTextureSet = PbrTextureSet(
        metallicRoughness = metallicRoughness?.toTextureAsset(),
        normal = normal?.toTextureAsset(),
        occlusion = occlusion?.toTextureAsset(),
        emissive = emissive?.toTextureAsset(),
    )
}

/** [GpuImageSource.readRgba8] as a [TextureAsset] of [filtering]: the CPU upload of this image. */
fun GpuImageSource.toTextureAsset(filtering: TextureFiltering = TextureFiltering.Linear): TextureAsset =
    TextureAsset(readRgba8(), width, height, filtering = filtering)
