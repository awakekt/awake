/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.sky.Skybox
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasView
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A View element's camera renders into a target the size of the element's box in pixels, before the
 * UI that samples it, and the target lives only as long as the element shows and its camera exists.
 */
class SceneCanvasViewsTest {
    /** What the runtime asked of the GPU, in order: frames, view renders and the targets each UI sampled. */
    private class RecordingRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        val calls = ArrayList<String>()
        val frames = ArrayList<List<Float>>()
        val renders = ArrayList<List<Float>>()
        val viewInputs = ArrayList<GpuPassInput>()
        val frameInputs = ArrayList<GpuPassInput>()
        val live = HashSet<RenderTarget>()
        private val targetOf = HashMap<Material, RenderTarget>()

        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }

        override fun createRenderTarget(width: Int, height: Int): RenderTarget = object : RenderTarget {
            override val width = width
            override val height = height
            override fun destroy() {
                live -= this
            }
        }.also { live += it }

        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material =
            super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures).also { if (renderTarget != null) targetOf[it] = renderTarget }

        override fun draw(input: GpuPassInput) {
            calls += "frame"
            frames += input.viewProjection.data.toList()
            frameInputs += input
        }

        override fun submitToTexture(target: RenderTarget, input: GpuPassInput) {
            check(target in live) { "rendered into a freed target" }
            calls += "view ${target.width}x${target.height}"
            renders += input.viewProjection.data.toList()
            viewInputs += input
        }

        override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) {
            val sampled = primitives.filterIsInstance<UiDrawPrimitive.Texture>().map { targetOf.getValue(it.material as Material) }
            check(sampled.all { it in live }) { "sampled a freed target" }
            calls += "ui ${sampled.map { "${it.width}x${it.height}" }}"
        }
    }

    private val renderer = RecordingRenderer()
    private val input = Input()
    private lateinit var portrait: CanvasElement
    private var portraitCamera: Entity? = null

    private val runtime = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = { populate() },
            renderableFactory = { error("no renderable requested in this test") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
        ),
    ).also {
        it.initialize(
            object : AppServiceLookup {
                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> service(type: KClass<T>): T? = if (type == Input::class) input as T else null
            },
        )
    }

    private fun SceneAppLifecycleRuntime.populate() {
        world.add(world.create(), Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)))
        portraitCamera = world.create().also {
            world.add(it, Name("Portrait Camera"))
            world.add(it, Camera(Lens(eye = Vec3f(50f, 1f, 3f), center = Vec3f(50f, 1f, 0f), fovYRadians = 0.5f, near = 0.1f, far = 100f), isPrimary = false))
        }
        portrait = CanvasElement().apply {
            kind = CanvasElementKind.View; camera = "Portrait Camera"; offsetX = 0f; offsetY = 0f; width = 100f; height = 50f
        }
        world.add(world.create(), portrait)
    }

    /** One frame at two pixels per dp, and what it asked for. */
    private fun frame(): List<String> {
        renderer.calls.clear()
        runtime.update(AppFrame(1f / 60f, 400f, 300f, input.snapshot(), density = 2f))
        return renderer.calls.toList()
    }

    @Test
    fun aViewsCameraRendersAtItsBoxsPixelSizeBeforeTheUiThatSamplesIt() = runTest {
        runtime.ready(renderer)

        val calls = frame()

        assertEquals(listOf("frame", "view 200x100", "ui [200x100]"), calls)
        assertTrue(renderer.renders.last() != renderer.frames.last(), "from its own camera, not the main one")
        val again = frame()
        assertEquals(calls, again, "the next frame renders into the same target")
        assertEquals(1, renderer.live.size)
    }

    @Test
    fun aResizedViewGetsANewTargetAndTheOldOneIsFreed() = runTest {
        runtime.ready(renderer)
        frame()

        portrait.width = 60f

        assertEquals(listOf("frame", "view 120x100", "ui [120x100]"), frame())
        assertEquals(listOf(120 to 100), renderer.live.map { it.width to it.height })
    }

    @Test
    fun transparencyCanToggleOnAReusedTargetWithoutChangingTheMainCamera() = runTest {
        runtime.ready(renderer)
        runtime.world.add(runtime.world.create(), Skybox())
        frame()
        val target = renderer.live.single()
        val opaque = renderer.viewInputs.last().environment
        assertTrue(opaque.showSky, "control: this scene has a sky")
        portrait.view = CanvasView(transparent = true)
        frame()
        val transparent = renderer.viewInputs.last().environment
        assertEquals(Color.Transparent, transparent.clearColor)
        assertEquals(false, transparent.showSky)
        assertEquals(opaque, renderer.frameInputs.last().environment, "the main frame keeps its sky and clear")
        assertEquals(target, renderer.live.single(), "changing coverage does not reallocate")
        portrait.view = null
        frame()
        assertEquals(opaque, renderer.viewInputs.last().environment, "returning to default restores the camera background")
    }

    @Test
    fun aHiddenViewOrOneWhoseCameraGoesFreesItsTarget() = runTest {
        runtime.ready(renderer)
        frame()

        portrait.visible = false
        assertEquals(listOf("frame", "ui []"), frame())
        assertTrue(renderer.live.isEmpty(), "hidden: freed")

        portrait.visible = true
        frame()
        runtime.world.remove<Camera>(portraitCamera!!)
        assertEquals(listOf("frame", "ui []"), frame())
        assertTrue(renderer.live.isEmpty(), "no camera: freed, and the element draws only its style")
    }

    @Test
    fun disposingTheRuntimeFreesEveryView() = runTest {
        runtime.ready(renderer)
        frame()

        runtime.dispose()

        assertTrue(renderer.live.isEmpty())
    }
}
