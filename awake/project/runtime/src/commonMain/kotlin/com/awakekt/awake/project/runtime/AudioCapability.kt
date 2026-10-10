/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.audio.NoOpAudioPlayer
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.audio.AudioListener
import com.awakekt.awake.scene.audio.AudioSourceBinding
import com.awakekt.awake.scene.audio.AudioSystem
import com.awakekt.awake.scene.audio.SceneAudioSource
import com.awakekt.awake.scene.audio.loadAudioClips
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.document.SceneDocument

/**
 * A scene's `audio_source`s: each clip is read from the project's files when it loads, and plays
 * through the host's [SceneHostServices.audio], heard from the active camera. A host with no audio
 * player, such as a game server or a test, runs the sources silently.
 */
internal object AudioCapability : SceneCapability {
    override val id = "com.awakekt.awake.audio"
    override val components = listOf(AudioSourceBinding)

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        if (scene.uses(SceneAudioSource::class)) content[CoreSceneContent.AudioClips] = loadAudioClips(scene, files)
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (!scene.uses(SceneAudioSource::class)) return
        plan.frame("audio-listener") { CameraListenerSystem() }
        plan.frame("audio") { AudioSystem(it.audio ?: NoOpAudioPlayer(), it.content[CoreSceneContent.AudioClips].orEmpty()) }
    }
}

/**
 * Keeps the [AudioListener] on the camera the scene is seen through, so a 3D sound fades and pans
 * from where the player looks, and moves it when another camera becomes active.
 */
internal class CameraListenerSystem : System {
    private var listening: Entity? = null

    /** A field rather than a lambda in [update], so finding the camera allocates nothing per frame. */
    private var active: Entity? = null
    private val findActive: (Entity, ActiveCamera) -> Unit = { entity, _ -> if (active == null) active = entity }

    override fun update(world: World, delta: Float) {
        active = null
        world.queryEach(ActiveCamera::class, findActive)
        val camera = active ?: return
        if (camera == listening && world.has(camera, AudioListener::class)) return
        listening?.takeIf(world::isAlive)?.let { world.remove(it, AudioListener::class) }
        world.add(camera, AudioListener())
        listening = camera
    }
}
