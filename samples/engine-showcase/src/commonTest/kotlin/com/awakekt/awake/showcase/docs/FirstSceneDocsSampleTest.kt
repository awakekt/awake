/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.docs

// --8<-- [start:imports]
import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.authoring.dsl.camera
import com.awakekt.awake.scene.authoring.dsl.directionalLight
import com.awakekt.awake.scene.authoring.dsl.mesh
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.Transform
// --8<-- [end:imports]
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// --8<-- [start:turntable]
/** Turns every entity that has a [SpinControl] around its Y axis, `speed` radians per second. */
class Turntable : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(Transform::class, SpinControl::class) { _, transform, spin ->
            spin.radians += spin.speed * delta
            transform.rotation.y = spin.radians
        }
    }
}
// --8<-- [end:turntable]

// --8<-- [start:app]
fun firstScene(): AwakeAppLifecycle = app {
    window {
        title = "Hello AwakeKt"
        size(1280, 720)
    }
    scene("first-scene") {
        // GPU assets, built by name the first time something asks for them.
        assets {
            mesh("cube") { renderer.createMesh(generate { cube(size = 1f, colored = true) }) }
            material("lit") { renderer.createMaterial(LitShadowUniformLayout) }
        }
        // Runs every frame, before the scene is drawn.
        frameSystem("turntable") { Turntable() }
        // The renderer exists from here on, so meshes and materials can be built.
        onReady {
            // --8<-- [start:scene-dsl]
            world.scene {
                entity("camera") {
                    camera(lens = Lens.perspective(eye = Vec3f(0f, 1.5f, 4f), center = Vec3f(0f, 0.5f, 0f)))
                }
                entity("sun") {
                    directionalLight(direction = Vec3f(0.4f, 0.8f, 0.4f))
                }
                entity("cube") {
                    transform(y = 0.5f)
                    mesh(requireMesh("cube"), requireMaterial("lit"))
                    configure(::SpinControl) { speed = 1f }
                }
            }
            // --8<-- [end:scene-dsl]
        }
    }
}
// --8<-- [end:app]

/** The "Your first scene" tutorial includes the samples above; this checks what it says they do. */
class FirstSceneDocsSampleTest {

    @Test
    fun theTurntableTurnsTheCubeAtItsSpeed() = runTest {
        val game = firstScene()
        game.ready(DrawlessRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val cube = assertNotNull(runtime.findEntity("cube"))
        assertNotNull(runtime.world.get<MeshRenderer>(cube))

        repeat(FRAMES) { game.update(FRAME_SECONDS, 640f, 360f) }

        val turned = assertNotNull(runtime.world.get<Transform>(cube)).rotation.y
        assertEquals(FRAMES * FRAME_SECONDS, turned, 1e-4f)
        game.dispose()
    }

    private companion object {
        const val FRAMES = 30
        const val FRAME_SECONDS = 1f / 60f
    }
}

/** Runs a scene's systems with no GPU: the scene renderer gets a preparer that draws nothing. */
internal class DrawlessRenderer : NoopRenderer(), GpuDrawPreparationSource {
    override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
}
