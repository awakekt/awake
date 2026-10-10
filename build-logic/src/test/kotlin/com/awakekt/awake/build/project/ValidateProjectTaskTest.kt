/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.project

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

/** `validateProject` against a small project on disk: the manifest's `tags`. */
class ValidateProjectTaskTest {
    private val root: File = createTempDirectory("validate-project").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun aManifestListingItsTagsValidates() {
        validate(tags = """["harbor", "ferry.dock", "crane-2"]""")
    }

    @Test
    fun aDuplicateOrMalformedTagIsAnError() {
        val failure = assertFailsWith<GradleException> { validate(tags = """["harbor", "harbor", "two words"]""") }

        assertContains(failure.message!!, "manifest.tags must not contain duplicates")
        assertContains(failure.message!!, "manifest.tags[2] \"two words\" is not a tag")
    }

    @Test
    fun tagsThatAreNotStringsAreAnErrorRatherThanACrash() {
        assertContains(assertFailsWith<GradleException> { validate(tags = "\"harbor\"") }.message!!, "manifest.tags must be an array of strings")
        assertContains(assertFailsWith<GradleException> { validate(tags = "[{}, 3]") }.message!!, "manifest.tags must contain only strings")
    }

    private fun validate(tags: String) {
        root.resolve("awake.project.json").writeText(
            """{ "formatVersion": 1, "id": "com.example.harbor", "name": "Harbor Town", "version": "1.0.0", """ +
                """"entryScene": "scenes/main.scene.json", "tags": $tags }""",
        )
        root.resolve("scenes").mkdirs()
        root.resolve("scenes/main.scene.json").writeText("""{ "version": 1, "name": "main", "nodes": [] }""")
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")
        (project.tasks.getByName("validateProject") as ValidateProjectTask).validate()
    }
}
