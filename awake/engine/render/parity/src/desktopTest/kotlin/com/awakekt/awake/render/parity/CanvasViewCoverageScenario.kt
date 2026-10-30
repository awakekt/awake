/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.depthFogContentFeature
import com.awakekt.awake.asset.shaderpack.skyboxContentFeature
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.sky.Skybox
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasGradient
import com.awakekt.awake.ui.canvas.CanvasMask
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasView
import kotlinx.coroutines.runBlocking
import java.io.File

internal const val VIEW_COVERAGE_SIZE = 256

/**
 * The actual scene runtime requests, renders and samples a camera target. Its upper half contains
 * one unlit red plane, its lower half is empty, and the scene has a blue sky. The canvas underneath
 * is a translucent green/blue gradient, so both RGB and alpha reveal wrong target coverage.
 */
internal suspend fun captureCanvasViewCoverage(backend: HeadlessUiBackend, out: File): Map<String, ByteArray> {
    lateinit var runtime: SceneAppLifecycleRuntime
    lateinit var portrait: CanvasElement
    var ancestorClip = false
    var fogVisible = false
    val plan = coveragePlan { fogVisible }
    val game = app {
        window { size(VIEW_COVERAGE_SIZE, VIEW_COVERAGE_SIZE) }
        scene("view-coverage") {
            onReady {
                runtime = this
                drawsSceneCanvas = false
                portrait = populateViewCoverage()
            }
            ui {
                val modifier = if (ancestorClip) Modifier.fillMaxSize().clip(CircleShape) else Modifier.fillMaxSize()
                SceneCanvas(runtime.world, modifier = modifier, views = runtime.canvasViews())
            }
        }
    }
    val captures = linkedMapOf<String, ByteArray>()
    openRuntimeSession(backend, game, VIEW_COVERAGE_SIZE, VIEW_COVERAGE_SIZE, plan).use { session ->
        val renderer = session.renderer
        try {
            game.ready(renderer)
            suspend fun capture(name: String) {
                repeat(2) { game.update(1f / 60f, VIEW_COVERAGE_SIZE.toFloat(), VIEW_COVERAGE_SIZE.toFloat()) }
                val target = renderer.createRenderTarget(VIEW_COVERAGE_SIZE, VIEW_COVERAGE_SIZE)
                val pixels = try {
                    renderer.drawUiToTexture(target, runtime.uiPrimitives)
                    renderer.readPixels(target).data.copyOf()
                } finally {
                    target.destroy()
                }
                captures[name] = pixels
                PixelMap(VIEW_COVERAGE_SIZE, VIEW_COVERAGE_SIZE, pixels).writePng(File(out, "canvas-view-$name-$backend.png"))
            }
            portrait.visible = false
            capture("panel")
            portrait.visible = true
            capture("transparent")
            portrait.camera = "Portrait 1"
            capture("solid")
            portrait.camera = "Portrait 0"
            portrait.style = CanvasStyle(cornerRadius = 48f)
            capture("rounded")
            portrait.view = CanvasView(transparent = true, mask = CanvasMask.Circle)
            capture("circle")
            portrait.view = CanvasView(transparent = true)
            portrait.style = CanvasStyle()
            ancestorClip = true
            portrait.visible = false
            capture("ancestor-panel")
            portrait.visible = true
            capture("ancestor")
            ancestorClip = false
            portrait.view = null
            capture("opaque")
            portrait.view = CanvasView(transparent = true)
            fogVisible = true
            capture("fog")
        } finally {
            renderer.waitIdle()
            game.dispose()
        }
    }
    return captures
}

// The showcase opts only its primary format into blending. This scene also draws textured
// transparent cards, and holds the optional depth fog off for the analytical coverage samples.
private fun coveragePlan(isFogVisible: () -> Boolean) = EngineShowcaseRenderPlan.copy(
    scenePipelines = EngineShowcaseRenderPlan.scenePipelines.map {
        if (it.vertexFormat == VertexFormat.PositionNormalColorUv) it.copy(buildTransparent = true) else it
    },
    contentFeatures = listOf(
        skyboxContentFeature(PackShaderSets.Skybox),
        depthFogContentFeature(Color(0.2f, 0.4f, 0.6f, 0.05f), isVisible = isFogVisible),
    ),
)

private fun SceneAppLifecycleRuntime.populateViewCoverage(): CanvasElement {
    world.add(world.create(), Camera(coverageLens(0f)))
    world.add(world.create(), Skybox(mode = Skybox.Mode.SolidColor(Color(0.1f, 0.2f, 0.8f))))
    val geometry = generate { plane(size = 2f, colored = false, format = VertexFormat.PositionNormalColorUv) }.let { source ->
        val vertices = source.vertices.copyOf()
        val stride = source.format.strideFloats
        val position = source.format.floatOffsetOf(VertexSemantic.Position)
        val normal = source.format.floatOffsetOf(VertexSemantic.Normal)
        for (vertex in vertices.indices step stride) {
            // XZ ground -> XY card occupying only the camera's upper half.
            vertices[vertex + position + 1] = (vertices[vertex + position + 2] + 1f) / 2f
            vertices[vertex + position + 2] = 0f
            vertices[vertex + normal] = 0f
            vertices[vertex + normal + 1] = 0f
            vertices[vertex + normal + 2] = 1f
        }
        source.copy(vertices = vertices)
    }
    for ((index, alpha) in listOf(0.5f, 1f).withIndex()) {
        val side = 50f + index * 10f
        world.create().also {
            world.add(it, Transform(position = Vec3f(side, 0f, 0f)))
            world.add(it, MeshRenderer(renderer.createMesh(geometry), renderer.texturedMaterial(SolidWhite), transparent = true))
            world.add(it, PbrMaterial(baseColorFactor = Color(1f, 0f, 0f, alpha), unlit = true))
        }
        world.create().also {
            world.add(it, Name("Portrait $index"))
            world.add(it, Camera(coverageLens(side), isPrimary = false))
        }
    }
    world.add(
        world.create(),
        CanvasElement().apply {
            kind = CanvasElementKind.Panel
            width = VIEW_COVERAGE_SIZE.toFloat(); height = VIEW_COVERAGE_SIZE.toFloat()
            style = CanvasStyle(gradient = CanvasGradient("#00408099", "#00804099"))
        },
    )
    return CanvasElement().apply {
        kind = CanvasElementKind.View; camera = "Portrait 0"; order = 1
        offsetX = 32f; offsetY = 32f; width = 192f; height = 192f
        view = CanvasView(transparent = true)
    }.also { world.add(world.create(), it) }
}

private fun coverageLens(side: Float) = Lens(
    eye = Vec3f(side, 0f, 3f),
    center = Vec3f(side, 0f, 0f),
    fovYRadians = 1f,
    near = 0.1f,
    far = 100f,
).apply { projection = Lens.Projection.Orthographic; orthoHalfHeight = 1f }

/** Test-scoped runtime evidence, appended to the ordinary PR render-evidence capture. */
fun main(args: Array<String>) = runBlocking {
    val out = File(args.single()).apply { mkdirs() }
    val captures = captureCanvasViewCoverage(HeadlessUiBackend.Vulkan, out)
    File(out, "captions.tsv").appendText(
        captures.keys.joinToString("") { "canvas-view-$it-Vulkan\tRuntime camera View: $it over a translucent gradient\n" },
    )
}
