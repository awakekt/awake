/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.camera.aimAt
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.attachRenderableComponents

/** Owns the loaded showcase documents and activates them through the shared scene lifecycle. */
internal class EngineShowcaseLoader {
    private val documents = mutableMapOf<String, SceneDocument>()

    /** What is running, so switching away can tell it to clean up after itself. */
    private var active: EngineShowcase? = null

    suspend fun preload() {
        preloadEngineShowcases()
        EngineShowcases.forEach { showcase ->
            documents[showcase.id] = SceneLoader.loadFromResource(showcase.scenePath)
        }
    }

    fun activate(id: String, runtime: SceneAppLifecycleRuntime) {
        val showcase = requireNotNull(EngineShowcases.find { it.id == id }) { "Unknown showcase '$id'." }
        val document = requireNotNull(documents[id]) { "Showcase '$id' was not preloaded." }
        // Debug lines belong to whoever drew them last, and the renderer holds the last list it
        // was given rather than clearing per frame. Without this, switching from a navigation
        // showcase to one that draws no lines leaves its blocked markers hanging over the new
        // scene forever. Cleared here rather than in each driver: a driver that draws nothing has
        // no reason to know the previous one did.
        runtime.renderer.drawDebugLines(emptyList())
        // Before the scene closes: a showcase that spawned entities of its own still has a World
        // to remove them from, and after close() it would be removing them from the next scene's.
        active?.onDeactivated?.invoke(runtime)
        active = showcase
        runtime.sceneManager.close()
        val instance = runtime.sceneManager.switchTo(document)
        val library = runtime.requireAssetLibrary()
        // Through attachRenderableComponents rather than a loop of its own: that is where a mesh's
        // own bounds become a MeshBounds component, and without them culling, the spatial index
        // and the bounds overlay all have nothing to work on.
        instance.attachRenderableComponents { request -> library.resolve(runtime, request) }
        instance.attachOrbitCamera()
        showcase.onActivated?.invoke(instance, runtime)
    }

    fun advance(id: String, runtime: SceneAppLifecycleRuntime, delta: Float) {
        EngineShowcases.firstOrNull { it.id == id }?.driver?.invoke(runtime, delta)
    }
}

/**
 * Gives the scene's primary camera a rig, so it can be orbited and zoomed.
 *
 * Authored scene documents describe where a camera IS, not how it may be moved -- there is no
 * `cameraRig` component to write. Without one, `CameraSystem` skips every camera and each
 * showcase is a fixed viewpoint, which is a poor way to look at something being demonstrated.
 *
 * ThirdPerson is the orbit mode (yaw, pitch and zoom). It orbits `offsetPosition` when no target
 * entity is set, so the pivot is simply where the authored camera was already looking -- turning
 * the rig on does not move the shot, it only makes it movable, which is why the angles come from
 * the authored eye rather than the mode's defaults.
 *
 * Attached per activation because switching showcase builds a new world; the rig on the old one
 * goes with it.
 */
private fun Scene.attachOrbitCamera() {
    world.queryEach<Camera> { entity, camera ->
        if (!camera.isPrimary) return@queryEach
        world.add(entity, ActiveCamera())
        world.add(
            entity,
            CameraRig().apply {
                mode = CameraMode.ThirdPerson
                // A copy, not the lens's own vector. `CameraSystem` writes the pivot back into
                // `lens.center` every frame, so sharing the instance makes `offsetPosition` the
                // pivot -- and once a rig has a target, the pivot is `target.position +
                // offsetPosition`, which then accumulates the target's position every frame and
                // sends the camera into orbit. Harmless while nothing was targeted, which is why
                // it sat here until something was.
                offsetPosition = Vec3f(camera.lens.center.x, camera.lens.center.y, camera.lens.center.z)
                // Through `aimAt`, not by hand. The angles have to be the exact inverse of the
                // forward vector CameraSystem builds, and the hand-written version here had both
                // signs the wrong way round -- so every showcase asked its camera to glide to the
                // mirror image of its authored shot, below the ground it was framing.
                aimAt(camera.lens.eye, camera.lens.center)
                // Last: assigning `mode` above asks for a reset, which would throw the angles
                // just computed away and reframe every showcase to the mode's own defaults.
                needsReset = false
            },
        )
    }
}
