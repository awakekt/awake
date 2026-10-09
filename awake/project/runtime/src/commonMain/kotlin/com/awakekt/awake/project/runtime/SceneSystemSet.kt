/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.authoring.infrastructure.gameplayInput
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.runtime.SceneSystemPhase

/**
 * What a scene's systems need from the host and cannot read from the scene itself. A host that draws
 * passes its renderer; a game server or a test makes one with [headless], and the systems that draw
 * are left out.
 */
class SceneHostServices private constructor(
    private val gpu: Renderer?,
    /** The keyboard, pointer and touch state, with what the UI owns of it. Read every frame. */
    val input: () -> GameplayInput,
    /** The physics world the scene's bodies and characters live in; null runs the scene without physics. */
    val physics: PhysicsWorld?,
    /** What the scene's capabilities read from the project's files, as [loadSceneContent] reads it. */
    val content: SceneContent,
) {
    /** Services for a host that draws through [renderer]. */
    constructor(
        input: () -> GameplayInput,
        renderer: Renderer,
        physics: PhysicsWorld? = null,
        content: SceneContent = SceneContent.Empty,
    ) : this(renderer, input, physics, content)

    /**
     * What systems create GPU content through, such as particle sprites and shader effects. Throws on a
     * [headless] host, whose plan leaves out every system that would ask; see [hasRenderer].
     */
    val renderer: Renderer
        get() = checkNotNull(gpu) { "This scene host is headless: it has no renderer" }

    /** Whether there is a [renderer]: false on a host made by [headless]. */
    val hasRenderer: Boolean get() = gpu != null

    /** Makes services for a host with no renderer. */
    companion object {
        /**
         * Services for a host that doesn't draw, such as a game server or a test: the scene's systems
         * that simulate run, and the ones that draw (particle sprites, shader effects) are left out.
         */
        fun headless(
            input: () -> GameplayInput,
            physics: PhysicsWorld? = null,
            content: SceneContent = SceneContent.Empty,
        ): SceneHostServices = SceneHostServices(null, input, physics, content)
    }
}

/**
 * The systems a scene plays with, in the order they run: every [fixed] system on each fixed step,
 * then [interpolate], then every [frame] system once per rendered frame, which is the order
 * `runProject` runs them in. [close] when the scene stops, to release what the systems created.
 */
class SceneSystemSet internal constructor(
    val fixed: List<System>,
    val frame: List<System>,
    private val release: () -> Unit,
) : AutoCloseable {
    private val interpolated = fixed.filterIsInstance<InterpolatedSystem>()

    /**
     * Blends the last two states of every [fixed] system that keeps them, as
     * [InterpolatedSystem.interpolate] describes, by the [alpha] the host's fixed-step loop reports.
     * Call it after the fixed steps and before the [frame] systems, which read what it places, as
     * `runProject` does; without it, bodies move in fixed-step jumps.
     */
    fun interpolate(world: World, alpha: Float) = interpolated.forEach { it.interpolate(world, alpha) }

    override fun close() = release()
}

/**
 * The systems [scene] runs, and none it doesn't, built from [services]: Core's capabilities first, then
 * [capabilities] in order, each adding what [scene] uses of it. A host that runs a scene in a world of
 * its own, as an editor's Play does, runs these instead of keeping a list of its own, so it gains a
 * system when Core does:
 * - `movement_control`: keyboard intent, moved by physics when the entity has a
 *   `character_controller` and straight through the world when it doesn't
 * - `physics_body`, `character_controller` and a `terrain` collider: the physics step, the
 *   character controller and the terrain collider, when [SceneHostServices.physics] is given; a `mesh`
 *   collision shape adds [com.awakekt.awake.scene.physics.MeshColliderSystem], which reads the
 *   triangles from [SceneHostServices.content]
 * - `camera_rig`: the camera system
 * - `spinControl`: spinning
 * - `locomotion_animation` and `keyframe_animation`: their clips and looping tracks
 * - `texture_clips`: the sprite sheet's clips, stepped on the scene's clock
 * - `particle_emitter`: its emitters, with the sprites in [SceneHostServices.content]
 * - `shader_effect`: its documents, drawn through [SceneHostServices.renderer] when it takes content
 *   features
 * - `day_cycle`: the sun's path and the blended sky, light and fog
 * - `paged_terrain`: its cells streamed around the primary camera from the index and shared images in
 *   [SceneHostServices.content], drawn through [SceneHostServices.renderer] when it takes content
 *   features, with collision cells when it is a `collider` and [SceneHostServices.physics] is given
 * - `canvas_element`s with an action: [CanvasActionSystem]
 * - `patrol`, `chase` and `flee`, with a `navigation` component to route them over: the behaviours and
 *   the system that answers their route requests
 * - skinned glTF animation, always
 *
 * Pass the same [capabilities] to [loadSceneContent] for the content. `runProject` is built on the same
 * decision, so the two cannot drift apart. These are only the scene's own systems: the host still
 * places the scene, resolves its assets, picks the camera, resolves transforms and draws. A scene that
 * needs physics with no [SceneHostServices.physics] runs without those systems.
 */
fun sceneSystemsFor(
    scene: SceneDocument,
    services: SceneHostServices,
    capabilities: List<SceneCapability> = emptyList(),
): SceneSystemSet {
    val fixed = mutableListOf<System>()
    val frame = mutableListOf<System>()
    val releasing = SystemReleases()
    sceneSystemSpecsFor(scene, hasPhysics = services.physics != null, capabilities, hasRenderer = services.hasRenderer).forEach { spec ->
        val system = releasing.keep(spec.create(services))
        when (spec.phase) {
            SceneSystemPhase.Fixed -> fixed += system
            SceneSystemPhase.Frame -> frame += system
        }
    }
    return SceneSystemSet(fixed, frame, releasing::release)
}

/** One system a scene runs: its name, the phase it runs in, and how to build it. */
internal class SceneSystemSpec(
    val name: String,
    val phase: SceneSystemPhase,
    val create: (SceneHostServices) -> System,
)

/**
 * The decision, as data: which systems [scene] needs, in the order they run, from Core's capabilities
 * and then [capabilities]. Both [sceneSystemsFor] and `runProject` build from this list, so there is
 * one place that says what a component needs.
 */
internal fun sceneSystemSpecsFor(
    scene: SceneDocument,
    hasPhysics: Boolean,
    capabilities: List<SceneCapability> = emptyList(),
    hasRenderer: Boolean = true,
): List<SceneSystemSpec> {
    val plan = SceneSystemPlan(hasPhysics, hasRenderer)
    installedCapabilities(capabilities).forEach { it.plan(scene, plan) }
    return plan.specs
}

/** Core's capabilities followed by [extra], refusing two with one id. */
internal fun installedCapabilities(extra: List<SceneCapability>): List<SceneCapability> {
    val installed = CORE_CAPABILITIES + extra
    val repeated = installed.groupBy { it.id }.filterValues { it.size > 1 }.keys
    require(repeated.isEmpty()) { "More than one capability has the id ${repeated.joinToString()}" }
    return installed
}

/**
 * Registers [sceneSystemSpecsFor] on this app's schedule for [project], building each system once the
 * runtime exists so it can read the runtime's input and renderer, and closing them when it stops.
 */
internal fun SceneAppDsl.registerSystemSpecs(project: LoadedProject) {
    val releasing = SystemReleases()
    sceneSystemSpecsFor(project.scene, hasPhysics = project.physics != null, project.capabilities).forEach { spec ->
        system(spec.name, spec.phase) {
            val services = SceneHostServices(
                input = { gameplayInput() },
                renderer = renderer,
                physics = project.physics,
                content = project.content,
            )
            releasing.keep(spec.create(services))
        }
    }
    onDispose { releasing.release() }
}

/** The systems that hold something to give back, closed newest first when the scene stops. */
private class SystemReleases {
    private val closing = mutableListOf<AutoCloseable>()

    fun keep(system: System): System = system.also { if (it is AutoCloseable) closing += it }

    fun release() {
        closing.asReversed().forEach(AutoCloseable::close)
        closing.clear()
    }
}
