/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.app

import io.github.awakelab.awake.webgpu.application.launchWebGpuGame

fun main() {
    launchWebGpuGame(applicationFactory = ::createStudioWebGpuApplication)
}
