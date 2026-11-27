/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.ktx.loader.CookedTextureLoader
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.scene.gltf.GltfAssetResolver

/**
 * Reads this resolver's models from [files], substituting each cooked image (`rock.png` ->
 * `rock.astc.ktx2` / `rock.etc2.ktx2` when [compressedFormats] reports mobile block support, or
 * `rock.ktx2` where a platform Basis transcoder is available) (#1211). Also decodes embedded
 * `KHR_texture_basisu` KTX2 images in GLBs.
 */
internal fun GltfAssetResolver.readProjectFiles(
    files: AssetSource,
    compressedFormats: () -> Set<CompressedTextureFormat> = { emptySet() },
) {
    val cooked = CookedTextureLoader(files)
    setAssetSource(cooked.withCookedImages(compressedFormats))
    setImageDecoder(cooked::decode)
}
