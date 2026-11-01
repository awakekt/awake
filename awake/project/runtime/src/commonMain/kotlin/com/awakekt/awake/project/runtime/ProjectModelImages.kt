/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.ktx.loader.CookedTextureLoader
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.scene.gltf.GltfAssetResolver

/**
 * Reads this resolver's models from [files], each image the project ships cooked to KTX2 beside it
 * (`rock.png` with `rock.ktx2`) from the cooked file (#1211). A model loads before the device that
 * draws it is known, so it reads the Basis Universal file, which is transcoded to the device's block
 * format when its material is made; where the platform has no transcoder, as on Android and iOS, it
 * reads the image. An image embedded in a GLB has no file of its own and loads as before.
 */
internal fun GltfAssetResolver.readProjectFiles(files: AssetSource) {
    val cooked = CookedTextureLoader(files)
    setAssetSource(cooked.withCookedImages())
    setImageDecoder(cooked::decode)
}
