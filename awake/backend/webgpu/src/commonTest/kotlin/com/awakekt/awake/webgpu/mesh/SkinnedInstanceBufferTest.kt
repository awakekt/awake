/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.mesh

import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import kotlin.test.Test
import kotlin.test.assertEquals

class SkinnedInstanceBufferTest {
    @Test
    fun paletteGroupFollowsSharedJointPaletteSlot() {
        assertEquals(
            BindingLayout.Standard.slot(BindingSemantic.JointPalette).toUInt(),
            SkinnedInstanceBuffer.PALETTE_GROUP,
        )
    }
}
