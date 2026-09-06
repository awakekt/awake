/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class CaptureImageTest {

    @Test
    fun captureImageWritesPngFile() {
        val frame = composeFrame(100, 100) {
            Box(Modifier.size(100.dp).background(Color(1f, 0f, 0f, 1f)))
        }
        val target = File("build/test-snapshots/capture-test.png")
        if (target.exists()) target.delete()

        val written = frame.captureImage(target)
        assertTrue(written.exists(), "Snapshot file should be written")
        assertTrue(written.length() > 0, "Snapshot file should have non-zero bytes")
    }
}
