/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/**
 * Coarse hardware classification reported by a [GpuDevice] adapter.
 *
 * Render features and image-effect passes use this classification to select a sensible default
 * [EffectQuality] tier without coupling shared pass code to Vulkan `VkPhysicalDeviceType` or
 * WebGPU adapter strings.
 */
enum class GpuDeviceClass {
    /** Dedicated GPU with independent high-bandwidth VRAM. */
    Discrete,

    /** Desktop or laptop GPU sharing system memory with the CPU. */
    Integrated,

    /** Tile-based or low-power mobile GPU architecture. */
    Mobile,

    /** CPU-based rasterizer such as Lavapipe or SwiftShader. */
    Software,

    /** Unclassified adapter (for example when WebGPU adapter info is masked by the browser). */
    Unknown,
}

/**
 * Quality tier for multi-pass image effects.
 *
 * Shared effect builders in `:awake:engine:render:effects` map this tier to pass counts,
 * downscale divisors, or fallback draws before emitting an `ImagePassChain`.
 */
enum class EffectQuality {
    /** Disables multi-pass GPU image effects and uses cheap single-draw fallbacks where available. */
    Off,

    /** Minimal pass count and aggressive downscaling for mobile and software adapters. */
    Low,

    /** Balanced pass count and resolution for integrated GPUs and unclassified adapters. */
    Medium,

    /** Full pass count and resolution for discrete desktop GPUs. */
    High,
}

/**
 * Returns the default [EffectQuality] tier appropriate for this [GpuDeviceClass].
 */
fun GpuDeviceClass.recommendedEffectQuality(): EffectQuality = when (this) {
    GpuDeviceClass.Discrete -> EffectQuality.High
    GpuDeviceClass.Integrated -> EffectQuality.Medium
    GpuDeviceClass.Mobile -> EffectQuality.Low
    GpuDeviceClass.Software -> EffectQuality.Low
    GpuDeviceClass.Unknown -> EffectQuality.Medium
}
