/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.fixture

/**
 * Metadata descriptor for an authored scene discoverable by Awake Studio.
 */
data class StudioSceneDescriptor(
    val id: String,
    val title: String,
    val path: String,
    val description: String = "",
)

/**
 * Registry of available scenes in Studio.
 *
 * Replaces hardcoded scene paths, allowing the top bar scene selector, the Files tab, and
 * the fixture loader to discover, switch, and register scenes dynamically.
 */
object StudioSceneRegistry {
    val ROTATING_CUBE = StudioSceneDescriptor(
        id = "rotating-cube",
        title = "Rotating Cube",
        path = "assets/examples/rotating-cube.scene.json",
        description = "Standard PBR cube with spin animation and ground plane.",
    )

    val CESIUM_MAN = StudioSceneDescriptor(
        id = "cesium-man",
        title = "CesiumMan Animated",
        path = "assets/examples/cesium-man.scene.json",
        description = "Skeletal rigged skinned character with walking animation.",
    )

    private val defaultScenes = listOf(
        ROTATING_CUBE,
        CESIUM_MAN,
    )

    private val scenes = mutableListOf<StudioSceneDescriptor>().apply {
        addAll(defaultScenes)
    }

    val all: List<StudioSceneDescriptor>
        get() = scenes.toList()

    /** Registers a new scene descriptor or replaces an existing one matching [descriptor.id]. */
    fun register(descriptor: StudioSceneDescriptor) {
        scenes.removeAll { it.id == descriptor.id }
        scenes.add(descriptor)
    }

    /** Registers multiple scene descriptors. */
    fun registerAll(vararg descriptors: StudioSceneDescriptor) {
        descriptors.forEach { register(it) }
    }

    /** Registers a collection of scene descriptors. */
    fun registerAll(descriptors: Iterable<StudioSceneDescriptor>) {
        descriptors.forEach { register(it) }
    }

    /** Clears all registered scenes. */
    fun clear() {
        scenes.clear()
    }

    /** Restores the built-in demo fixture scenes. */
    fun resetToDefaults() {
        scenes.clear()
        scenes.addAll(defaultScenes)
    }

    fun findById(id: String): StudioSceneDescriptor? = scenes.firstOrNull { it.id == id }

    fun findByPath(path: String): StudioSceneDescriptor? = scenes.firstOrNull { it.path == path }

    val defaultScene: StudioSceneDescriptor get() = scenes.firstOrNull() ?: ROTATING_CUBE
}
