package com.awakekt.awake.template

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.select
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.project.runtime.LIT_SHADOW_MATERIAL
import com.awakekt.awake.project.runtime.builtInSceneAssets
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.scene.authoring.dsl.camera
import com.awakekt.awake.scene.authoring.dsl.meshRenderer
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.skyboxEntity
import com.awakekt.awake.scene.authoring.dsl.sun
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.SpinSystem

/** The window title. */
const val GAME_TITLE = "Awake Game"

/** Creates a fresh application lifecycle for a platform host: a lit cube spinning above the ground. */
fun createGame(windowBackend: AppWindowBackend = AppWindowBackend.VULKAN): AwakeAppLifecycle = app {
    window {
        title = GAME_TITLE
        size(1280, 720)
        backend.select(windowBackend)
    }
    scene("game") {
        // The ready-made meshes ("cube", "ground", ...) and the lit, shadowed material.
        assets { builtInSceneAssets() }
        sun()
        skyboxEntity()
        entity("camera") {
            transform()
            camera(lens = Lens.perspective(eye = Vec3f(0f, 4f, 8f), center = Vec3f(0f, 0.5f, 0f)))
        }
        onReady {
            world.scene {
                entity("ground") {
                    transform()
                    meshRenderer(requireMesh("ground"), requireMaterial(LIT_SHADOW_MATERIAL), CullMode.Back)
                }
                entity("cube") {
                    transform(y = 1f)
                    meshRenderer(requireMesh("cube"), requireMaterial(LIT_SHADOW_MATERIAL), CullMode.Back)
                    configure(::SpinControl) { speed = 1f }
                }
            }
        }
        frameSystem("spin-clock") { SpinClock() }
        frameSystem("spin") { SpinSystem() } // writes each angle to Transform.rotation.y
    }
}

/** Turns each [SpinControl] by its speed every frame. A system is how gameplay runs each frame. */
class SpinClock : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(SpinControl::class) { _, spin -> spin.radians += spin.speed * delta }
    }
}
