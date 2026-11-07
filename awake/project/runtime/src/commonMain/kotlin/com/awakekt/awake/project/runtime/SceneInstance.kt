/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.host.FixedStepReport
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneDocument
import kotlinx.coroutines.CoroutineScope

/**
 * One independent copy of a loaded project's scene, made with [newSceneInstance]: a [world] of its
 * own with the scene's entities in it, the systems [sceneSystems] plans for it, and a physics world
 * of its own when the scene has bodies or characters. A game server runs many of them from one
 * [LoadedProject], one per party, room or match, and makes and closes them as those start and end.
 *
 * What was loaded once is shared and only read: the [scene] document, the component registry and
 * capabilities, the glTF models, and the scene's content, such as its collision meshes, baked
 * navigation grid and AI definitions. What changes is the instance's own: its [world], its systems and
 * its physics world, so a body moved in one instance stays where it is in every other.
 *
 * Step an instance on one thread at a time, as any [World]. Different instances may step on different
 * threads, since nothing they share is written once the project has loaded.
 *
 * An instance outlives what its project does next: a [switchTo] or [LoadedProject.close] leaves it
 * playing the scene it was made from. [close] it when it ends, since its physics world's native memory
 * outlives garbage collection.
 *
 * @property scene The document this instance plays, the same instance as its project's.
 * @property world This instance's world, holding the scene's entities as [newSceneInstance] made them.
 */
class SceneInstance internal constructor(
    val scene: SceneDocument,
    val world: World,
    private val physics: PhysicsWorld?,
    private val content: SceneContent,
    private val capabilities: List<SceneCapability>,
) : AutoCloseable {
    private val systemSets = mutableListOf<SceneSystemSet>()

    /** Whether the instance has a physics world of its own: the scene has bodies or characters. */
    val hasPhysics: Boolean get() = physics != null

    /** Whether [close] has run. */
    var isClosed: Boolean = false
        private set

    /**
     * The systems this instance's scene runs over its [world], as [LoadedProject.sceneSystems] plans
     * them, with this instance's physics world and the project's shared content. With no [renderer], as
     * on a game server, the systems that draw are left out. [audio], [fixedStep] and [coroutineScope]
     * are as [LoadedProject.sceneSystems] describes. [close] closes every set built here; a host that
     * replaces one before then closes the old one itself.
     *
     * Throws [IllegalStateException] once the instance is closed.
     */
    fun sceneSystems(
        input: () -> GameplayInput,
        renderer: Renderer? = null,
        audio: AudioPlayer? = null,
        fixedStep: FixedStepReport? = null,
        coroutineScope: CoroutineScope,
    ): SceneSystemSet {
        check(!isClosed) { "The scene instance is closed; make another with newSceneInstance" }
        val drawn = if (renderer == null) {
            SceneHostServices.headless(input, physics, content)
        } else {
            SceneHostServices(input, renderer, physics, content)
        }
        val scoped = drawn.withCoroutineScope(coroutineScope)
        val stepped = fixedStep?.let(scoped::withFixedStep) ?: scoped
        val services = audio?.let(stepped::withAudio) ?: stepped
        return sceneSystemsFor(scene, services, capabilities).also { systemSets += it }
    }

    /**
     * Ends this instance: closes the systems [sceneSystems] built, destroys every entity in its [world],
     * and destroys its physics world, in that order, so no system or entity outlives the bodies it used.
     * The project, its other instances and the data they share are untouched. Closing twice is harmless.
     */
    override fun close() {
        if (isClosed) return
        isClosed = true
        systemSets.asReversed().forEach(SceneSystemSet::close)
        systemSets.clear()
        world.clear()
        physics?.destroy()
    }
}

/**
 * Makes another, independent instance of the scene this project plays now, sharing what the project
 * loaded for it, as [SceneInstance] describes, with a world and planned systems of its own and, when
 * the scene has bodies or characters, a physics world of its own from the project's `physicsWorld`
 * factory. No file is read: it costs the scene's entities and its physics world, not a load.
 *
 * Headless-safe: a project loaded with `hasRenderer = false` makes instances that draw nothing and
 * reach no GPU. The project's own playing scene, and the world a host or [runProject] plays it in, are
 * not touched, and the project can make as many instances as the host has memory for.
 *
 * Throws [IllegalStateException] once the project is closed.
 */
suspend fun LoadedProject.newSceneInstance(): SceneInstance {
    check(!closed) { "The project is closed; load it again to make an instance of its scene" }
    val source = playing
    val physics = if (PhysicsCapability.needsPhysics(source.document)) {
        // The project made the scene's own world from this factory, so a scene with bodies has one.
        checkNotNull(physicsWorld) { "${source.path} has physics bodies or characters and the project has no physicsWorld factory" }()
    } else {
        null
    }
    val world = World()
    var made = false
    try {
        source.document.instantiate(world, componentRegistry ?: SceneComponentRegistry())
        made = true
    } finally {
        // A scene that fails to instantiate leaves no physics world behind that nothing would destroy.
        if (!made) physics?.destroy()
    }
    return SceneInstance(source.document, world, physics, source.content, capabilities)
}
