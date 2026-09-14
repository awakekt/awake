/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.debug

import com.awakekt.awake.render.pipeline.CullMode
import kotlin.test.Test
import kotlin.test.assertEquals

class DebugLinePipelinePolicyTest {
    @Test
    fun debugLinesAreAnAlwaysVisibleNonTriangleOverlay() {
        assertEquals(DebugLineDepthMode.AlwaysVisible, DebugLinePipelinePolicy.depthMode)
        assertEquals(CullMode.None, DebugLinePipelinePolicy.cullMode)
    }
}
