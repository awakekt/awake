/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import kotlin.reflect.KClass

/**
 * A capability as scenes use it: the scene components it adds, what it reads from a project's files
 * before the scene runs, and the systems a scene that uses it runs.
 *
 * Core's controls, physics, AI, motion, particles, shader effects and skinned animation are
 * capabilities. A game, or a package it depends on, adds its own by passing them to [loadProject],
 * [loadSceneContent] and [sceneSystemsFor]; they run after Core's, in the order given. Capabilities
 * are linked when the game is built, like any dependency: Kotlin/Native and wasmJs cannot load code.
 *
 * A capability is code that ships in the game. An editor plugin only edits the data it reads (D36).
 */
interface SceneCapability {
    /**
     * Reverse-domain id, unique among the capabilities a project runs with. A project's manifest names
     * it in `plugins`; a plugin marked `required` refuses the load when no capability has its id.
     */
    val id: String

    /** Bindings for the scene components this capability adds, registered before a scene decodes. */
    val components: List<SceneComponentBinding<*, *>> get() = emptyList()

    /**
     * Reads what [scene] needs from the project's [files] before it runs, such as images or meshes,
     * and puts it into [content] for the systems to read. Called once per load, before the physics
     * world exists. Throws [IllegalArgumentException] for a scene this capability cannot run.
     */
    suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) = Unit

    /** Adds to [plan] the systems [scene] runs with this capability, in run order: none when it uses none. */
    fun plan(scene: SceneDocument, plan: SceneSystemPlan)
}

/**
 * Whether a node of this scene, at any depth, has a component of [type]: what a capability checks to
 * decide whether the scene uses it.
 */
fun SceneDocument.uses(type: KClass<out SceneComponent>): Boolean = nodes.any { it.has(type) }

/**
 * The systems a scene runs, as [SceneCapability.plan] adds them. Each is built once the host's
 * services exist, from [SceneHostServices]; a system that holds something to give back implements
 * [AutoCloseable] and is closed when the scene stops.
 *
 * @property hasPhysics Whether the host gave a physics world; a system that steps bodies needs one.
 * @property hasRenderer Whether the host draws; a system that makes GPU content needs one. A
 *   [SceneHostServices.headless] host, such as a game server, has none.
 */
class SceneSystemPlan internal constructor(val hasPhysics: Boolean, val hasRenderer: Boolean = true) {
    internal val specs = mutableListOf<SceneSystemSpec>()
    private var physicsSystem: PhysicsSystem? = null

    /**
     * The one physics step this plan's systems share. A body is destroyed only through the system that
     * built it, so a system that removes bodies, such as streamed terrain's collision cells, needs it.
     */
    internal fun physicsSystem(services: SceneHostServices): PhysicsSystem =
        physicsSystem ?: PhysicsSystem(requireNotNull(services.physics)).also { physicsSystem = it }

    /** Adds a system run on every fixed step, named [name] in the scene's schedule. */
    fun fixed(name: String, create: (SceneHostServices) -> System) = add(name, SceneSystemPhase.Fixed, create)

    /** Adds a system run once per rendered frame, named [name] in the scene's schedule. */
    fun frame(name: String, create: (SceneHostServices) -> System) = add(name, SceneSystemPhase.Frame, create)

    private fun add(name: String, phase: SceneSystemPhase, create: (SceneHostServices) -> System) {
        require(specs.none { it.name == name }) { "Two systems are named '$name'; each system in a scene needs its own name" }
        specs += SceneSystemSpec(name, phase, create)
    }
}

/**
 * The name of one piece of [SceneContent], typed by what it holds. Keys compare by identity, so a
 * capability declares each of its keys once and reads with the same instance.
 *
 * @param T The type of the content the key names.
 * @property name What the content is, for messages.
 */
class SceneContentKey<T : Any>(val name: String) {
    override fun toString(): String = name
}

/**
 * What capabilities read from a project's files for its systems, by [SceneContentKey]: the sprites of
 * its particle emitters, the documents of its shader effects, the triangles of its collision meshes,
 * and whatever a game's own capabilities load. [loadSceneContent] fills it.
 */
class SceneContent private constructor(private val values: Map<SceneContentKey<*>, Any>) {
    /** The content under [key], or null when nothing loaded it. */
    // The builder only pairs a key with a value of the key's own type.
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: SceneContentKey<T>): T? = values[key] as T?

    /** Collects content while capabilities load it. */
    class Builder internal constructor() {
        private val values = LinkedHashMap<SceneContentKey<*>, Any>()

        /** Stores [value] under [key]; a key holds one value. */
        operator fun <T : Any> set(key: SceneContentKey<T>, value: T) {
            require(key !in values) { "Scene content '$key' was loaded twice" }
            values[key] = value
        }

        internal fun build(): SceneContent = SceneContent(values.toMap())
    }

    /** The empty content. */
    companion object {
        /** No content: a scene whose capabilities load nothing. */
        val Empty: SceneContent = SceneContent(emptyMap())

        /** Content built by [block], for a host or a test that has its own. */
        fun build(block: Builder.() -> Unit): SceneContent = Builder().apply(block).build()
    }
}

/**
 * Reads what [scene]'s capabilities need from the project's [files]: the sprites of its particle
 * emitters, the triangles of its collision meshes, its shader documents, and what [capabilities] load.
 * A host that builds a scene's systems with [sceneSystemsFor] passes the result as
 * [SceneHostServices.content]; [loadProject] reads it for a project. Throws [IllegalArgumentException]
 * for content a capability cannot load.
 */
suspend fun loadSceneContent(
    scene: SceneDocument,
    files: AssetSource,
    capabilities: List<SceneCapability> = emptyList(),
): SceneContent = loadContent(scene, files, installedCapabilities(capabilities), label = "The scene")

/** Runs each of [installed]'s [SceneCapability.load] in order, prefixing a refusal with [label]. */
internal suspend fun loadContent(
    scene: SceneDocument,
    files: AssetSource,
    installed: List<SceneCapability>,
    label: String,
): SceneContent {
    val content = SceneContent.Builder()
    for (capability in installed) {
        try {
            capability.load(scene, files, content)
        } catch (problem: IllegalArgumentException) {
            throw IllegalArgumentException("$label: ${problem.message}", problem)
        }
    }
    return content.build()
}
