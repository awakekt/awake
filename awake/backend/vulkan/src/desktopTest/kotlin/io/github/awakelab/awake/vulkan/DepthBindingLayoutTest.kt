/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.vulkan.renderer.depthBindingSlot
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
