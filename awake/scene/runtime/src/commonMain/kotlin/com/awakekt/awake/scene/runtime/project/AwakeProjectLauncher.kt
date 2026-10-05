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
 *
 * @property name The human-readable name of the project.
 * @property defaultScene Path or asset key of the initial scene to load upon startup.
 * @property targetFrameRate Target render frame rate in frames per second.
 * @property physicsTickRate Fixed physics simulation frequency in hertz.
 * @property plugins List of plugin identifiers or package coordinates required by the project.
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
     *
     * @param projectJson The raw JSON string representing project configuration.
     * @return The parsed [StandaloneProjectConfig] descriptor.
     */
    fun parseConfig(projectJson: String): StandaloneProjectConfig = json.decodeFromString(StandaloneProjectConfig.serializer(), projectJson)

    /**
     * Instantiates a scene document into an active [World].
     *
     * @param world The target ECS [World] where entities are created.
     * @param sceneJson The raw JSON string representing the scene document.
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
     *
     * @param world The target ECS [World] where entities are created.
     * @param document The parsed scene document to instantiate.
     * @return The instantiated [Scene] containing the root nodes.
     */
    fun loadScene(
        world: World,
        document: SceneDocument,
    ): Scene = SceneLoader.instantiate(document, world)
}
