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

class ChangelogFragmentsTest {
    private val dir: File = createTempDirectory("changelog-fragments").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun aCutFilesHandwrittenNotesAndFragmentsUnderTheReleaseInSectionOrder() {
        val changelog = """
            # Changelog

            ## [Unreleased]

            ### Fixed

            - **Handwritten fix.**

            ## [0.1.0-alpha.1] - 2026-09-01

            ### Added

            - **Old.**
        """.trimIndent() + "\n"

        val cut = promoteUnreleased(
            changelog,
            version = "0.1.0-alpha.2",
            date = "2026-09-29",
            fragments = mapOf("Fixed" to listOf("- **Fragment fix.**\n"), "Added" to listOf("- **Fragment feature.**")),
        )

        assertEquals(
            """
            # Changelog

            ## [Unreleased]

            ## [0.1.0-alpha.2] - 2026-09-29

            ### Added

            - **Fragment feature.**

            ### Fixed

            - **Handwritten fix.**
            - **Fragment fix.**

            ## [0.1.0-alpha.1] - 2026-09-01

            ### Added

            - **Old.**
            """.trimIndent() + "\n",
            cut,
        )
    }

    @Test
    fun fragmentsAreReadBySectionInFileNameOrder() {
        File(dir, "fixed").mkdirs()
        File(dir, "fixed/b-second.md").writeText("- **B.**")
        File(dir, "fixed/a-first.md").writeText("- **A.**")
        File(dir, "README.md").writeText("How to write a fragment.")

        val fragments = readChangelogFragments(dir)

        assertEquals(listOf("Fixed"), fragments.keys.toList())
        assertEquals(listOf("a-first.md", "b-second.md"), fragments.getValue("Fixed").map { it.name })
    }

    @Test
    fun aMisnamedSectionFailsTheCutInsteadOfDroppingItsEntries() {
        File(dir, "fix").mkdirs()
        File(dir, "fix/typo.md").writeText("- **Lost.**")

        val failure = assertFailsWith<IllegalStateException> { readChangelogFragments(dir) }

        assertContains(failure.message.orEmpty(), "changelog/unreleased/fix is not a changelog section")
    }
}
