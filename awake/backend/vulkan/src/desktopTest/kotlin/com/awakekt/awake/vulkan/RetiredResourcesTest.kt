/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.renderer.RetiredResources
import kotlin.test.Test
import kotlin.test.assertEquals

/** A retired resource is freed only once a fence wait shows the frame recording at its retire done. */
class RetiredResourcesTest {
    private val freed = mutableListOf<String>()
    private val retired = RetiredResources<String>(framesInFlight = 2) { freed += it }

    @Test
    fun aWaitBeforeTheRecordingFrameIsSubmittedFreesNothing() {
        retired.frameSubmitted(0)
        retired.frameSlotIdle(1)
        retired.retire("layer")
        retired.frameSlotIdle(1) // a mid-frame wait, frame 1 still recording
        retired.frameSubmitted(1)
        retired.frameSlotIdle(0) // next frame waits on slot 0: submission 1 done, 2 not yet
        assertEquals(emptyList(), freed)
        retired.frameSubmitted(0)
        retired.frameSlotIdle(1) // slot 1's fence covers submission 2, the frame that read it
        assertEquals(listOf("layer"), freed)
        assertEquals(0, retired.pending)
    }

    @Test
    fun destroyAllFreesWhatIsLeft() {
        retired.retire("a")
        retired.destroyAll()
        assertEquals(listOf("a"), freed)
    }
}
