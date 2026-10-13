/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.canvas.CanvasAnchor
import com.awakekt.awake.scene.canvas.CanvasElement
import com.awakekt.awake.scene.canvas.CanvasElementKind
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A host that turns the runtime's own canvas off and draws `SceneCanvas` in part of the window -- an
 * editor's game view, showing a Play world that the runtime's render system draws in place of its own
 * -- gets that world's followers and Views from the runtime's `canvasProjector` and `canvasViews`.
 */
class SceneCanvasHostTest {
    /** What the runtime asked of the GPU, in order: frames, view renders and the targets each UI sampled. */
    private class RecordingRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        val calls = ArrayList<String>()
        val frames = ArrayList<List<Float>>()
        val renders = ArrayList<List<Float>>()
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
        }

        override fun submitToTexture(target: RenderTarget, input: GpuPassInput) {
            check(target in live) { "rendered into a freed target" }
            calls += "view ${target.width}x${target.height}"
            renders += input.viewProjection.data.toList()
        }

        override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) {
            val sampled = primitives.filterIsInstance<UiDrawPrimitive.Texture>().map { targetOf.getValue(it.material as Material) }
            check(sampled.all { it in live }) { "sampled a freed target" }
            calls += "ui ${sampled.map { "${it.width}x${it.height}" }}"
        }
    }

    private val renderer = RecordingRenderer()
    private val input = Input()

    /** The world the game view shows, as an editor's Play world: not the runtime's own. */
    private val play = World()
    private val hero: Entity = play.create().also {
        play.add(it, Name("Hero"))
        play.add(it, Transform().apply { worldMatrix = Mat4() })
        play.add(it, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
    }
    private val plate = playElement { follow = "Hero"; anchor = CanvasAnchor.TopLeft; offsetX = 0f; offsetY = 0f; width = 20f; height = 10f }

    // Its own size is 5 by 5, so a box made from no meshes -- the wrong world's -- would show.
    private val marker = playElement { follow = "Hero"; followBounds = true; width = 5f; height = 5f }

    init {
        play.add(play.create(), Camera(Lens(eye = Vec3f(0f, 0f, 10f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)))
        play.create().also {
            play.add(it, Name("Portrait Camera"))
            play.add(it, Camera(Lens(eye = Vec3f(50f, 1f, 3f), center = Vec3f(50f, 1f, 0f), fovYRadians = 0.5f, near = 0.1f, far = 100f), isPrimary = false))
        }
        playElement { kind = CanvasElementKind.View; camera = "Portrait Camera"; anchor = CanvasAnchor.BottomRight; offsetX = 0f; offsetY = 0f; width = 100f; height = 50f }
    }

    private fun playElement(configure: CanvasElement.() -> Unit): Entity = play.create().also { play.add(it, CanvasElement().apply(configure)) }

    private var gameViewShown = true
    private lateinit var runtime: SceneAppLifecycleRuntime

    init {
        runtime = SceneAppLifecycleRuntime(
            SceneAppSpec(
                sceneName = null,
                systems = emptyList(),
                scenePopulationBlock = {},
                renderableFactory = { error("no renderable requested in this test") },
                assetLibraryFactory = null,
                updateBlock = { _, _ -> },
                // The game view: 400 x 300 at (100, 50) in an 800 x 600 window, the canvas over it.
                ui = {
                    if (gameViewShown) {
                        // --8<-- [start:game-view]
                        Box(Modifier.padding(start = 100.dp, top = 50.dp).size(400.dp, 300.dp)) {
                            SceneCanvas(play, projector = runtime.canvasProjector(play), views = runtime.canvasViews(play))
                        }
                        // --8<-- [end:game-view]
                    }
                },
                onReadyBlock = { drawsSceneCanvas = false },
                onDisposeBlock = {},
                serviceRegistrations = emptyList(),
                infrastructureSystemsFactory = {
                    defaultInfrastructureSystems(viewportProvider = { RenderViewport(100f, 50f, 400f, 300f) }, renderWorldProvider = { play })
                },
            ),
        )
        runtime.initialize(
            object : AppServiceLookup {
                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> service(type: KClass<T>): T? = if (type == Input::class) input as T else null
            },
        )
    }

    /** One frame of the 800 x 600 window, at one pixel per dp, and what it asked of the GPU. */
    private fun frame(): List<String> {
        renderer.calls.clear()
        runtime.update(AppFrame(1f / 60f, 800f, 600f, input.snapshot(), density = 1f))
        return renderer.calls.toList()
    }

    private fun drawn(entity: Entity): SemanticsNode = assertNotNull(runtime.uiSemantics.find("canvas-element-${entity.id}"))

    @Test
    fun aGameViewsFollowersLandOnThePlayWorldsNodesThroughTheRuntimesProjector() = runTest {
        runtime.ready(renderer)

        frame()
        // The second frame knows where the first placed the canvas in the window.
        frame()

        // The Hero is at the camera's centre: the middle of the 400 x 300 view at (100, 50).
        val plateBox = drawn(plate)
        assertEquals(300f, plateBox.x.toFloat(), 1f, "on the Hero in the window, not 100 by 50 past it")
        assertEquals(200f, plateBox.y.toFloat(), 1f)
        val expected = assertNotNull(runtime.canvasProjector(play).bounds(hero), "the Play world's own meshes")
        val markerBox = drawn(marker)
        assertWithinAPixel(
            listOf(expected.x, expected.y, expected.width, expected.height),
            listOf(markerBox.x, markerBox.y, markerBox.width, markerBox.height).map(Int::toFloat),
        )
        assertTrue(markerBox.width > 5, "the Hero's box, not the element's own size")
        assertNull(runtime.canvasProjector().bounds(hero), "the runtime's own world has no such meshes")
        assertSame(runtime.canvasProjector(), runtime.canvasProjector(), "its own world's projector is made once")
    }

    @Test
    fun aGameViewsViewShowsThePlayWorldsCameraThroughTheRuntimesViews() = runTest {
        runtime.ready(renderer)

        val calls = frame()

        assertEquals(listOf("frame", "view 100x50", "ui [100x50]"), calls, "rendered from the Play world's camera before the UI that samples it")
        assertTrue(renderer.renders.last() != renderer.frames.last(), "from its own camera, not the main one")

        gameViewShown = false
        assertEquals(listOf("frame", "ui []"), frame())
        assertTrue(renderer.live.isEmpty(), "a picture nothing draws is freed, and the Play world with it")

        gameViewShown = true
        frame()
        runtime.dispose()
        assertTrue(renderer.live.isEmpty(), "disposing the runtime frees the Play world's pictures")
    }
}

/** Each of [actual] within a pixel of [expected]'s, as a box placed on whole pixels is. */
private fun assertWithinAPixel(expected: List<Float>, actual: List<Float>) {
    assertEquals(expected.size, actual.size)
    expected.zip(actual).forEach { (want, got) -> assertEquals(want, got, 1f, "$expected vs $actual") }
}

private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.find(tag) }
