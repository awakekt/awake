// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.passes.debug

import kotlin.test.Test
import kotlin.test.assertEquals

class DebugLineLayoutTest {

    @Test
    fun strideIsDerivedFromTheFormatNotHandCounted() {
        // Both backends alias FLOATS_PER_VERTEX rather than re-declaring 7 -- the drift that
        // once left webgpu's rounded quad at 15 against a shared 16.
        assertEquals(7, DebugLineLayout.Format.strideBytes / Float.SIZE_BYTES)
        assertEquals(DebugLineLayout.FLOATS_PER_VERTEX, DebugLineLayout.Format.strideBytes / Float.SIZE_BYTES)
    }
}
