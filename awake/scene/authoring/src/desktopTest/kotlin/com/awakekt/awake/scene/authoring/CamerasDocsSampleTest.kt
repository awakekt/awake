/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.authoring.dsl.camera
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.authoring.infrastructure.cameraSystem
import com.awakekt.awake.scene.authoring.infrastructure.matrixRelativeMovementSystem
import com.awakekt.awake.scene.authoring.infrastructure.playerInputSystem
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.camera.CameraRigBinding
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementControlBinding
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The "Cameras and controls" guide shows a follow camera as a scene document and in the scene DSL.
 * Both are included from here, so this test keeps them loading and building the same components.
 */
class CamerasDocsSampleTest {

    private val json = File(DOCS_SNIPPETS, "scene/follow-camera.scene.json").readText()

    @Test
    fun theSceneDocumentAndTheSceneDslBuildTheSameFollowCamera() {
        val world = World()
        // --8<-- [start:follow-camera-dsl]
        world.scene {
            val player = entity("player") {
                transform(y = 0.5f)
                with(MovementControl().apply { speed = 6f })
            }
            entity("camera") {
                transform()
                camera(
                    mode = CameraMode.ThirdPerson,
                    target = player,
                    lens = Lens.perspective(
                        eye = Vec3f(0f, 4f, 8f),
                        center = Vec3f(0f, 1f, 0f),
                        fovYDegrees = 60f,
                        far = 500f,
                    ),
                ) {
                    distance = 8f
                    pitch = -0.3f
                    offsetPosition.set(0f, 1.5f, 0f)
                    needsReset = false // keep the pitch above; see the warning below
                }
            }
        }
        // --8<-- [end:follow-camera-dsl]

        // --8<-- [start:load-controls]
        DefaultSceneComponentResolvers.install()
        val registry = SceneComponentRegistry().registerControls()
        val document = SceneLoader.decode(json)
        val loaded = SceneLoader.instantiate(document, World(), registry).world
        // --8<-- [end:load-controls]

        assertSameCamera(loaded, world)
        assertEquals(6f, loaded.single<MovementControl>().speed)
        assertEquals(loaded.single<MovementControl>().speed, world.single<MovementControl>().speed)
        assertEquals(0.5f, world.transformOf("player").position.y)
        assertEquals(loaded.transformOf("player").position, world.transformOf("player").position)
    }

    @Test
    fun aDslCameraRigThatChangesModeResetsItsAnglesUnlessToldNotTo() {
        val world = World()
        world.scene {
            entity("camera") { camera(mode = CameraMode.ThirdPerson) { pitch = -0.3f } }
        }
        assertTrue(world.single<CameraRig>().needsReset, "setting a new mode schedules a reset")
    }

    @Test
    fun anAppRunsTheControlSystemsOverALoadedDocument() = runTest {
        // --8<-- [start:app-controls]
        DefaultSceneComponentResolvers.install()
        SceneComponentRegistry.registerGlobal(MovementControlBinding)
        SceneComponentRegistry.registerGlobal(CameraRigBinding)

        val game = app {
            ecs {
                scene(SceneLoader.decode(json))
                playerInputSystem() // keys to MovementControl
                matrixRelativeMovementSystem() // MovementControl to Transform, camera-relative
                cameraSystem() // CameraRig to Camera, on the entity tagged ActiveCamera
                onReady { world.add(requireEntity("camera"), ActiveCamera()) }
            }
        }
        // --8<-- [end:app-controls]

        game.ready(RecordingRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val input = game.requireService<Input>()
        val player = runtime.requireTransform("player")
        val eye = runtime.requireCamera("camera").lens.eye.copy()

        input.setKeyDown(Key.W, true)
        input.updateSnapshot()
        game.update(0.1f, 320f, 240f)

        assertNotEquals(0f, player.position.z, "W moved the player")
        assertNotEquals(eye, runtime.requireCamera("camera").lens.eye, "the rig moved the camera")
        game.dispose()
    }

    private fun assertSameCamera(expected: World, actual: World) {
        val a = expected.single<Camera>()
        val b = actual.single<Camera>()
        assertEquals(a.isPrimary, b.isPrimary)
        assertEquals(a.lens.eye, b.lens.eye)
        assertEquals(a.lens.center, b.lens.center)
        assertEquals(a.lens.up, b.lens.up)
        assertEquals(a.lens.fovYRadians, b.lens.fovYRadians, 1e-6f)
        assertEquals(a.lens.near, b.lens.near)
        assertEquals(a.lens.far, b.lens.far)

        val rigA = expected.single<CameraRig>()
        val rigB = actual.single<CameraRig>()
        assertEquals(rigA.mode, rigB.mode)
        assertEquals("player", expected.get<Name>(rigA.targetEntity!!)?.value)
        assertEquals("player", actual.get<Name>(rigB.targetEntity!!)?.value)
        assertEquals(rigA.distance, rigB.distance)
        assertEquals(rigA.minDistance, rigB.minDistance)
        assertEquals(rigA.maxDistance, rigB.maxDistance)
        assertEquals(rigA.pitch, rigB.pitch)
        assertEquals(rigA.yaw, rigB.yaw)
        assertEquals(rigA.offsetPosition, rigB.offsetPosition)
        assertEquals(rigA.flySpeed, rigB.flySpeed)
        assertEquals(rigA.needsReset, rigB.needsReset)
    }

    private inline fun <reified T : Any> World.single(): T = query(T::class).single().let { get<T>(it)!! }

    private fun World.transformOf(name: String): Transform =
        get<Transform>(query(Name::class).single { get<Name>(it)?.value == name })!!

    private companion object {
        /** Tests run from the module directory; the snippets live with the docs. */
        val DOCS_SNIPPETS = File("../../../website/docs/snippets")
    }
}
