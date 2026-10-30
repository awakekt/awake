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
import com.awakekt.awake.scene.scene2d.SceneParticleEmitter2D
import com.awakekt.awake.scene.scene2d.SceneSprite
import com.awakekt.awake.scene.scene2d.SceneTilemap

/**
 * Logs each tag [scene], the project's scene at [path], uses that the manifest's `tags` list leaves out:
 * a typo guard, so loading goes on.
 */
internal fun warnUnlistedTags(manifest: AwakeProjectManifest, scene: SceneDocument, path: String = manifest.entryScene) {
    ProjectContentValidator.unlistedTagIssues(manifest, scene.nodes.flatMap { it.tags() }, path)
        .forEach { issue -> log.warn { "${issue.path}: ${issue.message}" } }
}

/**
 * Logs each sorting layer [scene]'s sprites and tilemaps name that the manifest's `sortingLayers` list
 * leaves out, which draws on the default layer: a typo guard, so loading goes on.
 */
internal fun warnUnlistedSortingLayers(manifest: AwakeProjectManifest, scene: SceneDocument, path: String = manifest.entryScene) {
    ProjectContentValidator.unlistedSortingLayerIssues(manifest, scene.nodes.flatMap { it.sortingLayers() }, path)
        .forEach { issue -> log.warn { "${issue.path}: ${issue.message}" } }
}

private fun SceneNode.tags(): List<String> =
    components.filterIsInstance<SceneTag>().flatMap { it.tags } + children.flatMap { it.tags() }

private fun SceneNode.sortingLayers(): List<String> = components.mapNotNull { component ->
    when (component) {
        is SceneSprite -> component.sortingLayer
        is SceneTilemap -> component.sortingLayer
        is SceneParticleEmitter2D -> component.sortingLayer
        else -> null
    }
} + children.flatMap { it.sortingLayers() }

private val log = Logger("project")
