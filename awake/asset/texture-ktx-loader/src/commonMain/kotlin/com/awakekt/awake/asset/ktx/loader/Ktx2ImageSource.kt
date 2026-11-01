/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.GpuImageSource

/**
 * A cooked texture in a block format, as a [GpuImageSource] `createImageMaterial` uploads as it is.
 * [readRgba8] decodes it with [decode] for a device that doesn't take the format, which a mobile
 * block file can't be.
 */
internal class Ktx2ImageSource(override val compressed: CompressedTextureImage, private val decode: () -> ByteArray) : GpuImageSource {
    override val width: Int get() = compressed.width
    override val height: Int get() = compressed.height
    override val externalImage: Any? get() = null

    override fun readRgba8(): ByteArray = decode()
}
