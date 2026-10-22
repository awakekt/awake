/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.vulkan.Version
import com.awakekt.awake.vulkan.Version.Companion.vkVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionTest {

    @Test
    fun roundTripsPackedVulkanVersion() {
        val testVersions = listOf(
            Version(1, 0, 0),
            Version(1, 1, 0),
            Version(1, 2, 0),
            Version(1, 3, 0),
            Version(1, 3, 268),
            Version(1, 4, 0),
        )

        for (v in testVersions) {
            val packed = v.vkVersion
            val unpacked = Version.fromVkVersion(packed)
            assertEquals(v, unpacked, "Failed to round-trip $v (packed = $packed)")
        }
    }

    @Test
    fun comparesVersionsCorrectly() {
        assertTrue(Version(1, 4, 0) > Version(1, 3, 0))
        assertTrue(Version(1, 3, 0) > Version(1, 2, 0))
        assertTrue(Version(1, 2, 0) > Version(1, 1, 0))
        assertTrue(Version(1, 1, 0) > Version(1, 0, 0))
        assertTrue(Version(1, 3, 268) > Version(1, 3, 0))
        assertTrue(Version(1, 3, 0) < Version(1, 3, 1))
        assertTrue(Version(1, 4, 0) >= Version(1, 4, 0))
        assertTrue(Version(1, 4, 0) <= Version(1, 4, 0))
    }

    @Test
    fun sortsVersionsInAscendingOrder() {
        val unsorted = listOf(
            Version(1, 4, 0),
            Version(1, 1, 0),
            Version(1, 3, 5),
            Version(1, 0, 0),
            Version(1, 3, 0),
            Version(1, 2, 0),
        )

        val sorted = unsorted.sorted()
        assertEquals(
            listOf(
                Version(1, 0, 0),
                Version(1, 1, 0),
                Version(1, 2, 0),
                Version(1, 3, 0),
                Version(1, 3, 5),
                Version(1, 4, 0),
            ),
            sorted,
        )
    }
}
