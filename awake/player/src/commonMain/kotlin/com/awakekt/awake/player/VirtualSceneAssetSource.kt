/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.player

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.project.runtime.PROJECT_MANIFEST

private const val SYNTHETIC_PROJECT_ID = "com.awakekt.awake.player.scene"
private const val SYNTHETIC_PROJECT_VERSION = "1.0.0"
private const val SYNTHETIC_ENTRY_SCENE = "scenes/main.scene.json"

internal fun virtualSceneProjectSource(
    entryScenePath: String,
    projectName: String,
    backing: AssetSource,
): AssetSource {
    val manifestBytes = AwakeProjectValidator.encodeManifest(
        AwakeProjectManifest(
            id = SYNTHETIC_PROJECT_ID,
            name = projectName,
            version = SYNTHETIC_PROJECT_VERSION,
            entryScene = entryScenePath,
        ),
    ).encodeToByteArray()

    return object : AssetSource {
        override suspend fun read(path: AssetPath): Result<ByteArray> =
            if (path.value == PROJECT_MANIFEST) Result.success(manifestBytes) else backing.read(path)

        override suspend fun exists(path: AssetPath): Boolean =
            path.value == PROJECT_MANIFEST || backing.exists(path)
    }
}

internal fun virtualSceneDocumentSource(
    sceneJson: String,
    projectName: String,
    backing: AssetSource,
): AssetSource {
    val sceneBytes = sceneJson.encodeToByteArray()
    val base = virtualSceneProjectSource(
        entryScenePath = SYNTHETIC_ENTRY_SCENE,
        projectName = projectName,
        backing = backing,
    )
    return object : AssetSource {
        override suspend fun read(path: AssetPath): Result<ByteArray> =
            if (path.value == SYNTHETIC_ENTRY_SCENE) Result.success(sceneBytes) else base.read(path)

        override suspend fun exists(path: AssetPath): Boolean =
            path.value == SYNTHETIC_ENTRY_SCENE || base.exists(path)
    }
}

internal fun deriveSceneTitle(scenePath: String): String {
    val fileName = scenePath.substringAfterLast('/')
    val stem = fileName.removeSuffix(".scene.json").removeSuffix(".json")
    return stem
        .split('_', '-', ' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercaseChar) }
        .ifBlank { DEFAULT_PLAYER_TITLE }
}
