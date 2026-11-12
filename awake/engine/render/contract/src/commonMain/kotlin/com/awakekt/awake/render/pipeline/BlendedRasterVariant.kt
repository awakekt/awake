/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/** Optional raster states for non-instanced blended pipelines. */
enum class BlendedRasterVariant(
    internal val variant: PipelineVariant,
    internal val cullMode: CullMode,
) {
    /** Alpha blending with back-face culling. */
    AlphaBack(PipelineVariant.AlphaBlended, CullMode.Back),

    /** Additive blending with back-face culling. */
    AdditiveBack(PipelineVariant.AdditiveBlended, CullMode.Back),

    /** Alpha blending with depth writes. */
    AlphaDepth(PipelineVariant.AlphaBlendedDepthWrite, CullMode.None),

    /** Additive blending with depth writes. */
    AdditiveDepth(PipelineVariant.AdditiveBlendedDepthWrite, CullMode.None),

    /** Alpha blending with depth writes and back-face culling. */
    AlphaDepthBack(PipelineVariant.AlphaBlendedDepthWrite, CullMode.Back),

    /** Additive blending with depth writes and back-face culling. */
    AdditiveDepthBack(PipelineVariant.AdditiveBlendedDepthWrite, CullMode.Back),
    ;

    internal companion object {
        /** Returns the companion key, or null for the default blended raster state. */
        fun select(additive: Boolean, cullMode: CullMode, depthWrite: Boolean): BlendedRasterVariant? = when {
            depthWrite && cullMode == CullMode.Back -> if (additive) AdditiveDepthBack else AlphaDepthBack
            depthWrite -> if (additive) AdditiveDepth else AlphaDepth
            cullMode == CullMode.Back -> if (additive) AdditiveBack else AlphaBack
            else -> null
        }
    }
}
