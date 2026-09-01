/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.scene.controls.camera.CameraMode
import io.github.awakelab.awake.scene.controls.camera.ActiveCamera
import io.github.awakelab.awake.scene.controls.camera.CameraRig
import io.github.awakelab.awake.scene.rendering.Camera
import io.github.awakelab.awake.scene.runtime.Scene
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.scene.runtime.attachRenderableComponents
import kotlin.math.asin
import kotlin.math.atan2
import io.github.awakelab.awake.scene.runtime.SceneDocument
import io.github.awakelab.awake.scene.runtime.SceneLoader

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
        val toEye = camera.lens.eye - camera.lens.center
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
                distance = maxOf(toEye.length3(), EPSILON)
                pitch = asin((toEye.y / maxOf(toEye.length3(), EPSILON)).coerceIn(-1f, 1f))
                yaw = atan2(toEye.x, toEye.z)
                // Last: assigning `mode` above asks for a reset, which would throw the angles
                // just computed away and reframe every showcase to the mode's own defaults.
                needsReset = false
            },
        )
    }
}

private const val EPSILON = 0.0001f
