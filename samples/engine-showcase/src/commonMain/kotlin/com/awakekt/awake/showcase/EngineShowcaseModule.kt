/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.appModule
import com.awakekt.awake.engine.platform.core.AppModule
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.authoring.infrastructure.cameraInputSystem
import com.awakekt.awake.scene.authoring.infrastructure.cameraSystem
import com.awakekt.awake.scene.authoring.infrastructure.playerInputSystem
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.particles.ParticleSystem
import com.awakekt.awake.scene.runtime.defaultInfrastructureSystems
import com.awakekt.awake.showcase.examples.CharacterExampleDriver
import com.awakekt.awake.showcase.examples.ShowcasePhysics
import com.awakekt.awake.showcase.examples.TerrainPhysicsExampleDriver
import com.awakekt.awake.showcase.ui.ShowcaseOverlay

/** The independent engine-demo host. [initialShowcaseId] is injectable for focused smoke tests. */
internal fun engineShowcaseModule(initialShowcaseId: String = DEFAULT_SHOWCASE_ID): AppModule {
    require(EngineShowcases.any { it.id == initialShowcaseId }) { "Unknown showcase '$initialShowcaseId'." }
    val loader = EngineShowcaseLoader()
    val selection = ShowcaseSelection(initialShowcaseId)

    return appModule {
        scene("engine-showcase") {
            assets {
                mesh("cube") { renderer.createMesh(generate { cube(size = 1f, colored = true) }) }
                mesh("ground") { renderer.createMesh(generate { plane(size = 10f, colored = false) }) }
                material("lit-shadow") { renderer.createMaterial(uniformFloatCount = LitShadowUniformLayout.total) }
                registerEngineShowcaseAssets()
            }
            frameSystem("showcase-driver") {
                val runtime = this
                object : System {
                    override fun update(world: World, delta: Float) {
                        // Before advancing, not after: a switch requested by the UI last frame
                        // replaces the world the driver is about to read.
                        selection.consumeRequest()?.let { loader.activate(it, runtime) }
                        ShowcaseDebugToggles.applyTo(world, runtime.renderer)
                        loader.advance(selection.current, runtime, delta)
                    }
                }
            }
            // Orbit and zoom, on every showcase. The camera stack already existed
            // (CameraRig/CameraSystem/CameraInputSystem) and nothing here installed it, so every
            // demonstration was a fixed viewpoint -- you could not look at the thing being
            // demonstrated from anywhere else.
            cameraInputSystem()
            cameraSystem()
            // After cameraSystem, so it corrects the eye the rig just computed.
            frameSystem("cameraCollision") { CharacterExampleDriver.cameraCollisionSystem() }
            frameSystem("animation") { AnimationSystem() }
            frameSystem("particles") { ParticleSystem() }
            // Fixed, not frame: physics is the one system here that integrates, so it is the one
            // that must not see a variable delta.
            fixedSystem("physics") { ShowcasePhysics.system() }
            // After physics, because drainContacts hands over what the last step produced.
            fixedSystem("goalZone") { TerrainPhysicsExampleDriver.goalZoneSystem() }
            // Fixed too, and for the same reason: a character that integrates gravity on a frame
            // delta falls at a different speed on every machine.
            fixedSystem("character") {
                CharacterExampleDriver.system(
                    input = { GameplayInput(requireService(Input::class).currentSnapshot, uiOwnership) },
                )
            }
            // Fills MovementControl from the keyboard; the character driver reads it.
            playerInputSystem()
            infrastructureSystems { defaultInfrastructureSystems() }
            onReady {
                // Before the first activate: the terrain showcase attaches its bodies on
                // activation and needs a world to attach them to.
                ShowcasePhysics.world = createJoltPhysicsWorld()
                loader.preload()
                loader.activate(selection.current, this)
            }
            // A Jolt world owns native allocations that outlive the JVM's idea of garbage, and
            // this one is reachable from an object that outlives the app module.
            onDispose {
                ShowcasePhysics.world?.destroy()
                ShowcasePhysics.world = null
            }
            content { ShowcaseOverlay(selection, EngineShowcases) }
        }
    }
}

internal const val DEFAULT_SHOWCASE_ID = "point-lights"
