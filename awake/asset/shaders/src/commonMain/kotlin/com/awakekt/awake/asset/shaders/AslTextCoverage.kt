/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.and
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec3

/**
 * The text coverage rule: the alpha a glyph pixel in [color] draws with for [coverage], the share of the pixel its
 * outline covers. One rule for every glyph whose coverage is that area, plain or decorated (#1061), so a decoration
 * never changes the weight of the text over it.
 *
 * It is Skia's mask gamma with Chromium's Linux constants. The ground is not known here, so it is taken to be the
 * text's inverse, `1 - textLuma`; the coverage gets a contrast boost that fades out as that ground darkens; and the
 * two are mixed in light linearised with gamma 1.2. The alpha is what gives that mix when the blend unit mixes in the
 * target's own encoding. Mid-grey text, whose assumed ground is itself, takes the boosted coverage.
 * `UiRasterizer.textCoverageAlpha` is the CPU copy of this block, held to it by TextCoverageRuleTwinTest; the rule
 * and its constants are documented in the UI guide's "Draw crisp text".
 */
internal fun AslBlockBuilder.textCoverageRule(coverage: AslExpr, color: AslExpr): AslExpr {
    val textLinear = let(
        "textLinear",
        dot(
            pow(
                max(color.rgb, vec3(0f.lit, 0f.lit, 0f.lit)),
                vec3(TEXT_GAMMA.lit, TEXT_GAMMA.lit, TEXT_GAMMA.lit),
            ),
            vec3(0.2126f.lit, 0.7152f.lit, 0.0722f.lit),
        ),
    )
    val textLuma = let("textLuma", pow(textLinear, 1f.lit / TEXT_GAMMA.lit))
    val groundLuma = let("groundLuma", 1f.lit - textLuma)
    val groundLinear = let("groundLinear", pow(groundLuma, TEXT_GAMMA.lit))
    val contrasted = let(
        "contrasted",
        coverage + (1f.lit - coverage) * TEXT_CONTRAST.lit * groundLinear * coverage,
    )
    val mixed = let(
        "mixed",
        pow(
            clamp(textLinear * contrasted + groundLinear * (1f.lit - contrasted), 0f.lit, 1f.lit),
            1f.lit / TEXT_GAMMA.lit,
        ),
    )
    // |textLuma - groundLuma| < TEXT_SAME_LUMA, with groundLuma = 1 - textLuma.
    val sameLuma = let(
        "sameLuma",
        (textLuma gt (0.5f - TEXT_SAME_LUMA / 2f).lit) and (textLuma lt (0.5f + TEXT_SAME_LUMA / 2f).lit),
    )
    val lumaSpan = let("lumaSpan", select(textLuma - groundLuma, 1f.lit, sameLuma))
    return let(
        "ruled",
        select(clamp((mixed - groundLuma) / lumaSpan, 0f.lit, 1f.lit), contrasted, sameLuma),
    )
}

/** The gamma the text coverage rule mixes text and ground in: Skia's text gamma on Linux, as Chromium sets it. */
internal const val TEXT_GAMMA = 1.2f

/** The text coverage rule's contrast boost over a white ground: Skia's text contrast on Linux, as Chromium sets it. */
internal const val TEXT_CONTRAST = 0.2f

/** How close the text's luma and its assumed ground's may be before the rule takes the boosted coverage as is. */
internal const val TEXT_SAME_LUMA = 1f / 256f
