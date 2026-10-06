/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.document.SceneComponentCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scene document components reference page has one row per registered component id and one
 * field row per serialized field. A component or field added without a row fails here.
 */
class SceneComponentReferenceDocsTest {

    private val page = File("../../../website/docs/reference/scene-document-components.md").readText().replace("\r\n", "\n")

    @Test
    fun everyRegisteredComponentHasARowAndEveryFieldIsListed() {
        installEveryComponentKit()
        val registered = SceneComponentCatalog.schemas()
        val rows = Regex("""^\| \[`([a-z_]+)`]\(#""", RegexOption.MULTILINE)
            .findAll(page).map { it.groupValues[1] }.toSet()

        assertEquals(registered.keys.sorted(), rows.sorted(), "component ids on the reference page")
        val missing = registered.flatMap { (id, schema) ->
            val section = page.substringAfter("## `$id`\n", missingDelimiterValue = "")
                .substringBefore("\n## ")
            if (section.isEmpty()) {
                listOf("no `## \\`$id\\`` section")
            } else {
                schema.children.map { it.name }.filter { "| `$it` |" !in section }.map { "`$id` field `$it` has no row" }
            }
        }
        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
    }
}
