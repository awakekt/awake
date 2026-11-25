/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class WeakRefTest {
    @Test
    fun getReturnsTargetUntilCleared() {
        val target = SampleTarget("harbor-town")
        val ref = WeakRef(target)

        assertSame(target, ref.get())

        ref.clear()
        assertNull(ref.get())

        // Clearing twice is idempotent.
        ref.clear()
        assertNull(ref.get())
    }

    private class SampleTarget(val name: String)
}
