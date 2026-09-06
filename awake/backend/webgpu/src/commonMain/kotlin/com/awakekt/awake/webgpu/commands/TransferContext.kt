/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.commands

import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.handles.CommandPoolHandle

// Phase 2.5 (Web/WebGPU, decision D7) milestone 1: compile-only stub -- see
// docs/mvp-plan.md.
class TransferContext(graphicsDevice: GraphicsDevice) {
    var commandPool: CommandPoolHandle = CommandPoolHandle(0)

    fun runOneTimeCommands(block: (Long) -> Unit) {
        TODO("WebGPU not yet implemented -- see Phase 2.5, docs/mvp-plan.md")
    }

    fun destroy() {
        TODO("WebGPU not yet implemented -- see Phase 2.5, docs/mvp-plan.md")
    }
}
