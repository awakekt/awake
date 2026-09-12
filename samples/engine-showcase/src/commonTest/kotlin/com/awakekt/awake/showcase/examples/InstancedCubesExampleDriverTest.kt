/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Locks down the authored scene side of the "ghost cube" investigation.
 *
 * The grid is deliberately even-sized: its four innermost cells surround the world origin, but
 * no instance occupies it. If a cube is ever visible at screen centre, this test establishes it
 * did not come from the showcase's transform list and the renderer/binding path must be checked.
 */
class InstancedCubesExampleDriverTest {
    @Test
    fun gridHasExactlyOneHundredTransformsAndNoOriginInstance() {
        val transforms = InstancedCubesExampleDriver.gridTransforms()

        assertEquals(1_024, transforms.size)
        assertFalse(transforms.any { it.m03 == 0f && it.m13 == 0.5f && it.m23 == 0f })
    }
}
