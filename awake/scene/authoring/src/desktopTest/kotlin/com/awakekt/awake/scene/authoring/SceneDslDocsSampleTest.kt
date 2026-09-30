/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.authoring.dsl.EntityScope
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.SpinSystem
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// --8<-- [start:own-helper]
fun EntityScope.spin(speed: Float) = configure(::SpinControl) {
    this.speed = speed
}
// --8<-- [end:own-helper]

// --8<-- [start:beacon-spin]
class BeaconSpin : System {
    override fun update(world: World, delta: Float) {
        world.queryEach<SpinControl> { _, spin -> spin.radians += spin.speed * delta }
    }
}
// --8<-- [end:beacon-spin]

/**
 * The "Scene DSL" and "Scene documents" guides show Harbor Town in both forms. Their samples are
 * included from here, so this test keeps them compiling and building equal components.
 */
class SceneDslDocsSampleTest {

    private val json = File(DOCS_SNIPPETS, "scene/harbor-town.scene.json").readText()

    @Test
    fun theSceneDslAndTheSceneDocumentBuildTheSameHarborTown() {
        val world = World()
        // --8<-- [start:world-scene]
        world.scene {
            entity("dock") {
                transform(z = 4f, sx = 6f, sy = 0.2f, sz = 2f)
            }
            entity("lighthouse") {
                transform(x = 3f, z = -2f)
                entity("beacon") {
                    transform(y = 6f)
                    with(SpinControl().apply { speed = 0.5f })
                }
            }
        }
        // --8<-- [end:world-scene]

        DefaultSceneComponentResolvers.install()
        val loaded = SceneLoader.decode(json).instantiate().world

        for (name in listOf("dock", "lighthouse", "beacon")) {
            val fromDsl = world.transformOf(name)
            val fromDocument = loaded.transformOf(name)
            assertEquals(fromDocument.position, fromDsl.position, name)
            assertEquals(fromDocument.rotation, fromDsl.rotation, name)
            assertEquals(fromDocument.scale, fromDsl.scale, name)
            assertEquals(loaded.nameOf(fromDocument.parent), world.nameOf(fromDsl.parent), name)
        }
        assertEquals(loaded.spinOf("beacon").speed, world.spinOf("beacon").speed)
        assertEquals(loaded.spinOf("beacon").radians, world.spinOf("beacon").radians)

        // Saving the DSL world gives back the scene document.
        assertEquals(SceneLoader.decode(json), SceneLoader.fromWorld(world, name = "Harbor Town"))
    }

    @Test
    fun aHelperWrittenWithConfigureAttachesOrUpdatesOneComponent() {
        val world = World()
        world.scene {
            entity("beacon") {
                spin(speed = 0.5f)
                spin(speed = 2f)
            }
        }
        assertEquals(1, world.query(SpinControl::class).size)
        assertEquals(2f, world.spinOf("beacon").speed)
    }

    @Test
    fun aRootEntityWithoutTransformHasNoneAndIsNotSaved() {
        val world = World()
        world.scene {
            entity("marker")
            entity("parent") { entity("child") }
        }
        assertNull(world.get<Transform>(world.entityNamed("marker")))
        assertNull(world.get<Transform>(world.entityNamed("parent")))
        // A child without transform() does get a Transform, but SceneBuilder.entity drops the parent
        // link: it assigns through World.add's return value, which is the previous component (null).
        val child = world.get<Transform>(world.entityNamed("child"))
        assertNotNull(child)
        assertNull(child.parent)
        assertEquals(listOf("child"), SceneLoader.fromWorld(world).nodes.map { it.name })

        val flat = World().apply { scene { entity("marker") } }
        assertTrue(SceneLoader.fromWorld(flat).nodes.isEmpty(), "entities without a Transform are not saved")
    }

    @Test
    fun anAppBuildsTheSceneWhenTheRendererIsReady() = runTest {
        // --8<-- [start:app-scene]
        val game = app {
            scene("harbor-town") {
                entity("lighthouse") {
                    transform(x = 3f, z = -2f)
                    entity("beacon") {
                        transform(y = 6f)
                        spin(speed = 0.5f)
                    }
                }
                frameSystem("beacon-spin") { BeaconSpin() } // advances the angle
                frameSystem("spin") { SpinSystem() } // writes it to Transform.rotation.y
            }
        }
        // --8<-- [end:app-scene]

        game.ready(RecordingRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val beacon = runtime.world.spinOf("beacon")
        val before = beacon.radians
        game.update(0.5f, 320f, 240f)

        assertEquals("harbor-town", runtime.sceneName)
        assertEquals(before + 0.25f, beacon.radians, 1e-5f)
        assertEquals(beacon.radians, runtime.requireTransform("beacon").rotation.y)
        game.dispose()
    }

    @Test
    fun anAppCanRunASceneDocument() = runTest {
        // --8<-- [start:app-document]
        val game = app {
            ecs {
                scene(SceneLoader.decode(json))
                frameSystem("beacon-spin") { BeaconSpin() }
                frameSystem("spin") { SpinSystem() }
            }
        }
        // --8<-- [end:app-document]

        game.ready(RecordingRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        assertEquals("Harbor Town", runtime.sceneName)
        assertEquals(0.5f, runtime.world.spinOf("beacon").speed)
        game.dispose()
    }

    private fun World.entityNamed(name: String) = query(Name::class).single { get<Name>(it)?.value == name }

    private fun World.transformOf(name: String): Transform = get<Transform>(entityNamed(name))!!

    private fun World.spinOf(name: String): SpinControl = get<SpinControl>(entityNamed(name))!!

    private fun World.nameOf(entity: com.awakekt.awake.ecs.Entity?): String? = entity?.let { get<Name>(it)?.value }

    private companion object {
        /** Tests run from the module directory; the snippets live with the docs. */
        val DOCS_SNIPPETS = File("../../../website/docs/snippets")
    }
}
