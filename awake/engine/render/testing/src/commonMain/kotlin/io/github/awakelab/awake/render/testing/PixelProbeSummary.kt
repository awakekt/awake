/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.testing

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
