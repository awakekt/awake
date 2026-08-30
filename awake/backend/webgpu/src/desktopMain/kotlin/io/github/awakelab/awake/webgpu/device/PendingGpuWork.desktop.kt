/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.device

import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.poll

/** wgpu-native fires callbacks only while the device is polled, so poll until the caller's work
 * reports done. A busy loop is acceptable here: readback is already a full pipeline stall, and
 * this runs only in the desktop TEST target. */
internal actual suspend fun GPUDevice.drivePendingWork(isDone: () -> Boolean) {
    while (!isDone()) {
        poll().getOrThrow()
    }
}
