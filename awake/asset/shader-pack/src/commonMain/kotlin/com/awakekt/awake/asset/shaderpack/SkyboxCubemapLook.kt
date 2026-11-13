/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

/**
 * How a cubemap sky looks this frame, read each frame so a day cycle can change it without
 * reloading the panorama. The defaults draw the cubemap as baked.
 *
 * @property exposure Multiplies the sampled colour.
 * @property blend How far the first cubemap has blended into the second, 0 to 1.
 * @property tintR Red of the colour the sky is multiplied by.
 * @property tintG Green of the colour the sky is multiplied by.
 * @property tintB Blue of the colour the sky is multiplied by.
 */
class SkyboxCubemapLook(
    var exposure: Float = 1f,
    var blend: Float = 0f,
    var tintR: Float = 1f,
    var tintG: Float = 1f,
    var tintB: Float = 1f,
)
