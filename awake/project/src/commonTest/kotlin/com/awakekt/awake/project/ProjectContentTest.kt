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
            rootPath = "/example-project",
            files = listOf(
                ProjectIndexEntry(
                    path = "assets/models/example.glb",
                    sizeBytes = 42,
                    sha256 = "a".repeat(64),
                    url = "files/assets/models/example.glb",
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
    fun theTagsListMustHoldDistinctTags() {
        assertEquals(emptyList(), ProjectContentValidator.manifestIssueDetails(manifest(tags = listOf("enemy", "pickup"))))

        val issues = ProjectContentValidator.manifestIssueDetails(manifest(tags = listOf("enemy", "enemy", "two words")))

        assertEquals(
            listOf("tags must not contain duplicates", "tags[2] \"two words\" is not a tag"),
            issues.map { it.message.substringBefore(":") },
        )
        assertTrue(issues.all { it.code == ProjectIssueCode.INVALID_MANIFEST })
    }

    @Test
    fun aSceneTagTheListDoesNotNameIsAWarning() {
        val issues = ProjectContentValidator.unlistedTagIssues(
            manifest(tags = listOf("enemy", "pickup")),
            sceneTags = listOf("enemy", "enmey", "enmey"),
            path = "scenes/main.scene.json",
        )

        val issue = issues.single()
        assertEquals(ProjectIssueCode.UNLISTED_TAG, issue.code)
        assertEquals(ProjectIssueSeverity.WARNING, issue.severity)
        assertEquals("scenes/main.scene.json", issue.path)
        assertTrue("\"enmey\"" in issue.message, issue.message)
    }

    @Test
    fun aProjectWithNoTagsListGetsNoTagWarnings() {
        assertEquals(emptyList(), ProjectContentValidator.unlistedTagIssues(manifest(), sceneTags = listOf("enemy")))
    }

    @Test
    fun aManifestWithTagsRoundTripsAndOneWithoutStillDecodes() {
        val tagged = manifest(tags = listOf("enemy", "pickup"))

        assertEquals(tagged, AwakeProjectValidator.decodeManifest(AwakeProjectValidator.encodeManifest(tagged)))
        val older = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        assertEquals(emptyList(), AwakeProjectValidator.decodeManifest(older).tags)
    }

    private fun manifest(tags: List<String> = emptyList()) = AwakeProjectManifest(
        id = "com.example.harbor-town",
        name = "Harbor Town",
        version = "1.0.0",
        entryScene = "scenes/main.scene.json",
        tags = tags,
    )

    @Test
    fun assetRootMatchingDoesNotAcceptSiblingDirectories() {
        assertTrue(ProjectContentValidator.isUnderAssetRoot("assets/model.glb", listOf("assets")))
        assertFalse(ProjectContentValidator.isUnderAssetRoot("assets-old/model.glb", listOf("assets")))
    }
}
