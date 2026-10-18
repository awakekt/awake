/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.graphics2d.BackdropBlurQuality

/**
 * The broad kind of GPU a renderer runs on: enough to choose a default for a costly effect, not
 * a benchmark. Each backend maps its own device report onto one of these.
 */
enum class GpuDeviceClass {
    /** A CPU rasterizer, such as a software Vulkan driver or a browser's fallback adapter. */
    Software,

    /** A phone or tablet GPU, tiled and bandwidth-bound. */
    Mobile,

    /** A desktop GPU sharing memory with the CPU. */
    Integrated,

    /** A desktop GPU with its own memory. */
    Discrete,

    /** A device that reports nothing more specific, such as a browser adapter. */
    Unknown,
}

/**
 * The backdrop blur quality a renderer starts at on a [deviceClass] device. A software rasterizer
 * gets [BackdropBlurQuality.Off], the plain tint, since every blur pass there costs a full-screen
 * CPU fill; a mobile GPU gets [BackdropBlurQuality.Low]; a discrete one [BackdropBlurQuality.High].
 *
 * One decision for every backend, so the same device class never blurs on one backend and tints on
 * another.
 *
 * @param deviceClass The device the renderer runs on.
 * @return The quality to start at.
 */
fun recommendedBackdropBlurQuality(deviceClass: GpuDeviceClass): BackdropBlurQuality = when (deviceClass) {
    GpuDeviceClass.Software -> BackdropBlurQuality.Off
    GpuDeviceClass.Mobile -> BackdropBlurQuality.Low
    GpuDeviceClass.Integrated, GpuDeviceClass.Unknown -> BackdropBlurQuality.Medium
    GpuDeviceClass.Discrete -> BackdropBlurQuality.High
}
