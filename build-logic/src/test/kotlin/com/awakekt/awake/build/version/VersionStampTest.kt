/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.version

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionStampTest {
    @Test
    fun theThreeShapesParseAndFormatBack() {
        listOf("1.2.3", "0.1.0-beta.4", "10.0.0-rc.12", "0.0.0").forEach {
            assertEquals(it, ReleaseVersion.parse(it).toString())
        }
        assertEquals(ReleaseVersion(0, 1, 0, ReleaseVersion.Stage.BETA, 1), ReleaseVersion.parse("0.1.0-beta.1"))
    }

    @Test
    fun otherShapesAreRejected() {
        listOf("1.2", "v1.2.3", "1.2.3-alpha.1", "1.2.3-rc", "1.2.3-rc.", "01.2.3", "1.2.3-beta.01", "1.2.3+5", "", "a.b.c")
            .forEach { assertNull(ReleaseVersion.parseOrNull(it), it) }
        assertContains(assertFailsWith<IllegalArgumentException> { ReleaseVersion.parse("1.2") }.message.orEmpty(), "X.Y.Z-rc.N")
    }

    @Test
    fun betaPrecedesRcPrecedesStable() {
        val ordered = listOf("0.9.9", "1.0.0-beta.1", "1.0.0-beta.2", "1.0.0-beta.10", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.1.0", "2.0.0")
        val parsed = ordered.map(ReleaseVersion::parse)
        assertEquals(parsed, parsed.shuffled().sorted())
        assertTrue(ReleaseVersion.parse("1.0.0-rc.9") < ReleaseVersion.parse("1.0.0"))
        assertTrue(ReleaseVersion.parse("1.0.0-beta.9") < ReleaseVersion.parse("1.0.0-rc.1"))
    }

    @Test
    fun theStampHoldsVersionAndCommit() {
        val source = generateVersionStamp("1.2.0-rc.1", "abc1234", "com.example.app")
        assertContains(source, "package com.example.app")
        assertContains(source, "public object VersionStamp")
        assertContains(source, "public const val VERSION: String = \"1.2.0-rc.1\"")
        assertContains(source, "public const val COMMIT: String = \"abc1234\"")
    }

    @Test
    fun aBlankCommitIsUnknownAndABadVersionFails() {
        assertContains(generateVersionStamp("1.0.0", " ", "p", "Build"), "COMMIT: String = \"unknown\"")
        assertContains(generateVersionStamp("1.0.0", "x", "p", "Build"), "public object Build")
        assertFailsWith<IllegalArgumentException> { generateVersionStamp("unspecified", "x", "p") }
    }

    @Test
    fun aFailingGitReadsAsUnknown() {
        assertEquals("abc1234", shortCommit { "abc1234\n" })
        assertEquals(UNKNOWN_COMMIT, shortCommit { error("not a git repository") })
        assertEquals(UNKNOWN_COMMIT, shortCommit { "" })
    }
}
