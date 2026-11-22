/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutableMapKeyChecksTest {

    @Test
    fun detectsMutableDataClassesAndIgnoresImmutableOnes() {
        val sources = listOf(
            """
            data class TerrainComponent(val id: Int, var isVisible: Boolean = true)
            data class ImmutableKey(val id: Int, val name: String) {
                var bodyOnlyState: Int = 0
            }
            class RegularClass(var mutable: Int)
            """.trimIndent(),
        )
        assertEquals(setOf("TerrainComponent"), mutableDataClassNames(sources))
    }

    @Test
    fun findsMapAndSetKeysUsingMutableDataClassesAndIgnoresComments() {
        val mutable = setOf("TerrainComponent", "Camera")
        val source = """
            // HashMap<TerrainComponent, Int>() in a comment is ignored
            val trackers = HashMap<TerrainComponent, String>()
            val seen = mutableSetOf<Camera>()
            val safe = HashMap<String, TerrainComponent>()
            val literal = "HashMap<TerrainComponent, Int>()"
        """.trimIndent()

        assertEquals(
            listOf(2 to "TerrainComponent", 3 to "Camera"),
            mutableMapKeySites(source, mutable),
        )
    }

    @Test
    fun baselineRejectsGrowthAndRequiresShrinking() {
        val baseline = parseMutableMapKeyBaseline(
            """
            # comment
            awake/a.kt 1
            awake/b.kt 1
            """.trimIndent(),
        )
        val sites = mapOf(
            "awake/a.kt" to listOf(10 to "TerrainComponent"),
            "awake/c.kt" to listOf(4 to "Camera"),
        )
        val failures = mutableMapKeyFailures(sites, baseline)
        assertEquals(2, failures.size)
        assertTrue(failures.any { "awake/b.kt" in it && "0 remain" in it }, failures.toString())
        assertTrue(failures.any { "awake/c.kt" in it && "Camera" in it }, failures.toString())
    }
}
