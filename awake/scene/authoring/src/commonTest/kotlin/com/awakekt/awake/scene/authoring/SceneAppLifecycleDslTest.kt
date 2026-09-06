/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.appModule
import com.awakekt.awake.engine.bootstrap.dsl.module
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.DrawCall
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SceneLight
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.dsl.camera
import com.awakekt.awake.scene.authoring.dsl.cameraEntity
import com.awakekt.awake.scene.authoring.dsl.meshEntity
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.authoring.infrastructure.cameraSystem
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.runtime.LocalFrameStats
import com.awakekt.awake.scene.runtime.LocalRenderer
import com.awakekt.awake.scene.runtime.LocalWorld
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.session.SceneSession
import com.awakekt.awake.scene.runtime.ui.sceneComposeAppModule
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SceneAppLifecycleDslTest {

    @Test
    fun sceneAppProvidesTypedSystemsAndCachedAssets() = runTest {
        val recordingRenderer = RecordingRenderer()
        lateinit var tickSystem: com.awakekt.awake.scene.runtime.SceneSystemHandle<RecordingSystem>
        val game = app {
            ecs {
                name("runtime-proof")
                scene {
                    entity("camera") { camera() }
                    entity("cube") { transform() }
                }
                assets {
                    mesh("cube") { recordingRenderer.createMesh(EmptyGeometry) }
                    material("default") { recordingRenderer.createMaterial() }
                }
                tickSystem = fixedSystem("tick") {
                    RecordingSystem()
                }
            }
        }

        game.ready(recordingRenderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val session = game.requireService<SceneSession>()
        val mesh = runtime.requireMesh("cube")
        val material = runtime.requireMaterial("default")
        val system = runtime.system(tickSystem)

        runtime.update(tickSystem, 0.25f)
        runtime.update(tickSystem, 0.5f)

        assertSame(mesh, runtime.requireMesh("cube"))
        assertSame(material, runtime.requireMaterial("default"))
        assertEquals(1, recordingRenderer.meshCreateCount)
        assertEquals(1, recordingRenderer.materialCreateCount)
        assertEquals(2, system.calls)
        assertEquals(0.75f, system.accumulatedDelta)
        assertNotNull(runtime.findEntity("cube"))
        assertSame(runtime.session, session)
        assertSame(runtime.world, session.world)

        game.dispose()

        assertEquals(1, recordingRenderer.meshDestroyCount)
        assertEquals(1, recordingRenderer.materialDestroyCount)
    }

    @Test
    fun sceneBlockCanStillReplaceDirectlyAuthoredDocument() = runTest {
        val game = app {
            ecs {
                name("before-replace")
                entity("ignored")
                scene("after-replace") {
                    entity("camera") { camera() }
                }
            }
        }

        game.ready(RecordingRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        assertEquals("after-replace", runtime.sceneName)
        assertEquals(null, runtime.findEntity("ignored"))
        assertNotNull(runtime.findEntity("camera"))
    }

    @Test
    fun gameSceneFacadeBuildsNamedCameraAndMeshEntities() = runTest {
        val recordingRenderer = RecordingRenderer()
        val game = app {
            scene("facade-proof") {
                cameraEntity("camera") { transform(y = 1f, z = 5f) }
                meshEntity(
                    "cube",
                    recordingRenderer.createMesh(EmptyGeometry),
                    recordingRenderer.createMaterial(),
                ) {
                    transform(sx = 2f, sy = 2f, sz = 2f)
                }
                assets {
                    mesh("cube") { recordingRenderer.createMesh(EmptyGeometry) }
                    material("default") { recordingRenderer.createMaterial() }
                }
                cameraSystem()
            }
        }

        game.ready(recordingRenderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        assertEquals("facade-proof", runtime.sceneName)
        assertNotNull(runtime.findEntity("camera"))
        assertNotNull(runtime.findEntity("cube"))
        assertNotNull(runtime.findTransform("cube"))
        assertNotNull(runtime.findCamera("camera"))
        assertTrue(runtime.system("camera") is CameraSystem)
    }

    @Test
    fun gameModuleCanOwnSceneAndUiComposition() = runTest {
        val renderer = RecordingRenderer()
        val module = appModule {
            scene("module-scene") {
                cameraEntity("camera")
                meshEntity("cube", renderer.createMesh(EmptyGeometry), renderer.createMaterial())
                assets {
                    mesh("cube") { renderer.createMesh(EmptyGeometry) }
                    material("default") { renderer.createMaterial() }
                }
                content {
                    Text("module-scene")
                }
            }
        }

        val game = app {
            module(module)
        }

        game.ready(renderer)
        game.update(0.016f, 320f, 240f)

        assertEquals("module-scene", game.requireService<SceneAppLifecycleRuntime>().sceneName)
        assertTrue(renderer.lastUiPrimitives.any { primitive -> primitive is UiDrawPrimitive.Glyph })
    }

    @Test
    fun sceneRuntimeStagesUiBeforeInfrastructureUpdate() = runTest {
        val renderer = RecordingRenderer()
        val game = app {
            scene("ordered-scene") {
                cameraEntity("camera")
                content {
                    Text("ordered")
                }
            }
        }

        game.ready(renderer)
        renderer.frameCalls.clear()
        game.update(0.016f, 320f, 240f)

        assertEquals(listOf("drawUi", "draw"), renderer.frameCalls)
    }

    @Test
    fun appLevelComposeHostStagesUiBeforeScenePresentation() = runTest {
        val renderer = RecordingRenderer()
        val game = app {
            module(
                sceneComposeAppModule(content = { Text("app-level") }),
            )
            sceneSession {
                cameraEntity("camera")
            }
        }

        game.ready(renderer)
        renderer.frameCalls.clear()
        game.update(0.016f, 320f, 240f)

        assertEquals(listOf("drawUi", "draw"), renderer.frameCalls)
        assertTrue(renderer.lastUiPrimitives.any { it is UiDrawPrimitive.Glyph })
    }

    @Test
    fun explicitSystemPhasesSeparateFixedStepsFromRenderedFrames() = runTest {
        lateinit var fixedHandle: com.awakekt.awake.scene.runtime.SceneSystemHandle<RecordingSystem>
        lateinit var frameHandle: com.awakekt.awake.scene.runtime.SceneSystemHandle<RecordingSystem>
        val game = app {
            scene("phase-proof") {
                cameraEntity("camera")
                fixedHandle = fixedSystem("fixed") { RecordingSystem() }
                frameHandle = frameSystem("frame") { RecordingSystem() }
            }
        }

        game.ready(RecordingRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val fixedSystem = runtime.system(fixedHandle)
        val frameSystem = runtime.system(frameHandle)
        fixedSystem.reset()
        frameSystem.reset()

        game.update(0.001f, 320f, 240f)

        assertEquals(0, fixedSystem.calls)
        assertEquals(1, frameSystem.calls)

        game.update(1f / 60f, 320f, 240f)

        assertEquals(1, fixedSystem.calls)
        assertEquals(2, frameSystem.calls)
    }

    @Test
    fun anInterpolatedFixedSystemIsToldHowFarPastTheLastStepEachFrameIs() = runTest {
        lateinit var handle: com.awakekt.awake.scene.runtime.SceneSystemHandle<InterpolatingSystem>
        val game = app {
            scene("interpolation") {
                cameraEntity("camera")
                handle = fixedSystem("interpolated") { InterpolatingSystem() }
            }
        }

        game.ready(RecordingRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val system = runtime.system(handle)
        system.reset()

        // Three quarters of a step: too little to run one, which is exactly the frame that used to
        // redraw the previous pose unchanged.
        game.update(0.75f / 60f, 320f, 240f)

        assertEquals(0, system.steps, "a partial step should not have advanced the simulation")
        assertEquals(listOf(0.75f), system.alphas.map { round(it) })

        // Another three quarters: one step runs and half a step is left over.
        game.update(0.75f / 60f, 320f, 240f)

        assertEquals(1, system.steps)
        assertEquals(listOf(0.75f, 0.5f), system.alphas.map { round(it) })
    }

    @Test
    fun sceneSchedulePreservesFixedFrameAndInfrastructureOrder() = runTest {
        val trace = mutableListOf<String>()
        val game = app {
            scene("schedule-order") {
                fixedSystem("fixed") { TraceSystem("fixed", trace) }
                frameSystem("frame") { TraceSystem("frame", trace) }
                update { _, _ -> trace += "update" }
                infrastructureSystems {
                    listOf(TraceSystem("infrastructure", trace))
                }
            }
        }

        game.ready(RecordingRenderer())
        trace.clear()
        game.update(1f / 60f, 320f, 240f)

        assertEquals(listOf("fixed", "update", "frame", "infrastructure"), trace)
    }

    @Test
    fun appLevelComposeHostBlocksGameplayPointerCaptureOnInteract() = runTest {
        val renderer = RecordingRenderer()
        var clicked = false
        val game = app {
            module(
                sceneComposeAppModule(
                    content = {
                        Spacer(
                            Modifier.size(100.dp).testTag("click-box").clickable { clicked = true },
                        )
                    },
                ),
            )
            sceneSession {
                cameraEntity("camera")
                cameraSystem()
            }
        }

        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val input = game.requireService<Input>()

        // Initial frame: pointer up at (50, 50)
        input.setPointer(down = false, x = 50f, y = 50f)
        input.updateSnapshot()
        game.update(1f / 60f, 320f, 240f)
        assertEquals(false, runtime.uiOwnership.isCaptured)

        // Press frame at (50, 50): UI captures pointer, blocks gameplay camera
        input.setPointer(down = true, x = 50f, y = 50f)
        input.updateSnapshot()
        game.update(1f / 60f, 320f, 240f)
        assertEquals(true, runtime.uiOwnership.isCaptured)
        assertTrue(runtime.uiSemantics.isNotEmpty())

        // Release frame: triggers click
        input.setPointer(down = false, x = 50f, y = 50f)
        input.updateSnapshot()
        game.update(1f / 60f, 320f, 240f)
        assertTrue(clicked)
    }

    @Test
    fun appLevelComposeHostProvidesSceneLocalsAndFrameStats() = runTest {
        val renderer = RecordingRenderer()
        var seenWorld: World? = null
        var seenRenderer: Renderer? = null
        var seenFps: Float? = null

        val game = app {
            module(
                sceneComposeAppModule(
                    content = {
                        seenWorld = LocalWorld.current
                        seenRenderer = LocalRenderer.current
                        seenFps = LocalFrameStats.current.fps
                    },
                ),
            )
            sceneSession {
                cameraEntity("camera")
            }
        }

        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val session = game.requireService<SceneSession>()

        game.update(1f / 60f, 320f, 240f)

        assertSame(session.world, seenWorld)
        assertSame(renderer, seenRenderer)
        assertNotNull(seenFps)
    }

    @Test
    fun declaringBothAppLevelComposeAndLegacySceneContentFails() = runTest {
        assertFailsWith<IllegalStateException> {
            app {
                module(sceneComposeAppModule(content = { Text("app") }))
                scene("legacy-ui") {
                    content { Text("legacy") }
                }
            }
        }
    }

    @Test
    fun declaringAppLevelComposeAfterLegacySceneContentFails() = runTest {
        assertFailsWith<IllegalStateException> {
            app {
                scene("legacy-ui") {
                    content { Text("legacy") }
                }
                module(sceneComposeAppModule(content = { Text("app") }))
            }
        }
    }
}

private class RecordingSystem : System {
    var calls = 0
    var accumulatedDelta = 0f

    override fun update(world: World, delta: Float) {
        calls += 1
        accumulatedDelta += delta
    }

    fun reset() {
        calls = 0
        accumulatedDelta = 0f
    }
}

private class TraceSystem(
    private val name: String,
    private val trace: MutableList<String>,
) : System {
    override fun update(world: World, delta: Float) {
        trace += name
    }
}

private val EmptyGeometry = MeshGeometry(vertices = floatArrayOf(), indices = intArrayOf())

internal class RecordingRenderer : Renderer {
    var meshCreateCount = 0
    var materialCreateCount = 0
    var meshDestroyCount = 0
    var materialDestroyCount = 0
    var lastUiPrimitives: List<UiDrawPrimitive> = emptyList()
    val frameCalls = mutableListOf<String>()

    override val clipSpace: ClipSpace = ClipSpace.WebGpu
    override var clearColor: Color = Color.Black
    override var wireframe: Boolean = false
    override var shadowsEnabled: Boolean = true

    override fun createMesh(geometry: MeshGeometry): Mesh {
        meshCreateCount += 1
        return object : Mesh {
            override val format: VertexFormat = geometry.format
            override val sizeBytes: Long = 0

            override fun destroy() {
                meshDestroyCount += 1
            }
        }
    }

    override fun createMaterial(
        texture: TextureAsset?,
        renderTarget: RenderTarget?,
        uniformFloatCount: Int,
        pbrTextures: PbrTextureSet?,
    ): Material {
        materialCreateCount += 1
        return object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit

            override fun destroy() {
                materialDestroyCount += 1
            }
        }
    }

    override fun createRenderTarget(width: Int, height: Int): RenderTarget = object : RenderTarget {
        override val width: Int = width
        override val height: Int = height
        override fun destroy() = Unit
    }

    override fun draw(camera: Lens, drawCalls: List<DrawCall>, light: SceneLight) {
        frameCalls += "draw"
    }

    override fun renderToTexture(
        target: RenderTarget,
        camera: Lens,
        drawCalls: List<DrawCall>,
        light: SceneLight,
    ) =
        Unit

    override suspend fun readPixels(target: RenderTarget): TextureAsset =
        TextureAsset(ByteArray(target.width * target.height * 4), target.width, target.height)

    override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) {
        frameCalls += "drawUi"
        lastUiPrimitives = primitives
    }

    override fun drawDebugLines(lines: List<LineSegment>) = Unit

    override fun destroy() = Unit
}

/** Rounds to two places, so a float that is 0.7499999 reads as the 0.75 the arithmetic meant. */
private fun round(value: Float): Float = kotlin.math.round(value * 100f) / 100f

/** A fixed system that records the alpha it is drawn at, for the interpolation wiring. */
private class InterpolatingSystem : com.awakekt.awake.ecs.InterpolatedSystem {
    var steps = 0
    val alphas = mutableListOf<Float>()

    override fun update(world: World, delta: Float) {
        steps += 1
    }

    override fun interpolate(world: World, alpha: Float) {
        alphas += alpha
    }

    fun reset() {
        steps = 0
        alphas.clear()
    }
}
