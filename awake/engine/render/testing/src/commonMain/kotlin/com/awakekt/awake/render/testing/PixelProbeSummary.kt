/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.testing

import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.capture.RgbaSample

/**
 * Summary of pixel color samples at key landmark positions across a render buffer.
 *
 * @property width Width of the sampled pixel surface in pixels.
 * @property height Height of the sampled pixel surface in pixels.
 * @property center RGBA color sample at the centre of the surface.
 * @property topLeft RGBA color sample at the top-left corner.
 * @property topRight RGBA color sample at the top-right corner.
 * @property bottomLeft RGBA color sample at the bottom-left corner.
 * @property bottomRight RGBA color sample at the bottom-right corner.
 */
data class PixelProbeSummary(
    val width: Int,
    val height: Int,
    val center: RgbaSample,
    val topLeft: RgbaSample,
    val topRight: RgbaSample,
    val bottomLeft: RgbaSample,
    val bottomRight: RgbaSample,
)

/** Five corners-and-centre samples for diagnostics and assertion messages. */
fun PixelMap.summarize(): PixelProbeSummary = PixelProbeSummary(
    width = width,
    height = height,
    center = sample(width / 2, height / 2),
    topLeft = sample(0, 0),
    topRight = sample(width - 1, 0),
    bottomLeft = sample(0, height - 1),
    bottomRight = sample(width - 1, height - 1),
)
