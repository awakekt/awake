/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.vulkan.renderer.depthBindingSlot
import kotlin.test.Test
import kotlin.test.assertEquals

class DepthBindingLayoutTest {

    @Test
    fun initialDepthBindUsesTheAuthoredPipelineSlot() {
        val layout = BindingLayout.of(
            BindingSemantic.Material to 2,
            BindingSemantic.ShadowDepth to 4,
        )

        assertEquals(4, depthBindingSlot(layout))
    }
}
