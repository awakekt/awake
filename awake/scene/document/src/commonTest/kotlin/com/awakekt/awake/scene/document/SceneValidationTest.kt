/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private data class TestUniqueComponent(val id: String) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (id.isBlank()) {
            add(SceneValidationIssue(path, "testUnique.id must not be blank"))
        }
    }
}

class SceneValidationTest {

    @Test
    fun validatorReportsDuplicateNamesAndComponentValidationIssues() {
        val document = SceneDocument(
            name = "invalid",
            nodes = listOf(
                SceneNode(
                    name = "nodeA",
                    components = listOf(
                        TestUniqueComponent(id = ""),
                        ScenePrefabLink(prefabGuid = ""),
                    ),
                ),
                SceneNode(
                    name = "nodeA",
                    components = listOf(SceneCustomComponent(type = "", payload = kotlinx.serialization.json.JsonNull)),
                ),
            ),
        )

        val issues = SceneValidator.validate(document)

        assertTrue(issues.any { "duplicate node name" in it.message })
        assertTrue(issues.any { "testUnique.id must not be blank" in it.message })
        assertTrue(issues.any { "prefabLink.prefabGuid must not be blank" in it.message })
        assertTrue(issues.any { "custom.type must not be blank" in it.message })
    }

    @Test
    fun validatorEnforcesSingleInstanceForComponentsDisallowingMultiples() {
        val document = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "nodeB",
                    components = listOf(
                        TestUniqueComponent(id = "1"),
                        TestUniqueComponent(id = "2"),
                    ),
                ),
            ),
        )

        val issues = SceneValidator.validate(document)
        assertEquals(1, issues.size)
        assertTrue(issues.single().message.contains("expected at most 1"))
    }

    @Test
    fun requireValidThrowsOnInvalidScene() {
        val document = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "bad-node",
                    components = listOf(ScenePrefabLink(prefabGuid = "")),
                ),
            ),
        )

        val exception = assertFailsWith<SceneValidationException> {
            SceneValidator.requireValid(document)
        }
        assertEquals(1, exception.issues.size)
        assertTrue(exception.message.orEmpty().contains("bad-node"))
    }
}
