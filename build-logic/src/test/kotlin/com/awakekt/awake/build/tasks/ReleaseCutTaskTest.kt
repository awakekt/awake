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

    @Test
    fun olderChannelTagsNeverPullACutBackBehindANewerRelease() {
        runGit(dir, "init", "--quiet")
        runGit(
            dir, "-c", "user.name=test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false",
            "commit", "--allow-empty", "--quiet", "-m", "base",
        )
        for (tag in listOf("v0.1.0-dev.12", "v0.1.0-alpha.18", "v0.38.0-rc.4", "v0.38.0", "v0.42.0", "vulkan-v0.1.49")) {
            runGit(dir, "-c", "tag.gpgsign=false", "tag", tag)
        }

        assertEquals("v0.42.0", latestTag(dir, "v[0-9]*"))
        assertEquals("0.43.0", nextReleaseVersion("stable", bump = "minor") { latestTag(dir, it) })
        assertEquals("0.43.0-dev.1", nextReleaseVersion("dev", bump = "minor") { latestTag(dir, it) })
        assertEquals("0.43.0-rc.1", nextReleaseVersion("rc", bump = "minor") { latestTag(dir, it) })
    }

    @Test
    fun aStableTagSortsAheadOfItsOwnReleaseCandidates() {
        runGit(dir, "init", "--quiet")
        runGit(
            dir, "-c", "user.name=test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false",
            "commit", "--allow-empty", "--quiet", "-m", "base",
        )
        for (tag in listOf("v0.38.0-rc.1", "v0.38.0-rc.4", "v0.38.0")) {
            runGit(dir, "-c", "tag.gpgsign=false", "tag", tag)
        }

        assertEquals("v0.38.0", latestTag(dir, "v[0-9]*"))
        assertEquals("0.38.1", nextReleaseVersion("stable", bump = "patch") { latestTag(dir, it) })
    }

    @Test
    fun cuttingAtOrBehindTheNewestReleaseRequiresABump() {
        val afterStable = assertFailsWith<IllegalArgumentException> {
            nextReleaseVersion("rc", bump = null) { if (it == "v[0-9]*") "v0.42.0" else "" }
        }
        assertContains(afterStable.message.orEmpty(), "pass -Prelease.bump")

        val repeatStable = assertFailsWith<IllegalArgumentException> {
            nextReleaseVersion("stable", bump = null) { "v0.42.0" }
        }
        assertContains(repeatStable.message.orEmpty(), "pass -Prelease.bump")

        val lowerChannel = assertFailsWith<IllegalArgumentException> {
            nextReleaseVersion("beta", bump = null) { if (it == "v[0-9]*") "v0.43.0-rc.1" else "" }
        }
        assertContains(lowerChannel.message.orEmpty(), "pass -Prelease.bump")
    }
}
