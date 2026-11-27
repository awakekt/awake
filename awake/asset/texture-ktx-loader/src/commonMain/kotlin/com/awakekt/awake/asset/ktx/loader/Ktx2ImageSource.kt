/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.asset.ktx.Ktx2Reader
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.ReleasableGpuImageSource
import kotlin.concurrent.Volatile

/**
 * A cooked texture in a block format, as a [ReleasableGpuImageSource] `createImageMaterial` uploads
 * as it is. [readRgba8] decodes it with [decode] for a device that doesn't take the format, which a
 * mobile block file can't be. [releaseImageData] drops the compressed mip arrays and decoder closure
 * once uploaded so CPU memory is reclaimed immediately.
 */
internal class Ktx2ImageSource(
    compressed: CompressedTextureImage,
    decode: () -> ByteArray,
) : ReleasableGpuImageSource {
    override val width: Int = compressed.width
    override val height: Int = compressed.height
    override val externalImage: Any? get() = null

    @Volatile
    private var heldCompressed: CompressedTextureImage? = compressed

    @Volatile
    private var heldDecode: (() -> ByteArray)? = decode

    override val compressed: CompressedTextureImage? get() = heldCompressed

    override val isReleased: Boolean get() = heldCompressed == null && heldDecode == null

    override fun releaseImageData(): Boolean {
        val held = heldCompressed != null || heldDecode != null
        heldCompressed = null
        heldDecode = null
        return held
    }

    override fun readRgba8(): ByteArray =
        checkNotNull(heldDecode) { "This KTX2 image was released once uploaded; read its file again to make another material from it." }()
}

/**
 * A pre-compressed block-format KTX2 texture (such as `.astc.ktx2` or `.etc2.ktx2` on mobile) loaded
 * without a [BasisTranscoder]. Uploads directly via [compressed] on any device supporting its block
 * format, and releases its CPU mip byte arrays when [releaseImageData] runs.
 */
internal class Ktx2RawImageSource(compressed: CompressedTextureImage) : ReleasableGpuImageSource {
    override val width: Int = compressed.width
    override val height: Int = compressed.height
    override val externalImage: Any? get() = null

    @Volatile
    private var heldCompressed: CompressedTextureImage? = compressed

    override val compressed: CompressedTextureImage? get() = heldCompressed

    override val isReleased: Boolean get() = heldCompressed == null

    override fun releaseImageData(): Boolean {
        val held = heldCompressed != null
        heldCompressed = null
        return held
    }

    override fun readRgba8(): ByteArray =
        error("Pre-compressed ${heldCompressed?.format ?: "released"} KTX2 has no CPU RGBA8 decoder on this platform.")
}

/**
 * A Basis Universal KTX2 texture loaded before the device that draws it is known, such as a model's,
 * as a [ReleasableGpuImageSource]: [compressed] transcodes it with [transcoder] to the best block
 * format the device takes when its material is made, [readRgba8] decodes it for a device that takes
 * none, and [releaseImageData] drops the raw `.ktx2` file bytes once uploaded.
 */
internal class Ktx2BasisImageSource(bytes: ByteArray, private val transcoder: BasisTranscoder) : ReleasableGpuImageSource {
    private val header = Ktx2Reader.read(bytes)
    override val width: Int = header.width
    override val height: Int = header.height
    override val externalImage: Any? get() = null

    @Volatile
    private var heldBytes: ByteArray? = bytes

    override val isReleased: Boolean get() = heldBytes == null

    override fun releaseImageData(): Boolean {
        val held = heldBytes != null
        heldBytes = null
        return held
    }

    override fun compressed(formats: Set<CompressedTextureFormat>): CompressedTextureImage? {
        val bytes = heldBytes ?: return null
        return (Ktx2TextureLoader.decode(bytes, formats, transcoder) as? Ktx2Upload.Compressed)?.image
    }

    override fun readRgba8(): ByteArray {
        val bytes = checkNotNull(heldBytes) {
            "This KTX2 image was released once uploaded; read its file again to make another material from it."
        }
        return (Ktx2TextureLoader.decode(bytes, emptySet(), transcoder) as Ktx2Upload.Rgba).texture.data
    }
}
