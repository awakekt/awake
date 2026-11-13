/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneLoader
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What [sceneSystemsFor] builds for the scenes below, as the classes of its fixed, pre-animation and
 * frame systems in run order, for a host that draws and one that does not. Recorded from main before systems had
 * names or roles, so a change that alters what a scene plans, with nothing disabled, fails here.
 */
class SceneSystemPlanGoldenTest {
    private val input = Input()
    private val gameplay = { GameplayInput(input.currentSnapshot, InputOwnership()) }

    private fun classes(scene: String, drawn: Boolean): List<String> {
        installProjectComponents()
        val services = if (drawn) SceneHostServices(gameplay, NoopRenderer()) else SceneHostServices.headless(gameplay)
        return sceneSystemsFor(SceneLoader.decode(scene), services).use { set ->
            (set.fixed + set.preAnimation + set.frame).map { it::class.simpleName.orEmpty() }
        }
    }

    @Test
    fun theSamplesPlanWhatTheyDidBeforeSystemsHadNames() {
        val actual = SCENES.mapValues { (_, scene) -> classes(scene, drawn = true) to classes(scene, drawn = false) }
        assertEquals(GOLDEN, actual.mapValues { (_, both) -> both.first.joinToString() to both.second.joinToString() })
    }

    private companion object {
        val SCENES: Map<String, String> = mapOf(
            "PLAIN_SCENE" to PLAIN_SCENE,
            "MOVEMENT_SCENE" to MOVEMENT_SCENE,
            "CAMERA_SCENE" to CAMERA_SCENE,
            "SPIN_SCENE" to SPIN_SCENE,
            "KEYFRAME_SCENE" to KEYFRAME_SCENE,
            "CLIPS_SCENE" to CLIPS_SCENE,
            "DAY_SCENE" to DAY_SCENE,
            "PARTICLE_SCENE" to PARTICLE_SCENE,
            "PARTICLE_2D_SCENE" to PARTICLE_2D_SCENE,
        )

        val GOLDEN: Map<String, Pair<String, String>> = mapOf(
            "PLAIN_SCENE" to (
                "RuntimeCanvasInputSystem, RuntimeCanvasActionsSystem, ParticleContentSystem, ParticleSystem, AnimationSystem" to
                    "RuntimeCanvasInputSystem, AnimationSystem"
                ),
            "MOVEMENT_SCENE" to (
                "PlayerInputSystem, RuntimeCanvasActionsSystem, MatrixRelativeMovementSystem, ParticleContentSystem, ParticleSystem, AnimationSystem" to
                    "PlayerInputSystem, MatrixRelativeMovementSystem, AnimationSystem"
                ),
            "CAMERA_SCENE" to (
                "RuntimeCanvasInputSystem, RuntimeCanvasActionsSystem, CameraSystem, ParticleContentSystem, ParticleSystem, AnimationSystem" to
                    "RuntimeCanvasInputSystem, CameraSystem, AnimationSystem"
                ),
            "SPIN_SCENE" to (
                "RuntimeCanvasInputSystem, RuntimeCanvasActionsSystem, SpinClockSystem, SpinSystem, ParticleContentSystem, ParticleSystem, AnimationSystem" to
                    "RuntimeCanvasInputSystem, SpinClockSystem, SpinSystem, AnimationSystem"
                ),
            "KEYFRAME_SCENE" to (
                "RuntimeCanvasInputSystem, RuntimeCanvasActionsSystem, KeyframeAnimationSystem, ParticleContentSystem, ParticleSystem, AnimationSystem" to
                    "RuntimeCanvasInputSystem, KeyframeAnimationSystem, AnimationSystem"
                ),
            "CLIPS_SCENE" to (
                "RuntimeCanvasInputSystem, RuntimeCanvasActionsSystem, TextureClipSystem, ParticleContentSystem, ParticleSystem, AnimationSystem" to
                    "RuntimeCanvasInputSystem, TextureClipSystem, AnimationSystem"
                ),
            "DAY_SCENE" to (
                "RuntimeCanvasInputSystem, RuntimeCanvasActionsSystem, ParticleContentSystem, ParticleSystem, DayCycleSystem, AnimationSystem" to
                    "RuntimeCanvasInputSystem, DayCycleSystem, AnimationSystem"
                ),
            "PARTICLE_SCENE" to (
                "RuntimeCanvasInputSystem, RuntimeCanvasActionsSystem, ParticleContentSystem, ParticleSystem, AnimationSystem" to
                    "RuntimeCanvasInputSystem, AnimationSystem"
                ),
            "PARTICLE_2D_SCENE" to (
                "RuntimeCanvasInputSystem, RuntimeCanvasActionsSystem, Particle2DSystem, ParticleContentSystem, ParticleSystem, AnimationSystem" to
                    "RuntimeCanvasInputSystem, Particle2DSystem, AnimationSystem"
                ),
        )

        const val PLAIN_SCENE = """
{ "version": 1, "name": "still", "nodes": [
  { "name": "Rock", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [] } ] }
"""

        const val MOVEMENT_SCENE = """
{ "version": 1, "name": "walk", "nodes": [
  { "name": "Player", "components": [ { "component": "movement_control", "speed": 6.0 } ] }
] }
"""

        const val CAMERA_SCENE = """
{ "version": 1, "name": "look", "nodes": [
  { "name": "Camera", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "camera", "primary": true },
    { "component": "camera_rig", "mode": "ThirdPerson", "target": "Player", "distance": 9.0, "pitch": -0.3,
      "offset": { "x": 0.0, "y": 1.5, "z": 0.0 } }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [] }
] }
"""

        const val SPIN_SCENE = """
{ "version": 1, "name": "spin", "nodes": [
  { "name": "Spinner", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "spin_control", "speed": 2.0 } ] }
] }
"""

        const val KEYFRAME_SCENE = """
{ "version": 1, "name": "slide", "nodes": [
  { "name": "Crate", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "keyframe_animation", "duration": 1.0,
      "position": [ { "time": 0.0, "value": { "x": 0.0, "y": 0.0, "z": 0.0 } },
                    { "time": 1.0, "value": { "x": 4.0, "y": 0.0, "z": 0.0 } } ] }
  ] }
] }
"""

        const val CLIPS_SCENE = """
{ "version": 1, "name": "sprite", "nodes": [
  { "name": "Hero", "components": [
    { "component": "texture_clips", "columns": 4, "rows": 4, "clip": "walk",
      "clips": {
        "walk": { "firstFrame": 8, "frameCount": 4, "framesPerSecond": 10.0 },
        "attack": { "firstFrame": 12, "frameCount": 3, "framesPerSecond": 10.0, "loop": false }
      } }
  ] }
] }
"""

        const val DAY_SCENE = """
{ "version": 1, "name": "day", "nodes": [
  { "name": "Sun", "components": [
    { "component": "light", "type": "Directional" },
    { "component": "day_cycle", "dayLengthSeconds": 4.0, "time": 0.25 } ] }
] }
"""

        const val PARTICLE_SCENE = """
{ "version": 1, "name": "sparks", "nodes": [
  { "name": "Fountain", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "particle_emitter", "texture": "dust.png" } ] }
] }
"""

        const val PARTICLE_2D_SCENE = """
{ "version": 1, "name": "sparks2d", "nodes": [
  { "name": "Torch", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "particle_emitter_2d", "spawnRate": 20.0 } ] }
] }
"""
    }
}
