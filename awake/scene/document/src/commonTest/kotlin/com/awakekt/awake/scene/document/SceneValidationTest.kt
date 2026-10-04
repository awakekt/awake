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
                        ScenePrefabLink(path = ""),
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
        assertTrue(issues.any { "prefab_link.path must not be blank" in it.message })
        assertTrue(issues.any { "custom.type must not be blank" in it.message })
    }

    /** One prefab placed twice: each instance repeats the prefab's names, and only its own clash. */
    @Test
    fun aPrefabInstanceIsItsOwnNameScope() {
        fun fire(vararg sparks: String) = SceneNode(name = "fire", children = sparks.map { SceneNode(name = it) })
        fun camp(name: String, prefab: SceneNode) =
            SceneNode(name = name, components = listOf(ScenePrefabLink("fx/fire.prefab.json")), children = listOf(prefab))

        val twice = SceneDocument(nodes = listOf(camp("camp 0", fire("spark")), camp("camp 1", fire("spark")), SceneNode(name = "spark")))
        val clash = SceneDocument(nodes = listOf(camp("camp 0", fire("spark", "spark")), camp("camp 0", fire())))

        assertEquals(emptyList(), SceneValidator.validate(twice), "each instance and the document each have one 'spark'")
        assertEquals(
            listOf("camp 0/fire/spark", "camp 0"),
            SceneValidator.validate(clash).map { it.path },
            "names still clash within one instance, and instance nodes within the document",
        )
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
                    components = listOf(ScenePrefabLink(path = "")),
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
