/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.application

/** Why a WebGPU device was lost, as the `GPUDeviceLostInfo.reason` the browser reported. */
enum class WebGpuDeviceLostReason {
    /**
     * Not by a call to `destroy()`: the GPU process crashed, the driver reset, or the browser
     * reclaimed the device under memory pressure.
     */
    Unknown,

    /** Something other than the engine called the device's `destroy()`. */
    Destroyed,

    ;

    internal companion object {
        /** The reason for the spec's string [value]; a value this enum does not know is [Unknown]. */
        fun of(value: String): WebGpuDeviceLostReason = if (value == "destroyed") Destroyed else Unknown
    }
}

/**
 * A lost WebGPU device: what [WebGpuEngine.onDeviceLost] receives.
 *
 * @property reason Why it was lost.
 * @property message The browser's own description. It is implementation-defined: show or record it,
 * do not parse it.
 */
data class WebGpuDeviceLost(
    val reason: WebGpuDeviceLostReason,
    val message: String,
)

/**
 * The cause an `AppErrorHandler` receives for a lost WebGPU device, with source
 * `AppErrorSource.GraphicsDevice`.
 *
 * @property loss The reason and the browser's message.
 */
class WebGpuDeviceLostException(
    val loss: WebGpuDeviceLost,
) : IllegalStateException("WebGPU device lost (${loss.reason}): ${loss.message}")
