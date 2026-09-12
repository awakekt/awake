/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.capture

import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FramebufferDebugTest {
    @Test
    fun rgbaReadbackIsNormalisedToTightlyPackedFloats() {
        val data = FramebufferAttachmentData.fromRgba8(
            TextureAsset(
                data = byteArrayOf(0, 127, -1, 64),
                width = 1,
                height = 1,
            ),
        )

        assertEquals(FramebufferAttachment.Color0, data.attachment)
        assertEquals(4, data.channels)
        assertContentEquals(floatArrayOf(0f, 127f / 255f, 1f, 64f / 255f), data.values)
    }

    @Test
    fun unsupportedAttachmentsCannotLookLikeClearedPixels() {
        val data = FramebufferAttachmentData.unavailable(
            FramebufferAttachment.Stencil,
            "No stencil attachment was allocated.",
        )

        assertFalse(data.available)
        assertEquals(0, data.values.size)
        assertEquals("No stencil attachment was allocated.", data.reason)
    }
}
