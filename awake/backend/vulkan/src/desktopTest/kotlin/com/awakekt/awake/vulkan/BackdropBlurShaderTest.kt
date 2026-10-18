/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** The backdrop blur's two pack shaders translate to SPIR-V through naga, as Vulkan loads them. */
class BackdropBlurShaderTest {
    @Test
    fun bothBackdropShadersCompile() {
        for (name in listOf("ui_backdrop_blur", "ui_backdrop_composite")) {
            val pair = runBlocking { packShaderPair(name) }
            assertTrue(pair.vertex.isNotEmpty(), "$name produced no SPIR-V")
        }
    }
}
