/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.app

import com.awakekt.awake.webgpu.application.launchWebGpuGame

fun main() = launchWebGpuGame(applicationFactory = ::createEngineShowcaseWebGpuApplication)
