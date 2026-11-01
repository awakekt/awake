/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.asset.ktx.Ktx2Reader
import com.awakekt.awake.render.texture.CompressedTextureFormat
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

/**
 * A Basis Universal KTX2 texture loaded before the device that draws it is known, such as a model's,
 * as a [GpuImageSource]: [compressed] transcodes it with [transcoder] to the best block format the device
 * takes when its material is made, and [readRgba8] decodes it for a device that takes none.
 */
internal class Ktx2BasisImageSource(private val bytes: ByteArray, private val transcoder: BasisTranscoder) : GpuImageSource {
    private val header = Ktx2Reader.read(bytes)
    override val width: Int get() = header.width
    override val height: Int get() = header.height
    override val externalImage: Any? get() = null

    override fun compressed(formats: Set<CompressedTextureFormat>): CompressedTextureImage? =
        (Ktx2TextureLoader.decode(bytes, formats, transcoder) as? Ktx2Upload.Compressed)?.image

    override fun readRgba8(): ByteArray = (Ktx2TextureLoader.decode(bytes, emptySet(), transcoder) as Ktx2Upload.Rgba).texture.data
}
