/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.render.texture.TextureFiltering

/**
 * Content-addressed key for a glTF GPU material's uploaded textures, filtering, and uniform block size.
 *
 * Two primitives or models with identical encoded image bytes across all channels, the same
 * [filtering], and the same [uniformFloatCount] produce the same key so `SceneAssetLibrary`
 * uploads and retains one shared GPU material while each model/primitive keeps its own PBR
 * factors and cull mode.
 */
internal fun gltfMaterialContentKey(
    uniformFloatCount: Int,
    filtering: TextureFiltering,
    baseColorDigest: String,
    metallicRoughnessDigest: String? = null,
    normalDigest: String? = null,
    occlusionDigest: String? = null,
    emissiveDigest: String? = null,
): String {
    val pbrDigests = listOf(metallicRoughnessDigest, normalDigest, occlusionDigest, emissiveDigest)
    if (pbrDigests.all { it == null }) {
        return "gltf-tex:$uniformFloatCount:${filtering.name}:$baseColorDigest"
    }
    val combined = buildString {
        append(baseColorDigest)
        for (digest in pbrDigests) append('|').append(digest.orEmpty())
    }
    return "gltf-tex-pbr:$uniformFloatCount:${filtering.name}:${Sha256.digestHex(combined)}"
}

internal fun gltfUntexturedMaterialKey(uniformFloatCount: Int): String = "gltf-untextured:$uniformFloatCount"

internal fun imageDigest(bytes: ByteArray): String = Sha256.digestHex(bytes)
