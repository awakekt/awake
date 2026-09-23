/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectContentTest {
    @Test
    fun projectIndexUsesMetadataOnlyEntries() {
        val index = ProjectIndex(
            rootPath = "/flyffawaken",
            files = listOf(
                ProjectIndexEntry(
                    path = "assets/models/aibat.glb",
                    sizeBytes = 42,
                    sha256 = "a".repeat(64),
                    url = "files/assets/models/aibat.glb",
                ),
            ),
        )

        assertTrue(ProjectContentValidator.indexIssues(index).isEmpty())
        assertEquals(42, index.files.single().sizeBytes)
    }

    @Test
    fun projectIndexRejectsUnsafeAndDuplicateEntries() {
        val index = ProjectIndex(
            files = listOf(
                ProjectIndexEntry("../secret", 1, "a".repeat(64), "files/secret"),
                ProjectIndexEntry("../secret", 1, "a".repeat(64), "files/secret"),
            ),
        )

        val issues = ProjectContentValidator.indexIssues(index)
        assertTrue(issues.any { it.contains("safe project-relative") })
        assertTrue(issues.any { it.contains("duplicate") })

        val codedIssues = ProjectContentValidator.indexIssueDetails(index)
        assertTrue(codedIssues.any { it.code == ProjectIssueCode.UNSAFE_PATH })
        assertTrue(codedIssues.any { it.code == ProjectIssueCode.DUPLICATE_INDEX_PATH })
        assertEquals("unsafe_path", codedIssues.first { it.code == ProjectIssueCode.UNSAFE_PATH }.codeValue)
    }

    @Test
    fun legacyMessagesAndStructuredIssuesShareTheSameValidation() {
        val manifest = AwakeProjectManifest(
            id = "invalid",
            name = "",
            version = "nope",
            entryScene = "../scene.json",
        )

        val details = ProjectContentValidator.manifestIssueDetails(manifest)
        assertEquals(details.map { it.message }, ProjectContentValidator.manifestIssues(manifest))
        assertTrue(details.any { it.code == ProjectIssueCode.INVALID_MANIFEST })
        assertTrue(details.any { it.code == ProjectIssueCode.UNSAFE_PATH })
    }

    @Test
    fun assetRootMatchingDoesNotAcceptSiblingDirectories() {
        assertTrue(ProjectContentValidator.isUnderAssetRoot("assets/model.glb", listOf("assets")))
        assertFalse(ProjectContentValidator.isUnderAssetRoot("assets-old/model.glb", listOf("assets")))
    }
}
