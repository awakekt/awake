/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.project.ProjectContentValidator
import com.awakekt.awake.scene.core.SceneTag
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode

/** Logs each tag [scene] uses that the manifest's `tags` list leaves out: a typo guard, so loading goes on. */
internal fun warnUnlistedTags(manifest: AwakeProjectManifest, scene: SceneDocument) {
    ProjectContentValidator.unlistedTagIssues(manifest, scene.nodes.flatMap { it.tags() }, manifest.entryScene)
        .forEach { issue -> log.warn { "${issue.path}: ${issue.message}" } }
}

private fun SceneNode.tags(): List<String> =
    components.filterIsInstance<SceneTag>().flatMap { it.tags } + children.flatMap { it.tags() }

private val log = Logger("project")
