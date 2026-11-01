/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.render.texture.CompressedTextureFormat

/**
 * Transcodes a Basis Universal KTX2 texture (ETC1S or UASTC) to a format a GPU samples. A platform
 * supplies one: on desktop, `LibKtxBasisTranscoder`, which is libktx. Mobile needs none, since it loads the
 * ASTC or ETC2 the cook wrote.
 */
interface BasisTranscoder {
    /** The block formats this transcoder writes. RGBA8 it always writes. */
    val formats: Set<CompressedTextureFormat>

    /**
     * Every mip level of [ktx2], a Basis Universal KTX2, in [format], the largest first: or in tightly
     * packed RGBA8 when [format] is null. Rows stay in the order the file stores them.
     */
    fun transcode(ktx2: ByteArray, format: CompressedTextureFormat?): List<ByteArray>
}
