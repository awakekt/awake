/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReleaseCutTaskTest {
    private val dir: File = createTempDirectory("release-cut").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun aCutCountsOnFromTheNewestTagOnItsChannel() {
        val tags = mapOf("v[0-9]*-alpha.*" to "v0.1.0-alpha.8", "v[0-9]*" to "v0.1.0-dev.12")

        assertEquals("0.1.0-alpha.9", nextReleaseVersion("alpha", bump = null) { tags[it].orEmpty() })
        assertEquals("0.1.0-beta.1", nextReleaseVersion("beta", bump = null) { tags[it].orEmpty() })
        assertEquals("0.2.0-alpha.1", nextReleaseVersion("alpha", bump = "minor") { tags[it].orEmpty() })
        assertEquals("0.1.0", nextReleaseVersion("stable", bump = null) { tags[it].orEmpty() })
    }

    /** The bug this guards: a failed lookup read as "no tags" and restarted the channel at `.1`. */
    @Test
    fun aFailedTagLookupFailsTheCutInsteadOfReadingAsNoTags() {
        val failure = assertFailsWith<IllegalStateException> { latestTag(dir, "v[0-9]*") }

        assertContains(failure.message.orEmpty(), "not a git repository")
    }

    @Test
    fun aRepositoryWithNoMatchingTagIsEmptyNotAnError() {
        runGit(dir, "init", "--quiet")

        assertEquals("", latestTag(dir, "v[0-9]*-alpha.*"))
    }

    @Test
    fun anExistingTagIsNeverCutAgain() {
        runGit(dir, "init", "--quiet")
        runGit(dir, "-c", "user.name=test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false", "commit", "--allow-empty", "--quiet", "-m", "base")
        runGit(dir, "-c", "tag.gpgsign=false", "tag", "v0.1.0-alpha.8")

        val failure = assertFailsWith<IllegalStateException> { requireNewTag(dir, "v0.1.0-alpha.8") }
        assertContains(failure.message.orEmpty(), "already exists")
        requireNewTag(dir, "v0.1.0-alpha.9")
        assertEquals("0.1.0-alpha.9", nextReleaseVersion("alpha", bump = null) { latestTag(dir, it) })
    }
}
