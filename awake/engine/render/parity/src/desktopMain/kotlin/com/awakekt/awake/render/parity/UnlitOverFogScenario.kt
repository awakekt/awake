/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.depthFogContentFeature
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A pale haze, as a game's distance fog is; its alpha is the density per world unit. */
private val UNLIT_SCENE_FOG = Color(r = 0.9f, g = 0.9f, b = 0.95f, a = 0.03f)

/**
 * A plan with distance fog over a static and a skinned textured pipeline, the skinned one casting
 * into scene depth through its own depth shader as a played project's does. That is the frame an
 * unlit skin was fogged in: the fog covers every pixel scene depth holds.
 */
val UnlitOverFogPlan: RenderPlan = RenderPlan(
    primary = ScenePipeline(
        PipelineKey.Primary,
        PackShaderSets.LitShadow,
        VertexFormat.PositionNormalColor,
        materialBindings = GroupBindings.UniformOnlyMaterial,
    ),
    contentFeatures = listOf(depthFogContentFeature(UNLIT_SCENE_FOG)),
    depthPrePassShaderSet = PackShaderSets.ShadowDepth,
    sceneDepthShaderSet = PackShaderSets.SceneDepth,
    scenePipelines = listOf(
        ScenePipeline(
            PipelineKey.Format(VertexFormat.PositionNormalColorUv),
            PackShaderSets.Textured,
            VertexFormat.PositionNormalColorUv,
            materialBindings = GroupBindings.StandardMaterial,
            buildTransparent = true,
        ),
        ScenePipeline(
            PipelineKey.Format(VertexFormat.PositionNormalColorUvSkin),
            PackShaderSets.SkinnedTextured,
            VertexFormat.PositionNormalColorUvSkin,
            materialBindings = GroupBindings.TexturedMaterial,
            depthShaders = PackShaderSets.SkinnedTexturedShadowDepth,
        ),
    ),
)

/** [backend] booted headless at [size] square on [plan], [UnlitOverFogPlan] unless given. */
fun openUnlitOverFogSession(backend: HeadlessUiBackend, size: Int = SCENE_SIZE, plan: RenderPlan = UnlitOverFogPlan): HeadlessRenderSession {
    if (backend == HeadlessUiBackend.WebGpu) return webGpuHeadlessPlan(plan, surface = HeadlessSurface(size, size))
    val ready = CompletableDeferred<Renderer>()
    val app = AppSpecBuilder().apply { ready { ready.complete(it) } }.build().createLifecycle()
    val engine = VulkanEngine(app, plan)
    engine.create(HeadlessSurface(size, size))
    val renderer = runBlocking { withTimeout(UNLIT_BOOT_TIMEOUT_MS) { ready.await() } }
    return object : HeadlessRenderSession {
        override val renderer = renderer
        override fun close() = engine.dispose()
    }
}

/**
 * A plane filling the view, textured with the flat [texel] and white otherwise, drawn [unlit] or
 * lit under a white sun, [skinned] to one identity joint or static, through the fog of
 * [UnlitOverFogPlan]. Returns the frame's RGBA bytes.
 */
fun Renderer.renderUnlitOverFogScene(texel: IntArray, skinned: Boolean, unlit: Boolean, size: Int = SCENE_SIZE): ByteArray =
    renderPlanes(listOf(PlaneDraw(texel, skinned, unlit)), size)

/**
 * The dark texel's plane drawn lit and opaque, with a white plane at half opacity hovering over it,
 * filling the view through the same fog: the fog covers the pair by the ground's distance, as it
 * did before unlit draws were exempted. Returns the frame's RGBA bytes.
 */
fun Renderer.renderTransparentOverFogScene(size: Int = SCENE_SIZE): ByteArray = renderPlanes(
    listOf(
        PlaneDraw(intArrayOf(0x60, 0x40, 0x20), skinned = false, unlit = false),
        PlaneDraw(intArrayOf(0xFF, 0xFF, 0xFF, 0x80), skinned = false, unlit = false, y = HOVER_Y, transparent = true),
    ),
    size,
)

/** One plane of a scene: its flat RGB(A) [texel], whether it is [skinned], [unlit] or [transparent], and its height. */
private class PlaneDraw(val texel: IntArray, val skinned: Boolean, val unlit: Boolean, val y: Float = 0f, val transparent: Boolean = false)

private fun Renderer.renderPlanes(planes: List<PlaneDraw>, size: Int): ByteArray {
    val target = createRenderTarget(size, size)
    val meshes = planes.map { createMesh(unlitPlane(it.skinned, it.y)) }
    val materials = planes.map { plane ->
        val texture = flatTexture(plane.texel)
        if (plane.skinned) {
            createMaterial(SkinnedUniformLayout, texture = texture)
        } else {
            createMaterial(texture = texture, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = PbrTextureSet())
        }
    }
    val draws = planes.mapIndexed { index, plane ->
        val extras = if (plane.skinned) {
            skinnedMaterialFloats(IDENTITY_JOINT, Color.White, Color.Transparent, unlit = plane.unlit)
        } else {
            pbrMaterialFloats(metallic = 0f, roughness = 1f, baseColorFactor = Color.White, emissiveFactor = Color.Transparent, unlit = plane.unlit)
        }
        RenderDrawCommand(meshes[index], materials[index], extraUniformFloats = extras, transparent = plane.transparent, exemptFromCoverings = plane.unlit)
    }
    return try {
        val input = ScenePassCompiler.compile(
            lens = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f),
            drawCalls = draws,
            light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
            environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
            clipSpace = clipSpace,
            aspect = 1f,
            drawPreparer = (this as GpuDrawPreparationSource).gpuDrawPreparer,
        )
        renderToTexture(target, input)
        runBlocking { readPixels(target) }.data
    } finally {
        waitIdle()
        meshes.forEach { it.destroy() }
        materials.forEach { it.destroy() }
        target.destroy()
    }
}

/** The RGB of the pixel at the centre of a [size]-square frame. */
fun unlitSceneCentreRgb(pixels: ByteArray, size: Int = SCENE_SIZE): IntArray {
    val base = (size / 2 * size + size / 2) * 4
    return IntArray(3) { pixels[base + it].toInt() and 0xFF }
}

/** A 2x2 texture of [texel], RGB or RGBA; opaque when it gives no alpha. */
private fun flatTexture(texel: IntArray) = TextureAsset(
    data = ByteArray(2 * 2 * 4) { texel.getOrElse(it % 4) { 0xFF }.toByte() },
    width = 2,
    height = 2,
)

/** Position, normal (up), white colour, UV, then for [skinned] all weight on joint 0. */
private fun unlitPlane(skinned: Boolean, y: Float = 0f): MeshGeometry {
    val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)
    val skin = floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
    val vertices = corners.flatMap { (x, z) ->
        val u = (x + 1f) / 2f
        val v = (z + 1f) / 2f
        listOf(x * GROUND_HALF, y, z * GROUND_HALF, 0f, 1f, 0f, 1f, 1f, 1f, u, v) + if (skinned) skin.toList() else emptyList()
    }
    return MeshGeometry(
        vertices.toFloatArray(),
        intArrayOf(0, 1, 2, 2, 3, 0),
        if (skinned) VertexFormat.PositionNormalColorUvSkin else VertexFormat.PositionNormalColorUv,
    )
}

private val IDENTITY_JOINT = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

private const val UNLIT_BOOT_TIMEOUT_MS = 30_000L

/** The transparent plane's height over the ground: clear of it, near enough to fog alike. */
private const val HOVER_Y = 0.5f
