/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.deposit

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.command.GpuEnvironmentState

/** Default crisp frosted snow tint for [SurfaceDeposit]. */
val DefaultSurfaceDepositColor = Color(r = 0.95f, g = 0.97f, b = 1.0f, a = 1f)

/**
 * Scene-wide dynamic upward-facing surface deposit component (snow, frost, volcanic ash, desert
 * dust, moss). Every field may be updated in place per frame without allocating; the renderer
 * packs it once into the frame uniform block and shaders apply it dynamically by world-space
 * upward normal (`dot(N, +Y)`) with multi-scale procedural hash-fBm breakup, micro-bump relief,
 * dual-tone crevice shading, and optional crystalline glint across skinned characters, props,
 * buildings, and terrain.
 *
 * Individual entities may scale, opt out, or randomize their clump layout via
 * [com.awakekt.awake.scene.rendering.mesh.SurfaceDepositMask].
 *
 * @property enabled Whether dynamic surface deposit shading is active.
 * @property color Display-space primary crest/highlight tint of the accumulated surface layer.
 * @property amount Accumulation coverage in `[0, 1]`; `0` disables shading with a GPU early-out.
 * @property slopeThreshold Minimum upward normal (`worldNormal.y`) where deposits settle.
 * @property softness Edge feathering and organic clump breakup width (`0.02..1`).
 * @property roughness PBR roughness override on coated surfaces (`0` wet sheen, `1` matte powder).
 * @property noiseScale World-space frequency of organic drift/clump breakup.
 * @property secondaryColor Optional crevice/hollow contrast tint (`null` auto-derives a darker shaded tone).
 * @property glint Micro-sparkle glint intensity in `[0, 1]` (`0` for matte ash/dust, positive for snow/frost).
 * @property grainStrength Procedural micro-texture and micro-bump relief contrast in `[0, 1]`.
 * @property seed Per-storm spatial randomization seed shifting drift placement and wind-stretch angle.
 */
data class SurfaceDeposit(
    var enabled: Boolean = false,
    var color: Color = DefaultSurfaceDepositColor,
    var amount: Float = 0f,
    var slopeThreshold: Float = GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_SLOPE,
    var softness: Float = GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_SOFTNESS,
    var roughness: Float = GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_ROUGHNESS,
    var noiseScale: Float = GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_NOISE_SCALE,
    var secondaryColor: Color? = null,
    var glint: Float = 0f,
    var grainStrength: Float = GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_GRAIN,
    var seed: Float = 0f,
)
