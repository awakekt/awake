/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.test.Test
import kotlin.test.assertTrue

class BrowserMemoryWasmJsTest {
    @Test
    fun browserMemoryAndProfileHelpersDoNotThrow() {
        browserHeapUsedBytes()?.let { assertTrue(it >= 0L) }
        browserHeapLimitBytes()?.let { assertTrue(it > 0L) }
        browserDeviceMemoryMegabytes()?.let { assertTrue(it > 0L) }

        val profile = readDeviceProfile()
        profile.totalMemoryBytes?.let { assertTrue(it > 0L) }
    }
}
