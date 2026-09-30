/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.docs.loaddocument

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.Light
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.showcase.docs.DrawlessRenderer
import com.awakekt.awake.showcase.docs.GameRenderPlan
import com.awakekt.awake.showcase.docs.firstScene
import com.awakekt.awake.showcase.docs.firstwindow.renderHeadless
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import com.awakekt.awake.showcase.docs.Turntable
import com.awakekt.awake.vulkan.application.runVulkanDesktopGame
// --8<-- [start:imports]
import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.attachRenderableComponents
// --8<-- [end:imports]
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.Transform

// --8<-- [start:app]
fun firstDocumentScene(): AwakeAppLifecycle = app {
    window {
        title = "Hello AwakeKt"
        size(1280, 720)
    }
    scene("first-scene") {
        assets {
            mesh("cube") { renderer.createMesh(generate { cube(size = 1f, colored = true) }) }
            material("lit") { renderer.createMaterial(LitShadowUniformLayout) }
        }
        frameSystem("turntable") { Turntable() }
        onReady {
            val document = SceneLoader.loadFromResource("scenes/first.scene.json")
            val scene = sceneManager.switchTo(document)
            // Each mesh_renderer names a mesh and a material; build them from assets { }.
            scene.attachRenderableComponents { request -> requireAssetLibrary().resolve(this, request) }
        }
    }
}
// --8<-- [end:app]

// --8<-- [start:main]
fun main() = runVulkanDesktopGame(firstDocumentScene(), GameRenderPlan)
// --8<-- [end:main]

/**
 * The "Load a scene document" tutorial runs the same scene as "Your first scene", from a scene
 * document. This loads the page's snippet as a resource and checks both forms build, and draw, the
 * same scene.
 */
class LoadSceneDocumentDocsSampleTest {
    private val originalLoader = Thread.currentThread().contextClassLoader
    private val resources = Files.createTempDirectory("get-started").toFile()

    @BeforeTest
    fun putTheSnippetOnTheResourcePath() {
        File(DOCS_SNIPPETS, "get-started/first.scene.json").copyTo(File(resources, "scenes/first.scene.json"))
        Thread.currentThread().contextClassLoader = URLClassLoader(arrayOf(resources.toURI().toURL()), originalLoader)
    }

    @AfterTest
    fun restore() {
        Thread.currentThread().contextClassLoader = originalLoader
        resources.deleteRecursively()
    }

    @Test
    fun theDocumentBuildsTheSameEntitiesAsTheSceneDsl() = runBlocking {
        val fromDsl = firstScene().readyRuntime()
        val fromDocument = firstDocumentScene().readyRuntime()

        val dslCamera = fromDsl.component<Camera>("camera").lens
        val documentCamera = fromDocument.component<Camera>("camera").lens
        assertEquals(dslCamera.eye, documentCamera.eye)
        assertEquals(dslCamera.center, documentCamera.center)
        assertEquals(dslCamera.fovYRadians, documentCamera.fovYRadians, 1e-6f)
        assertEquals(dslCamera.near, documentCamera.near)
        assertEquals(dslCamera.far, documentCamera.far)
        assertEquals(fromDsl.component<Light>("sun"), fromDocument.component<Light>("sun"))
        assertEquals(fromDsl.component<Transform>("cube").position, fromDocument.component<Transform>("cube").position)
        assertEquals(fromDsl.component<SpinControl>("cube").speed, fromDocument.component<SpinControl>("cube").speed)
        assertSame(fromDocument.requireMesh("cube"), fromDocument.component<MeshRenderer>("cube").mesh)
        assertSame(fromDocument.requireMaterial("lit"), fromDocument.component<MeshRenderer>("cube").material)
    }

    @Test
    fun theDocumentDrawsTheSameFramesAsTheSceneDsl() = runBlocking {
        val fromDsl = renderHeadless(firstScene(), GameRenderPlan, frames = FRAMES)
        val fromDocument = renderHeadless(firstDocumentScene(), GameRenderPlan, frames = FRAMES)

        assertContentEquals(fromDsl, fromDocument)
    }

    private suspend fun AwakeAppLifecycle.readyRuntime(): SceneAppLifecycleRuntime {
        ready(DrawlessRenderer())
        return requireService<SceneAppLifecycleRuntime>()
    }

    private inline fun <reified T : Any> SceneAppLifecycleRuntime.component(entity: String): T {
        val handle: Entity = assertNotNull(findEntity(entity), "No entity named $entity")
        return assertNotNull(world.get<T>(handle), "$entity has no ${T::class.simpleName}")
    }

    private companion object {
        const val FRAMES = 20

        /** Tests run from the module directory; the snippets live with the docs. */
        val DOCS_SNIPPETS = File("../../website/docs/snippets")
    }
}
