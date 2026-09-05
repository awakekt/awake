/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlackboardTest {

    @Test
    fun readWriteAndClear() {
        val bb = Blackboard()
        bb["targetDistance"] = 4.5f
        bb["isAlert"] = true
        bb["targetName"] = "Player1"

        assertEquals(4.5f, bb.get<Float>("targetDistance"))
        assertEquals(true, bb.get<Boolean>("isAlert"))
        assertEquals("Player1", bb.get<String>("targetName"))
        assertTrue(bb.contains("isAlert"))

        bb.remove("isAlert")
        assertFalse(bb.contains("isAlert"))

        bb.clear()
        assertFalse(bb.contains("targetDistance"))
        assertFalse(bb.contains("targetName"))
    }
}
