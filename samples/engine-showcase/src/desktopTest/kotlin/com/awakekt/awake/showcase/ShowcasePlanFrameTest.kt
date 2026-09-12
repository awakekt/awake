/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.bootstrap.dsl.appSpec
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.scene.runtime.attachRenderableComponents
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.terrain.TerrainExampleAsset
import com.awakekt.awake.vulkan.application.VulkanEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import com.awakekt.awake.scene.rendering.light.SceneLight as SceneDocumentLight

class ShowcasePlanFrameTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun theTerrainIsLitWhenRenderedThroughTheAppsOwnPlan() = runBlocking {
        val engine = HeadlessPlanEngine(headlessLifecycle(), EngineShowcaseRenderPlan)
        // `boot` bypasses `GraphicsEngine.setupCommon`, which is what normally assigns the
        // engine's own `renderer` field -- so teardown goes through the renderer this returns
        // rather than through `destroyBackend`, which would read that unset field.
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT))
        val target = renderer.createRenderTarget(WIDTH, HEIGHT)
        try {
            val scene = showcaseScene()
            val camera = scene.camera
            val light = scene.light
            val mesh = renderer.createMesh(TerrainExampleAsset.geometry)
            val material = renderer.createMaterial(uniformFloatCount = LitShadowUniformLayout.total)
            renderer.renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = camera,
                    drawCalls = listOf(RenderDrawCommand(mesh, material)),
                    light = light.copy(
                        cascades = shadowCascadeUniforms(
                            light,
                            camera,
                            renderer.surfaceAspect,
                            renderer.clipSpace,
                        ),
                    ),
                    clipSpace = renderer.clipSpace,
                    aspect = renderer.surfaceAspect,
                    drawPreparer = requireNotNull((renderer as? GpuDrawPreparationSource)?.gpuDrawPreparer),
                ),
            )
            val pixels = runBlocking { renderer.readPixels(target) }.data

            val greens = TERRAIN_SAMPLES.map { (x, y) -> pixels[(y * WIDTH + x) * 4 + 1].toInt() and 0xFF }
            mesh.destroy()
            material.destroy()
            assertTrue(
                greens.all { it > MIN_LIT_GREEN },
                "The terrain reads $greens through the app's own plan. Ambient alone is about " +
                    "$AMBIENT_GREEN here, so this is the terrain receiving no direct light -- " +
                    "which is what it looks like on screen.",
            )
        } finally {
            target.destroy()
            renderer.destroy()
        }
    }

    /**
     * The same scene again, through the path the app actually presents with.
     *
     * The test above renders with `renderToTexture`; a window renders with `Renderer.draw`. Those
     * are two recordings, and they have diverged before -- the offscreen one once drew no content
     * features at all. So this draws the frame the way the app does, into a headless stand-in for
     * a swapchain image, and reads that image back.
     */
    @Test
    fun theTerrainIsLitThroughTheOnScreenPathToo() = runBlocking {
        val engine = HeadlessPlanEngine(headlessLifecycle(), EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT))
        try {
            val scene = showcaseScene()
            val mesh = renderer.createMesh(TerrainExampleAsset.geometry)
            val material = renderer.createMaterial(uniformFloatCount = LitShadowUniformLayout.total)
            renderer.draw(
                ScenePassCompiler.compile(
                    lens = scene.camera,
                    drawCalls = listOf(RenderDrawCommand(mesh, material)),
                    light = scene.light.copy(
                        cascades = shadowCascadeUniforms(
                            scene.light,
                            scene.camera,
                            renderer.surfaceAspect,
                            renderer.clipSpace,
                        ),
                    ),
                    clipSpace = renderer.clipSpace,
                    aspect = renderer.surfaceAspect,
                    drawPreparer = requireNotNull((renderer as? GpuDrawPreparationSource)?.gpuDrawPreparer),
                ),
            )
            val pixels = renderer.readPresentedPixels().data

            val greens = TERRAIN_SAMPLES.map { (x, y) -> pixels[(y * WIDTH + x) * 4 + 1].toInt() and 0xFF }
            mesh.destroy()
            material.destroy()
            assertTrue(
                greens.all { it > MIN_LIT_GREEN },
                "The terrain reads $greens through the path the app presents with, against " +
                    "about $AMBIENT_GREEN for ambient alone. The offscreen render of this same " +
                    "scene is lit, so the two recordings disagree.",
            )
        } finally {
            renderer.destroy()
        }
    }

    /**
     * The whole way the app renders: the scene instantiated into a world, and `RenderSystem3D`
     * driving the frame.
     *
     * The two tests above hand the renderer a camera, a light and one draw call. The running app
     * hands it none of those directly -- `RenderSystem3D` builds all three from ECS components,
     * fits the cascades, reads the debug settings and calls `draw` itself. That layer is the last
     * one between a scene file and a pixel, and it is where a terrain can end up lit by nothing
     * while every other test renders it correctly.
     *
     * It also does the two things a single direct render cannot: it attaches the terrain's static
     * physics body, the way this showcase's own driver does, and it renders ten frames rather
     * than one -- a frame that is right once and wrong afterwards is still wrong on screen.
     *
     * Measured green about 125 here too, the same as both direct paths.
     */
    @Test
    fun theTerrainIsLitThroughRenderSystemToo() = runBlocking {
        val engine = HeadlessPlanEngine(headlessLifecycle(), EngineShowcaseRenderPlan)
        val renderer = engine.boot(HeadlessSurface(WIDTH, HEIGHT))
        try {
            val scene = SceneLoader.instantiate(SceneLoader.loadFromResource(SCENE))
            val terrain = renderer.createMesh(TerrainExampleAsset.geometry)
            val cube = renderer.createMesh(generate { cube(size = 1f, colored = true) })
            val material = renderer.createMaterial(uniformFloatCount = LitShadowUniformLayout.total)
            scene.attachRenderableComponents { request ->
                MeshRenderer(if (request.meshRenderer.mesh == TERRAIN_MESH) terrain else cube, material)
            }

            // What the showcase's own driver attaches, and nothing else does: a static body for
            // the terrain, whose transform PhysicsSystem then writes back every step.
            val physics = createJoltPhysicsWorld()
            scene.roots.find { it.name == TERRAIN_MESH }?.let { node ->
                scene.world.add(node.entity, PhysicsBody(TerrainExampleAsset.collisionShape, MotionType.STATIC))
            }
            PhysicsSystem(physics).update(scene.world, FRAME_DELTA)

            val renderSystem = RenderSystem3D(renderer)
            repeat(10) { renderSystem.update(scene.world, FRAME_DELTA) }
            val pixels = renderer.readPresentedPixels().data

            val greens = TERRAIN_SAMPLES.map { (x, y) -> pixels[(y * WIDTH + x) * 4 + 1].toInt() and 0xFF }
            terrain.destroy()
            cube.destroy()
            material.destroy()
            assertTrue(
                greens.all { it > MIN_LIT_GREEN },
                "The terrain reads $greens with RenderSystem3D driving the frame, against about " +
                    "$AMBIENT_GREEN for ambient alone -- while the same scene rendered directly " +
                    "is lit. The difference is this system: the light it builds, the cascades it " +
                    "fits, or what it culls.",
            )
        } finally {
            renderer.destroy()
        }
    }

    /** The showcase scene's own camera and sun, read from the file it ships. */
    private suspend fun showcaseScene(): ShowcaseScene {
        val document = SceneLoader.loadFromResource(SCENE)
        val sceneCamera = document.nodes.firstNotNullOf { node ->
            node.components.filterIsInstance<SceneCamera>().firstOrNull()
        }
        val sceneSun = document.nodes.firstNotNullOf { node ->
            node.components.filterIsInstance<SceneDocumentLight>().firstOrNull()
        }
        return ShowcaseScene(
            camera = Lens.perspective(
                eye = Vec3f(sceneCamera.eye.x, sceneCamera.eye.y, sceneCamera.eye.z),
                center = Vec3f(sceneCamera.center.x, sceneCamera.center.y, sceneCamera.center.z),
                fovYDegrees = sceneCamera.fovYDegrees,
                near = sceneCamera.near,
                far = sceneCamera.far,
            ),
            light = SceneLight(
                direction = Vec3f(sceneSun.direction.x, sceneSun.direction.y, sceneSun.direction.z),
                color = Vec3f(sceneSun.color.r, sceneSun.color.g, sceneSun.color.b),
            ),
        )
    }

    private data class ShowcaseScene(val camera: Lens, val light: SceneLight)

    /** A lifecycle with no scene: this test drives the renderer directly, not the app's frame loop. */
    private fun headlessLifecycle(): AwakeAppLifecycle = appSpec {
        window {
            title = "headless-plan"
            size(WIDTH, HEIGHT)
        }
    }.createLifecycle()

    /** Exposes the protected half of the engine's own bootstrap, and nothing else. */
    private class HeadlessPlanEngine(
        lifecycle: AwakeAppLifecycle,
        plan: RenderPlan,
    ) : VulkanEngine(lifecycle, plan) {
        suspend fun boot(surface: HeadlessSurface): Renderer = createBackendResources(surface).renderer
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 180
        const val SCENE = "assets/examples/heightfield-terrain.scene.json"
        const val TERRAIN_MESH = "heightfield-terrain"
        const val FRAME_DELTA = 1f / 60f

        /** Points on the terrain, away from its silhouette. */
        val TERRAIN_SAMPLES = listOf(160 to 120, 140 to 130, 180 to 125)

        /** 0.08 ambient on this terrain's green, tone-mapped and gamma-encoded. */
        const val AMBIENT_GREEN = 43
        const val MIN_LIT_GREEN = 70
    }
}
