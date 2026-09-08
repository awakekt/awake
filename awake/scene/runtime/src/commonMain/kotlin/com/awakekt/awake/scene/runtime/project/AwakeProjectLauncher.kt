/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.project

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lightweight project manifest representation for standalone game execution.
 */
@Serializable
data class StandaloneProjectConfig(
    val name: String,
    val defaultScene: String = "scenes/main.scene.json",
    val targetFrameRate: Int = 60,
    val physicsTickRate: Int = 60,
    val plugins: List<String> = emptyList(),
)

/**
 * Headless or platform launcher utility to bootstrap an Awake game from a project manifest and scene.
 */
object AwakeProjectLauncher {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses the project configuration JSON string.
     */
    fun parseConfig(projectJson: String): StandaloneProjectConfig = json.decodeFromString(StandaloneProjectConfig.serializer(), projectJson)

    /**
     * Instantiates a scene document into an active [World].
     *
     * @return The instantiated [Scene] containing the root nodes.
     */
    fun loadScene(
        world: World,
        sceneJson: String,
    ): Scene {
        val document = SceneLoader.decode(sceneJson)
        return SceneLoader.instantiate(document, world)
    }

    /**
     * Instantiates an already parsed [SceneDocument] into an active [World].
     */
    fun loadScene(
        world: World,
        document: SceneDocument,
    ): Scene = SceneLoader.instantiate(document, world)
}
