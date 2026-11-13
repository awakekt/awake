/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.authoring.infrastructure.gameplayInput
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.runtime.SceneHostRole
import com.awakekt.awake.scene.runtime.requireKnownSystemNames

/**
 * Core's capabilities by typed handle, in the order their systems run: what a scene app passes to
 * [features] to get the systems a project gets for the same scene. A game's own are objects of its own.
 */
object CoreFeatures {
    /** Input actions, the camera rig and movement. */
    val Controls: SceneCapability get() = ControlsCapability

    /** The scene's canvas. */
    val Canvas: SceneCapability get() = CanvasCapability

    /** Paged terrain streamed around the camera. */
    val StreamedTerrain: SceneCapability get() = StreamedTerrainCapability

    /** Terrain drawing. */
    val Terrain: SceneCapability get() = TerrainCapability

    /** Bodies, characters and colliders. */
    val Physics: SceneCapability get() = PhysicsCapability

    /** Navigation, perception, behaviour trees and the route behaviours. */
    val Ai: SceneCapability get() = AiCapability

    /** Spin, locomotion, keyframes and clips. */
    val Motion: SceneCapability get() = MotionCapability

    /** 3D and 2D particles. */
    val Particles: SceneCapability get() = ParticlesCapability

    /** The day cycle. */
    val DayCycle: SceneCapability get() = DayCycleCapability

    /** Shader effects. */
    val ShaderEffects: SceneCapability get() = ShaderEffectsCapability

    /** Audio sources and the listener. */
    val Audio: SceneCapability get() = AudioCapability

    /** Skinned glTF animation. */
    val SkinnedAnimation: SceneCapability get() = SkinnedAnimationCapability

    /** Every one of Core's, in run order: what a project runs with before its own. */
    val all: List<SceneCapability> get() = CORE_CAPABILITIES
}

/**
 * Registers the systems [features] plan for [scene] in this scene app, by the same function and in the
 * same order as `runProject` and [sceneSystemsFor] plan them: `features(scene, CoreFeatures.all + game)`
 * registers what a project with `game` registers for that scene. Each system is registered under the
 * name and phase it was planned with, so [com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime.plannedSystems]
 * lists them as the project's [SceneSystemSet.planned] does.
 *
 * Systems the app registers itself with `system`, `fixedSystem` and the others, and systems from another
 * `features` call, run in the order the calls were made, within each phase: call this before them and
 * the planned systems run first. Nothing is reordered across calls.
 * Pass the features in the order they should run, Core's first as in [CoreFeatures.all].
 *
 * The systems get the app's renderer and input; the scene's physics world, content and the other host
 * services a project's runner provides, such as spawning and scene switching, are not wired: pass
 * [physics] and [content], and a feature that needs the rest asks the app for it.
 * Systems that hold something to give back are closed when the app is disposed.
 *
 * @param scene The scene document whose components decide which systems the features plan.
 * @param features The features to plan, in order; two with one id are refused.
 * @param physics The physics world, or null for none, as [SceneHostServices.physics].
 * @param content What the features loaded, as [SceneHostServices.content].
 * @param hasRenderer Whether the app draws; false plans no system that needs a renderer.
 * @param hostRole What the app runs: [SceneHostRole.SimulationOnly] leaves out the presentation systems.
 * @param disabledSystems Names to leave out; an unknown one is refused, listing the known names.
 */
@Suppress("LongParameterList")
fun SceneAppDsl.features(
    scene: SceneDocument,
    features: List<SceneCapability>,
    physics: PhysicsWorld? = null,
    content: SceneContent = SceneContent.Empty,
    hasRenderer: Boolean = true,
    hostRole: SceneHostRole = SceneHostRole.Full,
    disabledSystems: Set<String> = emptySet(),
) {
    val installed = installedFeatures(features)
    val specs = sceneSystemSpecsOf(scene, physics != null, hasRenderer, installed)
    requireKnownSystemNames(disabledSystems, CoreSystems.all + installed.flatMap { it.systemNames } + specs.map { it.name }, "the scene app's features")
    val closing = mutableListOf<AutoCloseable>()
    var services: SceneHostServices? = null
    specs.filter { it.name !in disabledSystems && hostRole.runs(it.role) }.forEach { spec ->
        system(spec.name, spec.phase, spec.role) {
            val host = services ?: run {
                val base = if (hasRenderer) SceneHostServices({ gameplayInput() }, renderer, physics, content) else SceneHostServices.headless({ gameplayInput() }, physics, content)
                base.withHostRole(hostRole).withFixedStep(fixedStep).withCoroutineScope(session.coroutineScope).also { services = it }
            }
            spec.create(host).also { if (it is AutoCloseable) closing += it }
        }
    }
    onDispose {
        closing.asReversed().forEach(AutoCloseable::close)
        closing.clear()
    }
}

/** [features] refused when two share an id, with the message a project gives for its capabilities. */
private fun installedFeatures(features: List<SceneCapability>): List<SceneCapability> {
    val repeated = features.groupBy { it.id }.filterValues { it.size > 1 }.keys
    require(repeated.isEmpty()) { "More than one capability has the id ${repeated.joinToString()}" }
    return features
}
