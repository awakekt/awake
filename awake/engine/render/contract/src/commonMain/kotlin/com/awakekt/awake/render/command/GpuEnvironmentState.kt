/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.color.Color

/**
 * Backend-neutral feature state carried by a compiled GPU pass.
 *
 * Scene and game code owns the richer environment authoring model. The render-pipeline module
 * lowers it to this small value before submitting the packet to the hardware contract.
 */
data class GpuEnvironmentState(
    val showSky: Boolean = false,
    val horizonColor: Color = Color.Black,
    val zenithColor: Color = Color.Black,
    val fogDensity: Float = 0f,
    val fogColor: Color = Color.Black,
    val shadowsEnabled: Boolean = true,
) {
    companion object {
        val Default = GpuEnvironmentState()
    }
}
